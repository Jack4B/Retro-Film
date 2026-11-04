package app.retrofilm.pipeline

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Wyniki analizy całego obrazu potrzebne do przetwarzania pasami; [halo] to zapas wierszy wokół pasa. */
@Suppress("LongParameterList") // zbiór niezależnych planów etapów, bez sensownego podziału
internal class StripPlans(
    val chroma: ChromaDenoise.Plan?,
    val chromaEdge: ChromaEdge.Plan?,
    val luma: LumaDenoise.Plan?,
    val dehaze: Dehaze.Plan?,
    val tone: ToneMask?,
    val clarity: DetailFilter.ClarityMap?,
    val semantic: SemanticSharpen.Map?,
    val halo: Int,
    val longSide: Int,
    val debandRadius: Int = 0
)

/**
 * Potok korekt pasami wierszy zamiast kolejnych przebiegów po całych tablicach 8-bitowych. Najpierw analiza obrazu w
 * niskiej rozdzielczości ([StripAnalysis]: mapy odszumiania koloru i dehaze, maska tonalna, baza wyrazistości), potem
 * każdy pas (z zapasem wierszy na wyostrzanie i odszumianie jasności) przechodzi wszystkie etapy na kanałach float:
 * odszumianie koloru i jasności, dehaze, korekty ([AdjustmentProcessor.processPlanes]), wyostrzanie i wyrazistość,
 * ziarno i winieta (po wyostrzaniu, więc ostrość nie wzmacnia ziarna) i dopiero na końcu jedna kwantyzacja z
 * ditheringiem. Dzięki temu nie ma pośrednich zaokrągleń (mniej pasmowania), a pamięć nie rośnie z rozmiarem zdjęcia
 * (pełne tablice ARGB i float zastępują bufory pasów po jednym na wątek). Wynik nie zależy od liczby wątków.
 */
internal object StripPipeline {
    private const val MIN_CORE = 128
    private const val HALO_FACTOR = 6
    private const val NANOS_PER_MS = 1_000_000L

    fun run(
        src: Bitmap,
        adj: Adjustments,
        deband: Boolean = false,
        faces: List<FaceBox> = emptyList(),
        io: StripIo = StripIo.Default
    ): Bitmap {
        val w = src.width
        val h = src.height
        val t0 = System.nanoTime()
        val plans = StripAnalysis(src, adj, deband, faces).plans()
        val t1 = System.nanoTime()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // pas jest co najmniej HALO_FACTOR razy wyższy od zapasu, żeby nadmiarowe wiersze nie dominowały kosztu
        val core = max(MIN_CORE, HALO_FACTOR * plans.halo)
        Parallel.forEach(
            (h + core - 1) / core,
            { StripWorker(adj, plans, w, h, min(h, core + 2 * plans.halo), io) }
        ) { worker, chunk -> worker.run(src, out, chunk * core, min(h, (chunk + 1) * core)) }
        PipelineStats.analysisMs = (t1 - t0) / NANOS_PER_MS
        PipelineStats.stripsMs = (System.nanoTime() - t1) / NANOS_PER_MS
        return out
    }
}

/** Czasy ostatniego przebiegu [StripPipeline] (analiza i pasy) – do raportu wydajności. */
internal object PipelineStats {
    @Volatile var analysisMs = 0L

    @Volatile var stripsMs = 0L
}

