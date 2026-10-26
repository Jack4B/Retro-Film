package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Sposób wyostrzania: standardowy (maska nieostra i dekonwolucja wg „Detalu”), maska nieostra albo dekonwolucja. */
enum class SharpenMode { Standard, Unsharp, Deconvolution }

/**
 * Parametry wyostrzania jak w programach do RAW: [amount] 0..1, [radius] 0,5..3 (1 = σ 1 px przy 4000 px
 * dłuższego boku, skaluje się z rozmiarem obrazu), [detail] 0..1 (0 = maska nieostra z tłumieniem aureoli,
 * 1 = dekonwolucja), [masking] 0..1 (0 = wszędzie, 1 = tylko mocne krawędzie).
 */
data class SharpenParams(
    val amount: Float,
    val radius: Float = DEFAULT_RADIUS,
    val detail: Float = DEFAULT_DETAIL,
    val masking: Float = 0f,
    val mode: SharpenMode = SharpenMode.Standard
) {
    companion object {
        const val DEFAULT_RADIUS = 1f
        const val DEFAULT_DETAIL = 0.25f
    }
}

/**
 * Wyostrzanie jasności: maska nieostra (USM) z miękkim ograniczeniem aureoli, dekonwolucja Richardsona–Lucy'ego
 * z gaussowskim PSF oraz maska krawędzi (jak „Masking” w Lightroomie). Liczone paskami wierszy z zapasem na
 * rozmycie, więc pamięć nie rośnie z rozmiarem zdjęcia. Wynik to różnica jasności do dodania do kanałów.
 */
internal object Sharpener {
    private const val REF_SIDE = 4000f
    private const val MIN_SIGMA = 0.5f
    private const val MAX_SIGMA = 4f
    private const val KERNEL_SPAN = 3f
    private const val USM_GAIN = 1.6f
    private const val DECONV_GAIN = 1.4f
    private const val NOISE_FLOOR = 2f
    private const val CLIP_BASE = 24f
    private const val CLIP_PER_DETAIL = 56f
    private const val ITER_STANDARD = 2
    private const val ITER_DECONV = 4
    private const val RATIO_MIN = 0.5f
    private const val RATIO_MAX = 2f
    private const val LUMA_MAX = 255f
    private const val MASK_LOW = 0.06f
    private const val MASK_BAND = 0.05f
    private const val STRIP_ROWS = 192
    private const val MIN_DECONV_WEIGHT = 0.02f
    private const val MARGIN_EXTRA = 2

    fun sigmaFor(radius: Float, longSide: Int): Float = (radius * longSide / REF_SIDE).coerceIn(MIN_SIGMA, MAX_SIGMA)

    /** Zapas wierszy wokół paska, w którym wynik [delta] jest już poprawny (wpływ sąsiedztwa rozmyć i dekonwolucji). */
    fun marginFor(p: SharpenParams, longSide: Int): Int {
        val iterations = if (p.mode == SharpenMode.Deconvolution) ITER_DECONV else ITER_STANDARD
        val reach = KERNEL_SPAN * sigmaFor(p.radius, longSide) * (iterations * 2 + MARGIN_EXTRA)
        return kotlin.math.ceil(reach).toInt() + 2
    }

    /**
     * Różnica jasności do dodania (0..255) dla każdego piksela; [stripRows] tylko do testów. [longSide] to dłuższy
     * bok całego obrazu, gdy [luma] jest tylko jego fragmentem (promień zależy od rozmiaru całości).
     */
    @Suppress("LongParameterList") // stripRows i longSide to parametry strojenia, nie dane
    fun delta(
        luma: FloatArray,
        w: Int,
        h: Int,
        p: SharpenParams,
        stripRows: Int = STRIP_ROWS,
        longSide: Int = max(w, h)
    ): FloatArray {
        val out = FloatArray(luma.size)
        if (p.amount <= 0f) return out
        val sigma = sigmaFor(p.radius, longSide)
        val iterations = if (p.mode == SharpenMode.Deconvolution) ITER_DECONV else ITER_STANDARD
        val margin = marginFor(p, longSide)
        Parallel.rows(h, stripRows) { y0, y1 ->
            val a = max(0, y0 - margin)
            val b = min(h, y1 + margin)
            val sub = luma.copyOfRange(a * w, b * w)
            val d = strip(sub, w, b - a, p, sigma, iterations)
            System.arraycopy(d, (y0 - a) * w, out, y0 * w, (y1 - y0) * w)
        }
        return out
    }

