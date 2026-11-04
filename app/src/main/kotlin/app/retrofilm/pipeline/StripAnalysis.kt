package app.retrofilm.pipeline

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

private const val SAMPLE_ROWS = 16
private const val SAMPLE_STRIDE = 3
private const val SEMANTIC_STRIDE = 2
private const val GAIN_STRIDE = 4
private const val NOISE_BANDS = 8
private const val NOISE_BAND_ROWS = 48
private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
private const val MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** Czyta z [src] co [stride]-ty piksel co [stride]-tego wiersza jako kanały float, porcjami wierszy dla wątków. */
private class Sampler(private val src: Bitmap, private val stride: Int) {
    private val w = src.width
    private val rowCount = (src.height + stride - 1) / stride
    private val n = (w + stride - 1) / stride
    val chunks = (rowCount + SAMPLE_ROWS - 1) / SAMPLE_ROWS

    /** Dla każdego wiersza próbek porcji [chunk] wywołuje [body] z numerem wiersza obrazu i kanałami próbek. */
    fun rows(chunk: Int, body: (y: Int, planes: Planes) -> Unit) {
        val buf = IntArray(w)
        val planes = Planes.of(n)
        for (k in chunk * SAMPLE_ROWS until min(rowCount, (chunk + 1) * SAMPLE_ROWS)) {
            val y = k * stride
            synchronized(src) { src.getPixels(buf, 0, w, 0, y, w, 1) }
            for (i in 0 until n) {
                val c = buf[i * stride]
                planes.r[i] = ((c shr RED_SHIFT) and MASK).toFloat()
                planes.g[i] = ((c shr GREEN_SHIFT) and MASK).toFloat()
                planes.b[i] = (c and MASK).toFloat()
            }
            body(y, planes)
        }
    }
}

private class Sums(val chroma: Lowres.Accumulator?, val dehaze: Lowres.Accumulator?) {
    fun merge(other: Sums) {
        if (other.chroma != null) chroma?.merge(other.chroma)
        if (other.dehaze != null) dehaze?.merge(other.dehaze)
    }
}

private class ClarityState(val acc: Lowres.Accumulator, val processor: AdjustmentProcessor)

/**
 * Analiza całego obrazu dla [StripPipeline]: mapy w niskiej rozdzielczości, które w dawnym potoku powstawały z
 * pełnych, już przetworzonych tablic. Mapy odszumiania koloru i dehaze liczone są ze źródła (pełny odczyt, tanio);
 * kompensacja jasności dehaze, maska tonalna i baza wyrazistości z podpróbkowania obrazu po kolejnych etapach.
 */
