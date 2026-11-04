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
    val debandRadius: Int = 0,
    /** Zapas wierszy samego wyostrzania (część [halo]); jasność do wyostrzania tylko z rdzenia ± tego zapasu. */
    val sharpMargin: Int = 0,
    /** Mapa poświaty halacji (I); null = bez halacji. */
    val halation: Halation.Map? = null
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
        val plans = StripAnalysis(src, adj, deband, faces, io.finish, io.stage).plans()
        val t1 = System.nanoTime()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val columns = io.fracIn == null && io.fracOut == null
        val layout = StripBudget.layout(
            w,
            h,
            plans,
            io.finish ?: adj,
            io.budget ?: StripBudget.bytes(),
            columns = columns
        )
        val core = layout.core
        val tiles = layout.tilesX
        val coreW = (w + tiles - 1) / tiles
        val tileW = StripBudget.tileWidth(w, tiles, plans.halo)
        val chunks = (h + core - 1) / core * tiles
        val done = java.util.concurrent.atomic.AtomicInteger()
        Parallel.forEach(
            chunks,
            { StripWorker(adj, plans, ImageSize(w, h), tileW * min(h, core + 2 * plans.halo), io) },
            layout.threads
        ) { worker, chunk ->
            val row = chunk / tiles
            val col = chunk % tiles
            val y = row * core
            val x = col * coreW
            worker.run(src, out, Tile(x, y, min(w, x + coreW), min(h, y + core)))
            SaveProgress.tiles(done.incrementAndGet(), chunks)
        }
        PipelineStats.analysisMs = (t1 - t0) / NANOS_PER_MS
        PipelineStats.stripsMs = (System.nanoTime() - t1) / NANOS_PER_MS
        return out
    }
}

/**
 * H: limit pamięci buforów pasów. W G jeden przebieg (film + wykończenie) łączył zapasy odbandowania i wyostrzania,
 * a wysokość pasa rosła z zapasem (×[HALO_FACTOR]) – przy 8 wątkach bufory pasów 12 MP sięgały ~1 GB
 * (OOM przy zapisie).
 * Teraz wysokość pasa i liczba wątków są dobierane tak, by suma buforów wszystkich wątków mieściła się w [bytes].
 */
internal object StripBudget {
    data class Layout(val core: Int, val threads: Int, val tilesX: Int = 1)

    const val MIN_CORE = 128
    const val HALO_FACTOR = 6
    private const val CORE_STEP = 64
    private const val MAX_TILES_X = 8
    private const val MB = 1L shl 20
    const val MIN_BYTES = 48 * MB
    private const val MAX_BYTES = 256 * MB
    private const val HEAP_SHARE = 2 // piksele bitmap są poza stertą Javy (API 26+) – bufory pasów to główny jej koszt
    private const val BASE_BPP = 16 // ints + 3 płaszczyzny float
    private const val DEBAND_BPP = 16 // Deband.Scratch
    private const val SHARP_KEEP_BPP = 8 // jasność + delta na cały zakres wyostrzania
    private const val SHARP_CHUNK_BPP = 40 // bufory jednego podpasa wyostrzania (RL, rozmycie, maska…)

    /** Wiersze podpasa wyostrzania (I): bufory RL zależą od niego, nie od wysokości pasa. */
    const val SHARP_CHUNK = 256

    /** Budżet: 1/[HEAP_SHARE] sterty, w granicach [MIN_BYTES]..[MAX_BYTES]. */
    fun bytes(maxHeap: Long = Runtime.getRuntime().maxMemory()): Long =
        (maxHeap / HEAP_SHARE).coerceIn(MIN_BYTES, MAX_BYTES)

    /** Szacunek pamięci jednego wątku dla pasa o rdzeniu [core] wierszy. */
    fun workerBytes(w: Int, core: Int, plans: StripPlans, detail: Boolean): Long {
        val rows = core + 2L * plans.halo
        val bpp = BASE_BPP + if (plans.debandRadius > 0) DEBAND_BPP else 0
        val sharp = if (detail) {
            val m = 2L * plans.sharpMargin
            (core + m) * SHARP_KEEP_BPP + (min(core, SHARP_CHUNK) + m) * SHARP_CHUNK_BPP
        } else {
            0L
        }
        return w * (rows * bpp + sharp)
    }

