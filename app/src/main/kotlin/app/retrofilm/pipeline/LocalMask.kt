package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Kształt maski lokalnej: gradient liniowy (jak filtr połówkowy) albo radialny (elipsa). */
enum class MaskShape { Linear, Radial }

/**
 * Maska lokalna: obszar zdjęcia ze współrzędnymi znormalizowanymi do kadru (0..1), w którym działają własne korekty
 * ([exposure] w EV/2, [contrast], [saturation], [temperature], [highlights], [shadows] w −1..1 jak w [Adjustments]).
 * Liniowa: linia przez ([centerX], [centerY]) obrócona o [angle]°, efekt po jednej stronie, miękkie przejście o
 * szerokości [feather]. Radialna: elipsa o środku ([centerX], [centerY]), promieniu [size] (ułamek szerokości),
 * proporcjach [ratio] (wysokość/szerokość), obrócona o [angle]°, efekt w środku, [feather] to miękkość krawędzi.
 * [invert] odwraca obszar.
 */
data class LocalMask(
    val shape: MaskShape = MaskShape.Linear,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.35f,
    val angle: Float = 0f,
    val size: Float = 0.4f,
    val ratio: Float = 1f,
    val feather: Float = 0.5f,
    val invert: Boolean = false,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f
) {
    /** Czy maska cokolwiek zmienia (bez korekt jest pomijana). */
    val hasEffect: Boolean
        get() = exposure != 0f || contrast != 0f || saturation != 0f || temperature != 0f ||
            highlights != 0f || shadows != 0f

    /** Korekty tej maski jako zwykłe [Adjustments] (ten sam, sprawdzony tor obliczeń). */
    fun toAdjustments(): Adjustments = Adjustments(
        exposure = exposure * Adjustments.EXPOSURE_MAX_EV,
        contrast = contrast,
        saturation = saturation,
        temperature = temperature,
        highlights = highlights,
        shadows = shadows
    )

    companion object {
        const val MAX_MASKS = 3
    }
}

/** Siła maski 0..1 w pikselu: geometria policzona raz dla obrazu [w]×[h]. */
class MaskShapeMath(private val m: LocalMask, private val w: Int, private val h: Int) {
    private val rad = m.angle * PI.toFloat() / HALF_TURN
    private val cosA = cos(rad)
    private val sinA = sin(rad)
    private val cx = m.centerX * w
    private val cy = m.centerY * h
    private val band = maxOf(1f, m.feather * hypot(w.toFloat(), h.toFloat()) * BAND_FRACTION)
    private val rx = maxOf(1f, m.size * w)
    private val ry = maxOf(1f, m.size * w * m.ratio.coerceIn(MIN_RATIO, 1f))

    /** Siła maski w środku piksela ([x], [y]). */
    fun alpha(x: Int, y: Int): Float {
        val dx = x + HALF - cx
        val dy = y + HALF - cy
        val a = when (m.shape) {
            MaskShape.Linear -> linear(dx, dy)
            MaskShape.Radial -> radial(dx, dy)
        }
        return if (m.invert) 1f - a else a
    }

    /** Odległość ze znakiem od linii; po stronie ujemnej (nad linią dla kąta 0) efekt pełny. */
    private fun linear(dx: Float, dy: Float): Float {
        val d = -sinA * dx + cosA * dy
        return 1f - smoothstep(-band, band, d)
    }

    private fun radial(dx: Float, dy: Float): Float {
        val u = (cosA * dx + sinA * dy) / rx
        val v = (-sinA * dx + cosA * dy) / ry
        val r = sqrt(u * u + v * v)
        return 1f - smoothstep(1f - m.feather.coerceIn(0f, 1f) * FEATHER_INNER, 1f, r)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        if (abs(e1 - e0) < EPS) return if (x < e0) 0f else 1f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (SMOOTH_A - SMOOTH_B * t)
    }