/** Przetwarza pasy jednego wątku: własny procesor i bufory (nie są bezpieczne wątkowo). */
internal class StripWorker(
    private val adj: Adjustments,
    private val plans: StripPlans,
    private val w: Int,
    private val h: Int,
    capRows: Int,
    private val io: StripIo = StripIo.Default
) {
    private val processor = AdjustmentProcessor(adj)
    private val neutral = adj.isNeutral
    private val ints = IntArray(w * capRows)
    private val planes = Planes.of(w * capRows)

    /** Przetwarza wiersze [y0, y1) obrazu [src] i zapisuje je do [out]. */
    fun run(src: Bitmap, out: Bitmap, y0: Int, y1: Int) {
        val a = max(0, y0 - plans.halo)
        val b = min(h, y1 + plans.halo)
        val count = (b - a) * w
        synchronized(src) { src.getPixels(ints, 0, w, 0, a, w, b - a) }
        for (i in 0 until count) {
            val c = ints[i]
            planes.r[i] = ((c shr RED_SHIFT) and MASK).toFloat()
            planes.g[i] = ((c shr GREEN_SHIFT) and MASK).toFloat()
            planes.b[i] = (c and MASK).toFloat()
        }
        io.fracIn?.let { Frac.add(it, a * w, planes, count) }
        val span = PixelSpan(w, a, h)
        plans.chroma?.let { ChromaDenoise.applyPlanes(it, planes, count, span) }
        plans.chromaEdge?.let { ChromaEdge.applyPlanes(it, planes, count, w) }
        plans.luma?.let { LumaDenoise.applyPlanes(it, planes, count, w) }
        plans.dehaze?.let { Dehaze.restorePlanes(it, planes, count, span) }
        if (!neutral) processor.processPlanes(planes, count, span, plans.tone)
        if (plans.debandRadius > 0) Deband.applyPlanes(planes, count, w, plans.debandRadius)
        val core = (y0 - a) * w
        val coreCount = (y1 - y0) * w
        if (adj.hasDetail) sharpen(count, a, core, coreCount)
        processor.effectsPlanes(planes, core, coreCount, PixelSpan(w, y0, h))
        io.stage?.apply(planes, core, coreCount)
        val fracOut = io.fracOut
        if (fracOut != null) {
            Frac.split(planes, core, coreCount, fracOut, y0 * w)
            quantize(core, coreCount, y0, dither = false)
        } else {
            quantize(core, coreCount, y0, io.dither)
        }
        synchronized(out) { out.setPixels(ints, core, w, 0, y0, w, y1 - y0) }
    }

    /** Wyostrzanie i wyrazistość na jasności całego pasa; wynik dodawany tylko do wierszy właściwych (bez zapasu). */
    private fun sharpen(count: Int, firstRow: Int, core: Int, coreCount: Int) {
        val luma = FloatArray(count) { LUMA_R * planes.r[it] + LUMA_G * planes.g[it] + LUMA_B * planes.b[it] }
        val delta = DetailFilter.planeDelta(
            luma,
            w,
            firstRow,
            plans.longSide,
            adj.sharpenParams,
            adj.clarity,
            plans.clarity,
            plans.semantic
        )
        for (i in core until core + coreCount) {
            val d = delta[i]
            planes.r[i] = (planes.r[i] + d).coerceIn(0f, MAX)
            planes.g[i] = (planes.g[i] + d).coerceIn(0f, MAX)
            planes.b[i] = (planes.b[i] + d).coerceIn(0f, MAX)
        }
    }

    /** Jedyna kwantyzacja do 8 bitów (dithering trójkątny jak w [AdjustmentProcessor.process]); alfa ze źródła. */
    private fun quantize(core: Int, coreCount: Int, y0: Int, dither: Boolean) {
        var x = 0
        var row = y0
        for (i in core until core + coreCount) {
            val d = if (dither) Dither.at(x, row) * MAX else 0f
            ints[i] = (ints[i] and ALPHA_MASK) or (to8(planes.r[i] + d) shl RED_SHIFT) or
                (to8(planes.g[i] + d) shl GREEN_SHIFT) or to8(planes.b[i] + d)
            if (++x == w) {
                x = 0
                row++
            }
        }
    }

    private fun to8(v: Float): Int = (v + HALF).toInt().coerceIn(0, BYTE_MAX)

    private companion object {
        const val MAX = 255f
        const val BYTE_MAX = 255
        const val HALF = 0.5f
        const val MASK = 0xFF
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val ALPHA_MASK = 0xFF000000.toInt()
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}

/** Element obróbki wstawiany przed końcową kwantyzacją pasa (np. LUT w eksporcie); wartości 0..255. */
internal fun interface PlaneStage {
    fun apply(planes: Planes, from: Int, count: Int)
}

/**
 * Wejście/wyjście [StripPipeline] poza 8-bitową bitmapą: [fracIn]/[fracOut] to ułamkowa część wartości (2 bity na
 * kanał, [Frac]) – razem z bitmapą daje ~10 bitów między etapami eksportu bez pełnej kopii float. [stage] działa na
 * kanałach float tuż przed kwantyzacją. [dither] = false wyłącza dithering (np. wyjście pośrednie).
 */
internal class StripIo(
    val fracIn: ByteArray? = null,
    val fracOut: ByteArray? = null,
    val stage: PlaneStage? = null,
    val dither: Boolean = true
) {
    companion object {
        val Default = StripIo()
    }
}

/** Ułamek wartości kanału (−0,5..0,5 poziomu) zapisany na 2 bitach; 3 kanały w jednym bajcie. */
internal object Frac {
    private const val LEVELS = 4f
    private const val MAX_CODE = 3
    private const val HALF = 0.5f
    private const val BITS = 2
    private const val MAX = 255f

    fun encode(f: Float): Int = ((f + HALF) * LEVELS).toInt().coerceIn(0, MAX_CODE)

    fun decode(code: Int): Float = (code + HALF) / LEVELS - HALF

    /** Dodaje ułamki z [frac] (od indeksu [offset]) do kanałów. */
    fun add(frac: ByteArray, offset: Int, planes: Planes, count: Int) {
        for (i in 0 until count) {
            val c = frac[offset + i].toInt()
            planes.r[i] += decode(c shr (2 * BITS) and MAX_CODE)
            planes.g[i] += decode(c shr BITS and MAX_CODE)
            planes.b[i] += decode(c and MAX_CODE)
        }
    }

    /** Zapisuje ułamek względem zaokrąglenia (jak w kwantyzacji bez ditheringu) do [frac] od [offset]. */
    fun split(planes: Planes, from: Int, count: Int, frac: ByteArray, offset: Int) {
        for (k in 0 until count) {
            val i = from + k
            frac[offset + k] = (
                (code(planes.r[i]) shl (2 * BITS)) or (code(planes.g[i]) shl BITS) or code(planes.b[i])
                ).toByte()
        }
    }

    private fun code(v: Float): Int {
        val c = v.coerceIn(0f, MAX)
        return encode(c - Math.round(c))
    }
}