    /** Szerokość kafla z zapasem po obu stronach, gdy obraz [w] dzielimy na [tiles] kolumn. */
    fun tileWidth(w: Int, tiles: Int, halo: Int): Int = min(w, (w + tiles - 1) / tiles + 2 * halo)

    /**
     * Układ o największej przepustowości w budżecie: wątki × udział rdzenia w kaflu (zapas to praca stracona).
     * H dzieliło tylko na pasy pełnej szerokości i obcinało wątki – przy 8 rdzeniach 12 MP zostawały 2–3 wątki
     * (regres zapisu i pełnego podglądu). I dzieli też na kolumny ([Layout.tilesX]), więc kafel jest mały,
     * a wątków dużo. [columns] = false: tylko pasy (ułamki [StripIo.fracIn]/[StripIo.fracOut] w układzie obrazu).
     */

    @Suppress("LongParameterList")
    fun layout(
        w: Int,
        h: Int,
        plans: StripPlans,
        sharp: Adjustments,
        budget: Long,
        workers: Int = Parallel.workers,
        columns: Boolean = true
    ): Layout {
        val detail = sharp.hasDetail
        val top = min(h, max(MIN_CORE, HALO_FACTOR * plans.halo))
        var best = Layout(min(h, MIN_CORE), 1)
        var bestScore = -Float.MAX_VALUE
        val maxTiles = if (columns) max(1, w / max(MIN_CORE, 2 * plans.halo)).coerceAtMost(MAX_TILES_X) else 1
        for (tiles in 1..maxTiles) {
            val coreW = (w + tiles - 1) / tiles
            val tileW = tileWidth(w, tiles, plans.halo)
            var core = min(h, MIN_CORE)
            while (core <= top) {
                val bytes = workerBytes(tileW, core, plans, detail)
                val chunks = (h + core - 1) / core * tiles
                val threads = (budget / bytes).toInt().coerceIn(1, min(workers, chunks))
                // ponad budżet nawet jeden wątek: wybierz najmniejszy kafel
                val score = if (bytes > budget) {
                    -bytes.toFloat()
                } else {
                    threads * core.toFloat() / min(h, core + 2 * plans.halo) * coreW / tileW
                }
                if (score > bestScore) {
                    best = Layout(core, threads, tiles)
                    bestScore = score
                }
                core += CORE_STEP
            }
        }
        return best
    }
}

/** Czasy ostatniego przebiegu [StripPipeline] (analiza i pasy) – do raportu wydajności. */
internal object PipelineStats {
    @Volatile var analysisMs = 0L

    @Volatile var stripsMs = 0L
}

/** Przetwarza pasy jednego wątku: własny procesor i bufory (nie są bezpieczne wątkowo). */
internal data class ImageSize(val w: Int, val h: Int)

