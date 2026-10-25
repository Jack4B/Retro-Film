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
    val temperature: Float = 0f,
    /** Światła −1..1: minus przywraca szczegóły w jasnych partiach, plus je rozjaśnia. */
    val highlights: Float = 0f,
    /** Cienie −1..1: plus rozjaśnia ciemne partie, minus je pogłębia. */
    val shadows: Float = 0f,
    /** Blaknięcie 0..1: podniesiona czerń i przytłumiona biel (wygląd starej odbitki). */
    val fade: Float = 0f,
    /** Ziarno filmowe 0..1. */
    val grain: Float = 0f,
    /** Winieta 0..1: przyciemnione rogi. */
    val vignette: Float = 0f,
    /** Ostrość 0..1: maska unsharp na jasności (zob. [DetailFilter]). */
    val sharpness: Float = 0f,
    /** Wyrazistość −1..1: lokalny kontrast w półtonach; minus zmiękcza. */
    val clarity: Float = 0f
) {
    val isNeutral: Boolean get() = this == None

    /** Czy potrzebny jest przebieg po sąsiedztwie pikseli ([DetailFilter]). */
    val hasDetail: Boolean get() = sharpness > 0f || clarity != 0f

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

    private val rgb = FloatArray(3)
    private val hasTone = adj.shadows != 0f || adj.highlights != 0f
    private val hasEffects = adj.vignette > 0f || adj.grain > 0f

    /**
     * Przetwarza [count] pikseli ARGB w miejscu (alfa bez zmian). [width], [firstRow] i [totalRows]
     * opisują położenie porcji w obrazie (winieta i ziarno zależą od współrzędnych).
     */
    fun process(px: IntArray, count: Int = px.size, width: Int = count, firstRow: Int = 0, totalRows: Int = 1) {
        val cell = maxOf(1, maxOf(width, totalRows) / GRAIN_CELL_DIVISOR)
        for (i in 0 until count) {
            val c = px[i]
            rgb[0] = encodeLinear(toLinear[(c shr RED_SHIFT) and BYTE_MASK] * gainR)
            rgb[1] = encodeLinear(toLinear[(c shr GREEN_SHIFT) and BYTE_MASK] * gainG)
            rgb[2] = encodeLinear(toLinear[c and BYTE_MASK] * gainB)
            if (adj.saturation != 0f) vibrance()
            if (hasTone) toneRecovery()
            if (adj.contrast !=
                0f
            ) {
                for (k in 0..2) rgb[k] = contrastLut[(rgb[k].coerceIn(0f, 1f) * CONTRAST_SIZE + HALF).toInt()]
            }
            if (adj.fade > 0f) fade()
            if (hasEffects) effects(i % width, firstRow + i / width, width, totalRows, cell)
            px[i] = (c and ALPHA_MASK) or (to8(rgb[0]) shl RED_SHIFT) or (to8(rgb[1]) shl GREEN_SHIFT) or to8(rgb[2])
        }
    }

    private fun luma() = LUM_R * rgb[0] + LUM_G * rgb[1] + LUM_B * rgb[2]

    private fun vibrance() {
        val mx = maxOf(rgb[0], rgb[1], rgb[2])
        val chroma = if (mx > EPS) (mx - minOf(rgb[0], rgb[1], rgb[2])) / mx else 0f
        val f = if (adj.saturation < 0f) 1f + adj.saturation else 1f + adj.saturation * (1f - chroma)
        val y = luma()
        for (k in 0..2) rgb[k] = (y + (rgb[k] - y) * f).coerceIn(0f, 1f)
    }

    /** Cienie i światła: wpływ maleje do zera po drugiej stronie skali jasności (kwadratowa waga). */
    private fun toneRecovery() {
        val y = luma()
        val wShadow = (1f - y) * (1f - y)
        val wHighlight = y * y
        for (k in 0..2) {
            rgb[k] = shift(shift(rgb[k], adj.shadows, wShadow), adj.highlights, wHighlight)
        }
    }

    private fun shift(v: Float, amount: Float, weight: Float): Float =
        v + amount * TONE_GAIN * weight * (if (amount > 0f) 1f - v else v)

    private fun fade() {
        val lift = FADE_LIFT * adj.fade
        val top = FADE_TOP * adj.fade
        for (k in 0..2) rgb[k] = lift + rgb[k] * (1f - lift - top)
    }

    private fun effects(x: Int, y: Int, width: Int, rows: Int, cell: Int) {
        if (adj.vignette > 0f) {
            val nx = (x + HALF) / width * 2f - 1f
            val ny = (y + HALF) / rows * 2f - 1f
            val d2 = (nx * nx + ny * ny) / 2f
            val f = 1f - adj.vignette * VIGNETTE_MAX * d2 * d2
            for (k in 0..2) rgb[k] *= f
        }
        if (adj.grain > 0f) {
            val gx = x / cell
            val gy = y / cell
            val amp = adj.grain * GRAIN_MAX * (1f - kotlin.math.abs(2f * luma() - 1f) * GRAIN_MIDTONE_BIAS)
            val shared = noise(gx, gy, 0)
            for (k in 0..2) rgb[k] += (shared * GRAIN_SHARED + noise(gx, gy, k + 1) * (1f - GRAIN_SHARED)) * amp
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
        private const val TONE_GAIN = 0.5f
        private const val FADE_LIFT = 0.12f
        private const val FADE_TOP = 0.06f
        private const val VIGNETTE_MAX = 0.65f
        private const val GRAIN_MAX = 0.12f
        private const val GRAIN_MIDTONE_BIAS = 0.6f
        private const val GRAIN_SHARED = 0.8f
        private const val GRAIN_CELL_DIVISOR = 1400
        private const val HASH_X = 374761393
        private const val HASH_Y = 668265263
        private const val HASH_SALT = 1442695041
        private const val HASH_MIX = 1274126177
        private const val HASH_SHIFT_A = 13
        private const val HASH_SHIFT_B = 16
        private const val HASH_MASK = 0xFFFF
        private const val HASH_HALF = 32767.5f
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

        /** Deterministyczny szum −1..1 dla komórki ([x], [y]) i kanału [salt] (ten sam obraz = to samo ziarno). */
        fun noise(x: Int, y: Int, salt: Int): Float {
            var h = x * HASH_X + y * HASH_Y + salt * HASH_SALT
            h = (h xor (h ushr HASH_SHIFT_A)) * HASH_MIX
            h = h xor (h ushr HASH_SHIFT_B)
            return (h and HASH_MASK) / HASH_HALF - 1f
        }

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
            processor.process(buf, w * rows, w, y, h)
            out.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
        if (adj.hasDetail) {
            val all = IntArray(w * h)
            out.getPixels(all, 0, w, 0, 0, w, h)
            DetailFilter.apply(all, w, h, adj.sharpness, adj.clarity)
            out.setPixels(all, 0, w, 0, 0, w, h)
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
