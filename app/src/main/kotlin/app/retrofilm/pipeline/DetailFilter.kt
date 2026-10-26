package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.max

/**
 * Wyostrzanie ([Sharpener]) i wyrazistość (lokalny kontrast: ta sama maska z dużym promieniem
 * i wagą półtonów) na pikselach ARGB, bez Androida. Promienie liczone od dłuższego boku obrazu, więc podgląd
 * i zapis wyglądają tak samo. Zmiana jasności jest dodawana do wszystkich kanałów, więc barwy się nie przesuwają.
 */
object DetailFilter {
    private const val CLARITY_RADIUS_DIV = 25
    private const val CLARITY_TARGET = 600
    private const val CLARITY_EPS = 0.0036f
    private const val CLARITY_GAIN = 0.9f
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

    /** Wygodne wywołanie z samą ilością wyostrzania i wyrazistością (ustawienia domyślne reszty parametrów). */
    fun apply(px: IntArray, width: Int, height: Int, sharpness: Float, clarity: Float) =
        apply(px, width, height, SharpenParams(sharpness), clarity, false)

    /**
     * Wyostrzanie ([Sharpener]) i wyrazistość −1..1 (minus zmiękcza); modyfikuje [px] w miejscu. Przy [maskPreview]
     * zamiast zdjęcia powstaje obraz maski krawędzi (biel = wyostrzane).
     */
    @Suppress("LongParameterList")
    fun apply(px: IntArray, width: Int, height: Int, sharp: SharpenParams, clarity: Float, maskPreview: Boolean) {
        if (sharp.amount <= 0f && clarity == 0f) return
        val luma = FloatArray(width * height) { lumaOf(px[it]) }
        if (maskPreview && sharp.amount > 0f) {
            val m = Sharpener.maskOnly(luma, width, height, sharp)
            for (i in px.indices) {
                val v = (m[i] * BYTE_MAX + HALF).toInt().coerceIn(0, MASK)
                px[i] = (px[i] and ALPHA_MASK) or (v shl RED_SHIFT) or (v shl GREEN_SHIFT) or v
            }
            return
        }
        val delta = if (sharp.amount > 0f) Sharpener.delta(luma, width, height, sharp) else FloatArray(luma.size)
        if (clarity != 0f) {
            val base = edgePreservingBase(luma, width, height)
            for (i in luma.indices) {
                val mid = 1f - abs(2f * luma[i] / BYTE_MAX - 1f)
                delta[i] += (luma[i] - base[i]) * clarity * CLARITY_GAIN * mid
            }
        }
        for (i in px.indices) px[i] = shifted(px[i], delta[i])
    }

    /**
     * Warstwa bazowa jasności z filtru prowadzonego w zmniejszonej skali: różnica od niej to lokalny kontrast
     * bez halo przy krawędziach (zwykłe rozmycie rysuje jasne obwódki wokół kontrastowych krawędzi).
     */
    private fun edgePreservingBase(luma: FloatArray, w: Int, h: Int): FloatArray {
        val low = Lowres.forSize(w, h, CLARITY_TARGET)
        val small = low.down(FloatArray(luma.size) { luma[it] / BYTE_MAX })
        val radius = max(2, max(low.w, low.h) / CLARITY_RADIUS_DIV)
        val filtered = GuidedFilter.filter(small, small, low.w, low.h, radius, CLARITY_EPS)
        val base = FloatArray(luma.size)
        for (y in 0 until h) for (x in 0 until w) base[y * w + x] = low.sample(filtered, x, y) * BYTE_MAX
        return base
    }

    private fun lumaOf(c: Int): Float =
        LUMA_R * ((c shr RED_SHIFT) and MASK) + LUMA_G * ((c shr GREEN_SHIFT) and MASK) + LUMA_B * (c and MASK)

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

    internal fun blurRows(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
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

    internal fun blurCols(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
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