    /** Maska krawędzi 0..1 do podglądu („tylko krawędzie” w Lightroomie); przy masking = 0 wszędzie 1. */
    fun maskOnly(luma: FloatArray, w: Int, h: Int, p: SharpenParams): FloatArray {
        if (p.masking <= 0f) return FloatArray(luma.size) { 1f }
        val out = FloatArray(luma.size)
        val sigma = sigmaFor(p.radius, max(w, h))
        val margin = kotlin.math.ceil(KERNEL_SPAN * sigma).toInt() + MARGIN_EXTRA + 1
        Parallel.rows(h, STRIP_ROWS) { y0, y1 ->
            val a = max(0, y0 - margin)
            val b = min(h, y1 + margin)
            val sub = luma.copyOfRange(a * w, b * w)
            val blur = gauss(sub, w, b - a, sigma)
            val m = edgeMask(blur, w, b - a, p.masking, sigma)
            System.arraycopy(m, (y0 - a) * w, out, y0 * w, (y1 - y0) * w)
        }
        return out
    }

    @Suppress("LongParameterList")
    private fun strip(sub: FloatArray, w: Int, h: Int, p: SharpenParams, sigma: Float, iterations: Int): FloatArray {
        val blur = gauss(sub, w, h, sigma)
        val deconvWeight = when (p.mode) {
            SharpenMode.Standard -> p.detail
            SharpenMode.Unsharp -> 0f
            SharpenMode.Deconvolution -> 1f
        }
        val restored = if (deconvWeight > MIN_DECONV_WEIGHT) richardsonLucy(sub, w, h, sigma, iterations) else null
        val mask = if (p.masking > 0f) edgeMask(blur, w, h, p.masking, sigma) else null
        val clip = CLIP_BASE + CLIP_PER_DETAIL * deconvWeight
        val out = FloatArray(sub.size)
        for (i in sub.indices) {
            val usm = softThreshold(sub[i] - blur[i]) * USM_GAIN
            val deconv = if (restored != null) (restored[i] - sub[i]) * DECONV_GAIN else 0f
            val raw = ((1f - deconvWeight) * usm + deconvWeight * deconv) * p.amount
            out[i] = clip * raw / (clip + abs(raw)) * (mask?.get(i) ?: 1f)
        }
        return out
    }

    private fun softThreshold(d: Float): Float {
        val a = abs(d)
        return if (a <= NOISE_FLOOR) 0f else (a - NOISE_FLOOR) * (if (d < 0f) -1f else 1f)
    }

    /** Dekonwolucja Richardsona–Lucy'ego: u ← u · (PSF ⊗ (obraz / (PSF ⊗ u))), z ograniczeniem stosunku (szum). */
    private fun richardsonLucy(src: FloatArray, w: Int, h: Int, sigma: Float, iterations: Int): FloatArray {
        var u = src.copyOf()
        repeat(iterations) {
            val est = gauss(u, w, h, sigma)
            val ratio = FloatArray(src.size) { ((src[it] + 1f) / (est[it] + 1f)).coerceIn(RATIO_MIN, RATIO_MAX) }
            val corr = gauss(ratio, w, h, sigma)
            u = FloatArray(src.size) { (u[it] * corr[it]).coerceIn(0f, LUMA_MAX) }
        }
        return u
    }

    /** Siła krawędzi z gradientu (różnice centralne) rozmytej jasności; progi rosną z [masking]. */
    private fun edgeMask(blur: FloatArray, w: Int, h: Int, masking: Float, sigma: Float): FloatArray {
        val lo = masking * MASK_LOW
        val hi = lo + MASK_BAND
        val scale = max(1f, sigma) / LUMA_MAX
        val out = FloatArray(blur.size)
        for (y in 0 until h) {
            val up = max(0, y - 1) * w
            val down = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val gx = (blur[y * w + min(w - 1, x + 1)] - blur[y * w + max(0, x - 1)]) * HALF
                val gy = (blur[down + x] - blur[up + x]) * HALF
                val g = sqrt(gx * gx + gy * gy) * scale
                out[y * w + x] = smooth(lo, hi, g)
            }
        }
        return out
    }

    @Suppress("MagicNumber")
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Rozmycie gaussowskie rozdzielne z brzegami przedłużanymi (clamp). */
    fun gauss(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val radius = kotlin.math.ceil(KERNEL_SPAN * sigma).toInt().coerceAtLeast(1)
        val kernel = FloatArray(2 * radius + 1) { exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
        val sum = kernel.sum()
        for (i in kernel.indices) kernel[i] /= sum
        val tmp = FloatArray(src.size)
        Convolution.rows(src, tmp, w, h, kernel)
        val out = FloatArray(src.size)
        Convolution.columns(tmp, out, w, h, kernel)
        return out
    }

    private const val HALF = 0.5f
}