/** Rdzeń kafla: kolumny [x0, x1), wiersze [y0, y1). */
internal data class Tile(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

internal class StripWorker(
    private val adj: Adjustments,
    private val plans: StripPlans,
    size: ImageSize,
    capacity: Int,
    private val io: StripIo = StripIo.Default
) {
    private val fullW = size.w
    private val h = size.h
    private var w = fullW // szerokość bieżącego kafla z zapasem
    private val processor = AdjustmentProcessor(adj)
    private val neutral = adj.isNeutral
    private val finish = io.finish
    private val finisher = finish?.let { AdjustmentProcessor(it) }
    private val ints = IntArray(capacity)
    private val planes = Planes.of(capacity)
    private val debandScratch = if (plans.debandRadius > 0) Deband.Scratch(capacity) else null
    private var firstCol = 0

    /** Przetwarza kafel [t] obrazu [src] (z zapasem [StripPlans.halo] w obu osiach) i zapisuje rdzeń do [out]. */
    fun run(src: Bitmap, out: Bitmap, t: Tile) {
        val a = max(0, t.y0 - plans.halo)
        val b = min(h, t.y1 + plans.halo)
        val xa = max(0, t.x0 - plans.halo)
        val xb = min(fullW, t.x1 + plans.halo)
        w = xb - xa
        firstCol = xa
        val count = (b - a) * w
        synchronized(src) { src.getPixels(ints, 0, w, xa, a, w, b - a) }
        for (i in 0 until count) {
            val c = ints[i]
            planes.r[i] = ((c shr RED_SHIFT) and MASK).toFloat()
            planes.g[i] = ((c shr GREEN_SHIFT) and MASK).toFloat()
            planes.b[i] = (c and MASK).toFloat()
        }
        io.fracIn?.let { Frac.add(it, a * w, planes, count) }
        val span = span(a)
        plans.chroma?.let { ChromaDenoise.applyPlanes(it, planes, count, span) }
        plans.chromaEdge?.let { ChromaEdge.applyPlanes(it, planes, count, w) }
        plans.luma?.let { LumaDenoise.applyPlanes(it, planes, count, w) }
        plans.dehaze?.let { Dehaze.restorePlanes(it, planes, count, span) }
        if (!neutral) processor.processPlanes(planes, count, span, plans.tone)
        if (plans.debandRadius > 0) {
            Deband.applyPlanes(planes, count, w, plans.debandRadius, debandScratch ?: Deband.Scratch(count))
        }
        val core = (t.y0 - a) * w
        val coreCount = (t.y1 - t.y0) * w
        if (finisher != null && finish != null) {
            filmAndFinish(count, a, t.y0, t.y1)
        } else {
            if (adj.hasDetail) sharpen(count, a, core, coreCount, adj)
            processor.effectsPlanes(planes, core, coreCount, span(t.y0), plans.halation)
            io.stage?.apply(planes, core, coreCount)
        }
        val fracOut = io.fracOut
        if (fracOut != null) {
            Frac.split(planes, core, coreCount, fracOut, t.y0 * w)
            quantize(core, coreCount, t.y0, dither = false)
        } else {
            quantize(core, coreCount, t.y0, io.dither)
        }
        synchronized(out) { out.setPixels(ints, core + (t.x0 - xa), w, t.x0, t.y0, t.x1 - t.x0, t.y1 - t.y0) }
    }

    private fun span(firstRow: Int) = PixelSpan(w, firstRow, h, firstCol, fullW)

    /** Jak dawny drugi przebieg: film na całym pasie (z zapasem dla wyostrzania), potem wykończenie. */
    private fun filmAndFinish(count: Int, a: Int, y0: Int, y1: Int) {
        val finisher = finisher ?: return
        val finish = finish ?: return
        val core = (y0 - a) * w
        val coreCount = (y1 - y0) * w
        io.stage?.apply(planes, 0, count)
        if (!finish.isNeutral) finisher.processPlanes(planes, count, span(a), null)
        if (finish.hasDetail) sharpen(count, a, core, coreCount, finish)
        finisher.effectsPlanes(planes, core, coreCount, span(y0), plans.halation)
    }

    /**
     * Wyostrzanie i wyrazistość na jasności rdzenia pasa z zapasem [StripPlans.sharpMargin] (reszta zapasu służy
     * odszumianiu i odbandowaniu); wynik dodawany tylko do wierszy właściwych.
     */
    private fun sharpen(count: Int, firstRow: Int, core: Int, coreCount: Int, adj: Adjustments) {
        val from = max(0, core - plans.sharpMargin * w)
        val to = min(count, core + coreCount + plans.sharpMargin * w)
        val luma = FloatArray(to - from) {
            val i = from + it
            LUMA_R * planes.r[i] + LUMA_G * planes.g[i] + LUMA_B * planes.b[i]
        }
        val delta = DetailFilter.planeDelta(
            luma,
            w,
            firstRow + from / w,
            plans.longSide,
            adj.sharpenParams,
            adj.clarity,
            plans.clarity,
            plans.semantic,
            firstCol
        )
        for (i in core until core + coreCount) {
            val d = delta[i - from]
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
            val d = if (dither) Dither.at(firstCol + x, row) * MAX else 0f
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
    val dither: Boolean = true,
    /** Wykończenie po [stage] w tym samym przebiegu (G): LUT na całym pasie, potem wyostrzanie, ziarno i winieta. */
    val finish: Adjustments? = null,
    /** Limit pamięci buforów pasów (H); null = [StripBudget.bytes]. */
    val budget: Long? = null
) {
    fun copyWith(budget: Long) = StripIo(fracIn, fracOut, stage, dither, finish, budget)

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
