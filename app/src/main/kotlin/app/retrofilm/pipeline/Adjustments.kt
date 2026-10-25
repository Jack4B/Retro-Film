package app.retrofilm.pipeline

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.pow

/**
 * Korekty obrazu po nałożeniu filmu: kontrast, nasycenie i temperatura w zakresie −1..1,
 * ekspozycja w EV (−[EXPOSURE_MAX_EV]..+[EXPOSURE_MAX_EV]).
 * Wartości neutralne ([None]) omijają ten krok, więc wynik filmu (golden testy silnika) się nie zmienia.
 * Model opisany w [AdjustmentProcessor]; ten sam kod w podglądzie i w zapisie.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f
) {
    val isNeutral: Boolean get() = this == None

    companion object {
        val None = Adjustments()
        const val EXPOSURE_MAX_EV = 2f
    }
}

/**
 * Czysta matematyka korekt na pikselach ARGB (bez Androida), wzorowana na darktable / RawTherapee:
 * 1. ekspozycja i temperatura w świetle liniowym (mnożniki kanałów, jak „exposure” i balans bieli);
 *    temperatura to przesunięcie ±[TEMPERATURE_EV] EV między R i B przy stałej luminancji;
 * 2. miękkie przycięcie świateł (kolano od [KNEE]) zamiast ostrego obcięcia, potem kodowanie sRGB;
 * 3. nasycenie jak „vibrance”: plus wzmacnia głównie kolory mało nasycone (skóra i wysycone kolory
 *    nie przepalają się), minus odbarwia równomiernie, luminancja Rec.709 zachowana;
 * 4. kontrast krzywą sigmoidalną wokół szarości 0,5 (czernie i biele nieruchome, bez przycięć);
 *    wartości ujemne spłaszczają obraz ku szarości.
 */
class AdjustmentProcessor(private val adj: Adjustments) {
    private val gainR: Float
    private val gainG: Float
    private val gainB: Float
    private val toLinear = FloatArray(BYTE_LEVELS) { srgbToLinear(it / BYTE_MAX) }
    private val encode: FloatArray
    private val contrastLut =
        FloatArray(CONTRAST_SIZE + 1) { contrastCurve(it.toFloat() / CONTRAST_SIZE, adj.contrast) }

    init {
        val exposure = 2.0.pow(adj.exposure.toDouble()).toFloat()
        val shift = 2.0.pow((TEMPERATURE_EV * adj.temperature).toDouble()).toFloat()
        val r = shift
        val b = 1f / shift
        val norm = LUM_R * r + LUM_G + LUM_B * b
        gainR = exposure * r / norm
        gainG = exposure / norm
        gainB = exposure * b / norm
        // Kolano normalizuje się do największego mnożnika; bez rozjaśniania krzywa jest tożsamością.
        val ceiling = maxOf(1f, gainR, gainG, gainB)
        encode = FloatArray(ENCODE_SIZE + 1) { linearToSrgb(softClip(it.toFloat() / ENCODE_SCALE, ceiling)) }
    }

    /** Przetwarza [count] pikseli ARGB w miejscu (alfa bez zmian). */
    fun process(px: IntArray, count: Int = px.size) {
        val sat = adj.saturation
        val hasContrast = adj.contrast != 0f
        for (i in 0 until count) {
            val c = px[i]
            var r = encodeLinear(toLinear[(c shr RED_SHIFT) and BYTE_MASK] * gainR)
            var g = encodeLinear(toLinear[(c shr GREEN_SHIFT) and BYTE_MASK] * gainG)
            var b = encodeLinear(toLinear[c and BYTE_MASK] * gainB)
            if (sat != 0f) {
                val mx = maxOf(r, g, b)
                val chroma = if (mx > EPS) (mx - minOf(r, g, b)) / mx else 0f
                val f = if (sat < 0f) 1f + sat else 1f + sat * (1f - chroma)
                val y = LUM_R * r + LUM_G * g + LUM_B * b
                r = (y + (r - y) * f).coerceIn(0f, 1f)
                g = (y + (g - y) * f).coerceIn(0f, 1f)
                b = (y + (b - y) * f).coerceIn(0f, 1f)
            }
            if (hasContrast) {
                r = contrastLut[(r * CONTRAST_SIZE + HALF).toInt()]
                g = contrastLut[(g * CONTRAST_SIZE + HALF).toInt()]
                b = contrastLut[(b * CONTRAST_SIZE + HALF).toInt()]
            }
            px[i] = (c and ALPHA_MASK) or (to8(r) shl RED_SHIFT) or (to8(g) shl GREEN_SHIFT) or to8(b)
        }
    }

