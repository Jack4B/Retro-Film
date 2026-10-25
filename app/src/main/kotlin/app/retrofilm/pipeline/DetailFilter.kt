package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.max

/**
 * Ostrzenie (maska unsharp na jasności) i wyrazistość (lokalny kontrast: ta sama maska z dużym promieniem
 * i wagą półtonów) na pikselach ARGB, bez Androida. Promienie liczone od dłuższego boku obrazu, więc podgląd
 * i zapis wyglądają tak samo. Zmiana jasności jest dodawana do wszystkich kanałów, więc barwy się nie przesuwają.
 */
object DetailFilter {
    private const val SHARP_RADIUS_DIV = 1500
    private const val CLARITY_RADIUS_DIV = 45
    private const val SHARP_GAIN = 1.6f
    private const val CLARITY_GAIN = 0.9f
    private const val NOISE_FLOOR = 2f
    private const val BOX_PASSES = 3
    private const val HALF = 0.5f
    private const val BYTE_MAX = 255f
    private const val MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val ALPHA_MASK = 0xFF000000.toInt()

    /** [sharpness] 0..1, [clarity] −1..1 (minus zmiękcza); 0 = bez zmian. Modyfikuje [px] w miejscu. */
    fun apply(px: IntArray, width: Int, height: Int, sharpness: Float, clarity: Float) {
        if (sharpness <= 0f && clarity == 0f) return
        val luma = FloatArray(width * height) { lumaOf(px[it]) }
        val delta = FloatArray(luma.size)
        val longSide = max(width, height)
        if (sharpness > 0f) {
            val radius = max(1, longSide / SHARP_RADIUS_DIV)
            val blur = boxBlur(luma, width, height, radius)
            for (i in luma.indices) delta[i] += softThreshold(luma[i] - blur[i]) * sharpness * SHARP_GAIN
        }
        if (clarity != 0f) {
            val radius = max(2, longSide / CLARITY_RADIUS_DIV)
            val blur = boxBlur(luma, width, height, radius)
            for (i in luma.indices) {
                val mid = 1f - abs(2f * luma[i] / BYTE_MAX - 1f)
                delta[i] += (luma[i] - blur[i]) * clarity * CLARITY_GAIN * mid
            }
        }
        for (i in px.indices) px[i] = shifted(px[i], delta[i])
    }

    private fun lumaOf(c: Int): Float =
        LUMA_R * ((c shr RED_SHIFT) and MASK) + LUMA_G * ((c shr GREEN_SHIFT) and MASK) + LUMA_B * (c and MASK)

    /** Drobne różnice (szum) są tłumione, żeby ostrzenie nie wzmacniało ziarna matrycy. */
    private fun softThreshold(d: Float): Float {
        val a = abs(d)
        return if (a <= NOISE_FLOOR) 0f else (a - NOISE_FLOOR) * (if (d < 0f) -1f else 1f)
    }

    private fun shifted(c: Int, d: Float): Int {
        fun ch(v: Int) = (v + d + HALF).toInt().coerceIn(0, MASK)
        val r = ch((c shr RED_SHIFT) and MASK)
        val g = ch((c shr GREEN_SHIFT) and MASK)
        val b = ch(c and MASK)
        return (c and ALPHA_MASK) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }

    /** Rozmycie pudełkowe (kilka przebiegów ≈ Gauss), liniowe w czasie, z obcinaniem brzegów. */
    internal fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        var a = src.copyOf()
        var b = FloatArray(src.size)
        repeat(BOX_PASSES) {
            blurRows(a, b, w, h, radius)
            blurCols(b, a, w, h, radius)
        }
        return a
    }

    private fun blurRows(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val window = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / window
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun blurCols(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val window = (2 * r + 1).toFloat()
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / window
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }
}