internal class StripAnalysis(
    private val src: Bitmap,
    private val adj: Adjustments,
    private val deband: Boolean = false,
    private val faces: List<FaceBox> = emptyList()
) {
    private val w = src.width
    private val h = src.height

    fun plans(): StripPlans {
        val sums = globalSums()
        val chroma = sums?.chroma?.let { ChromaDenoise.plan(it, it.low, adj.denoise) }
        val dehaze = sums?.dehaze?.let { acc -> Dehaze.plan(Array(RGB) { acc.mean(it) }, acc.low, adj.dehaze) }
        dehaze?.let { it.gain = dehazeGain(it) }
        val luma = LumaDenoise.plan(w, h, adj.lumaDenoise, if (adj.lumaDenoise > 0f) noiseSigma() else 0f)
        val tone = if (adj.usesToneMask) toneMask(chroma, dehaze) else null
        val clarity = if (adj.clarity != 0f) clarityMap(chroma, dehaze, tone) else null
        val sharpMargin = if (adj.sharpness > 0f) Sharpener.marginFor(adj.sharpenParams, max(w, h)) else 0
        val debandRadius = if (deband) Deband.radiusFor(max(w, h)) else 0
        val semantic = if (adj.sharpness > 0f && adj.sharpSmart) semanticMap() else null
        val edge = ChromaEdge.plan(w, h, adj.chromaEdges)
        val halo = sharpMargin + max(luma?.margin ?: 0, edge?.margin ?: 0) + debandRadius
        return StripPlans(chroma, edge, luma, dehaze, tone, clarity, semantic, halo, max(w, h), debandRadius)
    }

    /** Sumy w komórkach dla odszumiania koloru i dehaze z całego obrazu (bez podpróbkowania); null = niepotrzebne. */
    private fun globalSums(): Sums? {
        val chromaLow = if (adj.denoise > 0f) Lowres.forSize(w, h, ChromaDenoise.TARGET) else null
        val dehazeLow = if (adj.dehaze > 0f) Lowres.forSize(w, h, Dehaze.TARGET) else null
        if (chromaLow == null && dehazeLow == null) return null
        val sampler = Sampler(src, 1)
        return Parallel.reduce(
            sampler.chunks,
            { Sums(chromaLow?.let { Lowres.Accumulator(it) }, dehazeLow?.let { Lowres.Accumulator(it) }) },
            { into, other -> into.merge(other) }
        ) { sums, chunk ->
            sampler.rows(chunk) { y, p ->
                for (i in 0 until p.size) {
                    sums.chroma?.let { ChromaDenoise.accumulate(it, i, y, p.r[i], p.g[i], p.b[i]) }
                    sums.dehaze?.add(i, y, p.r[i], p.g[i], p.b[i])
                }
            }
        }
    }

    /** Kompensacja jasności po dehaze z podpróbkowania (średnia jasność przed i po usunięciu mgły). */
    private fun dehazeGain(plan: Dehaze.Plan): Float {
        val sampler = Sampler(src, GAIN_STRIDE)
        val total = Parallel.reduce(
            sampler.chunks,
            { DoubleArray(RGB) },
            { into, other -> for (k in 0 until RGB) into[k] += other[k] }
        ) { sums, chunk ->
            val px = FloatArray(RGB)
            sampler.rows(chunk) { y, p ->
                for (i in 0 until p.size) {
                    px[0] = p.r[i]
                    px[1] = p.g[i]
                    px[2] = p.b[i]
                    Dehaze.accumulateGain(plan, sums, i * GAIN_STRIDE, y, px)
                }
            }
        }
        return Dehaze.gainOf(total)
    }

    /** Szum (σ w skali 0..255) z kilku pasów obrazu o pełnej rozdzielczości (uśrednianie zmniejszyłoby szum). */
    private fun noiseSigma(): Float {
        val band = min(h, NOISE_BAND_ROWS)
        val bands = if (h <= band) 1 else NOISE_BANDS
        val buf = IntArray(w * band)
        var sum = 0f
        for (k in 0 until bands) {
            val y0 = if (bands == 1) 0 else (h - band) * k / (bands - 1)
            src.getPixels(buf, 0, w, 0, y0, w, band)
            sum += SharpenPlanner.noiseSigma(SharpenPlanner.lumaOf(buf), w, band)
        }
        return sum / bands
    }

    /** Mapa wyostrzania świadomego treści ([SemanticSharpen]) z jasności zmniejszonego obrazu (próbki co 2 piksele). */
    private fun semanticMap(): SemanticSharpen.Map {
        val low = Lowres.forSize(w, h, SemanticSharpen.TARGET)
        val stride = min(SEMANTIC_STRIDE, low.factor)
        val sampler = Sampler(src, stride)
        val acc0 = Parallel.reduce(sampler.chunks, { Lowres.Accumulator(low) }, { a, b -> a.merge(b) }) { acc, chunk ->
            sampler.rows(chunk) { y, p ->
                for (i in 0 until p.size) acc.add(i * stride, y, LUMA_R * p.r[i] + LUMA_G * p.g[i] + LUMA_B * p.b[i])
            }
        }
        return SemanticSharpen.build(low, acc0.mean(0), faces)
    }

    /** Odszumianie koloru i dehaze na próbkach wiersza [y] (co [stride]-ty piksel), jak na pasach. */
    private fun restoreRow(chroma: ChromaDenoise.Plan?, dehaze: Dehaze.Plan?, p: Planes, y: Int, stride: Int) {
        val span = PixelSpan(p.size, y, h)
        chroma?.let { ChromaDenoise.applyPlanes(it, p, p.size, span, stride) }
        dehaze?.let { Dehaze.restorePlanes(it, p, p.size, span, stride) }
    }

    /** Maska tonalna z jasności obrazu po odszumianiu koloru i dehaze (podpróbkowanie co 3 piksele). */
    private fun toneMask(chroma: ChromaDenoise.Plan?, dehaze: Dehaze.Plan?): ToneMask {
        val stride = min(SAMPLE_STRIDE, ToneMask.Builder(w, h).cellSize)
        val sampler = Sampler(src, stride)
        val tone = Parallel.reduce(
            sampler.chunks,
            { ToneMask.Builder(w, h) },
            { a, b -> a.merge(b) }
        ) { builder, chunk ->
            sampler.rows(chunk) { y, p ->
                restoreRow(chroma, dehaze, p, y, stride)
                for (i in 0 until p.size) {
                    builder.addSample(i * stride, y, LUMA_R * p.r[i] + LUMA_G * p.g[i] + LUMA_B * p.b[i])
                }
            }
        }
        return tone.build()
    }

    /** Baza wyrazistości z jasności obrazu po odszumianiu, dehaze i korektach (bez ziarna i winiety). */
    private fun clarityMap(
        chroma: ChromaDenoise.Plan?,
        dehaze: Dehaze.Plan?,
        tone: ToneMask?
    ): DetailFilter.ClarityMap {
        val low = Lowres.forSize(w, h, DetailFilter.CLARITY_TARGET)
        val stride = min(SAMPLE_STRIDE, low.factor)
        val sampler = Sampler(src, stride)
        val clarity = Parallel.reduce(
            sampler.chunks,
            { ClarityState(Lowres.Accumulator(low), AdjustmentProcessor(adj)) },
            { a, b -> a.acc.merge(b.acc) }
        ) { state, chunk ->
            sampler.rows(chunk) { y, p ->
                restoreRow(chroma, dehaze, p, y, stride)
                state.processor.processPlanes(p, p.size, PixelSpan(p.size, y, h), tone, stride)
                for (i in 0 until p.size) {
                    state.acc.add(i * stride, y, (LUMA_R * p.r[i] + LUMA_G * p.g[i] + LUMA_B * p.b[i]) / MAX)
                }
            }
        }
        return DetailFilter.clarityMap(low, clarity.acc.mean(0))
    }

    private companion object {
        const val RGB = 3
        const val MAX = 255f
    }
}