    private fun encodeLinear(v: Float): Float {
        val idx = (v * ENCODE_SCALE + HALF).toInt().coerceIn(0, ENCODE_SIZE)
        return encode[idx]
    }

    private fun to8(v: Float): Int = (v * BYTE_MAX + HALF).toInt().coerceIn(0, BYTE_LEVELS - 1)

    companion object {
        const val TEMPERATURE_EV = 0.4f
        const val KNEE = 0.8f
        private const val BYTE_LEVELS = 256
        private const val BYTE_MAX = 255f
        private const val BYTE_MASK = 0xFF
        private const val ALPHA_MASK = 0xFF000000.toInt()
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ENCODE_SCALE = 4096f
        private const val ENCODE_MAX_LINEAR = 4.5f
        private const val ENCODE_SIZE = (ENCODE_MAX_LINEAR * ENCODE_SCALE).toInt()
        private const val CONTRAST_SIZE = 1023
        private const val HALF = 0.5f
        private const val EPS = 1e-6f
        private const val LUM_R = 0.2126f
        private const val LUM_G = 0.7152f
        private const val LUM_B = 0.0722f
        private const val SIGMOID_GAIN = 8f
        private const val FLATTEN = 0.8f
        private const val SRGB_LINEAR_CUT = 0.04045f
        private const val SRGB_LINEAR_CUT_ENC = 0.0031308f
        private const val SRGB_SLOPE = 12.92f
        private const val SRGB_A = 0.055f
        private const val SRGB_GAMMA = 2.4

        fun srgbToLinear(v: Float): Float = if (v <=
            SRGB_LINEAR_CUT
        ) {
            v / SRGB_SLOPE
        } else {
            ((v + SRGB_A) / (1 + SRGB_A)).toDouble().pow(SRGB_GAMMA).toFloat()
        }

        fun linearToSrgb(v: Float): Float = if (v <= SRGB_LINEAR_CUT_ENC) {
            v * SRGB_SLOPE
        } else {
            ((1 + SRGB_A) * v.toDouble().pow(1 / SRGB_GAMMA) - SRGB_A).toFloat()
        }

        /**
         * Miękkie kolano świateł: tożsamość do [KNEE], potem płynne dojście do 1,0 w punkcie [ceiling]
         * (największy mnożnik korekt). Gdy [ceiling] <= 1, nic nie jest rozjaśniane i krzywa jest tożsamością.
         */
        fun softClip(v: Float, ceiling: Float): Float {
            if (ceiling <= 1f || v <= KNEE) return v.coerceAtMost(1f)
            val width = 1f - KNEE
            val shape = (1f - exp(-(v - KNEE) / width)) / (1f - exp(-(ceiling - KNEE) / width))
            return KNEE + width * shape.coerceAtMost(1f)
        }

        /** Krzywa kontrastu na wartości 0..1: sigmoida (c > 0) albo spłaszczenie (c < 0); 0, 0,5 i 1 stałe. */
        fun contrastCurve(x: Float, c: Float): Float = when {
            c == 0f -> x
            c < 0f -> (x * (1f + FLATTEN * c) - HALF * FLATTEN * c).coerceIn(0f, 1f)
            else -> sigmoid(x, SIGMOID_GAIN * c)
        }

        private fun sigmoid(x: Float, k: Float): Float {
            fun s(t: Float) = 1f / (1f + exp(-k * (t - HALF)))
            val lo = s(0f)
            return ((s(x) - lo) / (s(1f) - lo)).coerceIn(0f, 1f)
        }
    }
}

/** Nakłada [Adjustments] na bitmapę (nowa bitmapa ARGB_8888, źródło bez zmian). */
object AdjustmentsApplier {
    private const val ROWS_PER_CHUNK = 64

    fun apply(src: Bitmap, adj: Adjustments): Bitmap {
        if (adj.isNeutral) return src
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val processor = AdjustmentProcessor(adj)
        val buf = IntArray(w * ROWS_PER_CHUNK)
        var y = 0
        while (y < h) {
            val rows = minOf(ROWS_PER_CHUNK, h - y)
            src.getPixels(buf, 0, w, 0, y, w, rows)
            processor.process(buf, w * rows)
            out.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
        return out
    }

    /** Wygodny skrót dla testów i podglądów małych kafelków. */
    fun applyToPixel(argb: Int, adj: Adjustments): Int {
        val px = intArrayOf(argb)
        AdjustmentProcessor(adj).process(px)
        return px[0]
    }
}