    private companion object {
        const val HALF_TURN = 180f
        const val HALF = 0.5f
        const val BAND_FRACTION = 0.5f
        const val MIN_RATIO = 0.2f
        const val FEATHER_INNER = 0.95f
        const val EPS = 1e-6f
        const val SMOOTH_A = 3f
        const val SMOOTH_B = 2f
    }
}

/**
 * Nakłada maski lokalne na już skorygowane piksele: dla każdej maski liczy jej korekty ([AdjustmentProcessor])
 * na kopii porcji i miesza z oryginałem siłą maski w pikselu (lerp po kanałach w kodowaniu gamma). Piksele z siłą
 * zero zostają bez zmian, a porcje całkiem poza maską nie są w ogóle liczone. Cienie i światła w masce działają
 * punktowo (bez maski tonalnej obrazu), bo wejściem są już skorygowane globalnie piksele.
 */
internal class MaskBlender(masks: List<LocalMask>, private val w: Int, private val h: Int) {
    private class Layer(val shape: MaskShapeMath, val processor: AdjustmentProcessor)

    private val layers = masks.filter { it.hasEffect }.take(LocalMask.MAX_MASKS).map {
        Layer(MaskShapeMath(it, w, h), AdjustmentProcessor(it.toAdjustments()))
    }
    private var alpha = FloatArray(0)
    private var scratch = IntArray(0)

    val isEmpty: Boolean get() = layers.isEmpty()

    /** Miesza maski do [px] ([count] pikseli, wiersze od [firstRow]). */
    fun blend(px: IntArray, count: Int, firstRow: Int) {
        if (alpha.size < count) {
            alpha = FloatArray(count)
            scratch = IntArray(count)
        }
        for (layer in layers) {
            var any = false
            for (i in 0 until count) {
                val a = layer.shape.alpha(i % w, firstRow + i / w)
                alpha[i] = a
                if (a > 0f) any = true
            }
            if (!any) continue
            System.arraycopy(px, 0, scratch, 0, count)
            layer.processor.process(scratch, count, PixelSpan(w, firstRow, h))
            for (i in 0 until count) if (alpha[i] > 0f) px[i] = mix(px[i], scratch[i], alpha[i])
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        var out = a and ALPHA_MASK
        for (shift in intArrayOf(RED_SHIFT, GREEN_SHIFT, 0)) {
            val ca = (a shr shift) and BYTE
            val cb = (b shr shift) and BYTE
            out = out or ((ca + (cb - ca) * t + ROUND).toInt().coerceIn(0, BYTE) shl shift)
        }
        return out
    }

    private companion object {
        const val ALPHA_MASK = 0xFF000000.toInt()
        const val BYTE = 0xFF
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val ROUND = 0.5f
    }
}

/** Podgląd maski: czerwona nakładka o przezroczystości proporcjonalnej do siły maski (do ustawiania, nie do zapisu). */
object MaskPreview {
    private const val LONG_SIDE = 160
    private const val TINT = 0xFF3040
    private const val MAX_ALPHA = 150f
    private const val ALPHA_SHIFT = 24

    /** Bitmapa o proporcjach kadru [w]×[h] (dłuższy bok [LONG_SIDE] px) z nałożoną maską [mask]. */
    fun render(mask: LocalMask, w: Int, h: Int): android.graphics.Bitmap {
        val k = LONG_SIDE.toFloat() / maxOf(w, h)
        val pw = (w * k).toInt().coerceAtLeast(1)
        val ph = (h * k).toInt().coerceAtLeast(1)
        val math = MaskShapeMath(mask, pw, ph)
        val px = IntArray(pw * ph) { ((math.alpha(it % pw, it / pw) * MAX_ALPHA).toInt() shl ALPHA_SHIFT) or TINT }
        return android.graphics.Bitmap.createBitmap(px, pw, ph, android.graphics.Bitmap.Config.ARGB_8888)
    }
}
