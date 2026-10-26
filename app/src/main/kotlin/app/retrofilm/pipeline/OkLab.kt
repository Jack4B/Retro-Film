package app.retrofilm.pipeline

import kotlin.math.sqrt

/**
 * Konwersja sRGB (kodowanie gamma 0..1) ↔ OKLab (Ottosson 2020) z tablicami dla gammy, wspólna dla nasycenia,
 * miksera HSL i tonowania. Wynik [fromRgb] jest w [lab] (L, a, b). [toRgb] pilnuje gamutu sRGB: kolor spoza
 * gamutu wraca przez zmniejszenie chromy (bisekcja) przy stałych L i odcieniu, a nie przez przycięcie kanałów,
 * które zmienia odcień. Instancja nie jest bezpieczna wątkowo (bufory robocze).
 */
internal class OkLab {
    val lab = FloatArray(3)
    private val lin = FloatArray(3)
    private val toLinear = FloatArray(LUT + 1) { AdjustmentProcessor.srgbToLinear(it.toFloat() / LUT) }
    private val toGamma = FloatArray(LUT + 1) { AdjustmentProcessor.linearToSrgb(it.toFloat() / LUT) }
    private val cbrtLut = FloatArray(CBRT_SIZE + 2) { Math.cbrt(Math.pow(it.toDouble() / CBRT_SCALE, 2.0)).toFloat() }

    /** Ustawia [lab] dla koloru [rgb] (gamma, 0..1). */
    fun fromRgb(rgb: FloatArray) {
        val r = lin(rgb[0])
        val g = lin(rgb[1])
        val b = lin(rgb[2])
        val l = cbrt(M1_00 * r + M1_01 * g + M1_02 * b)
        val m = cbrt(M1_10 * r + M1_11 * g + M1_12 * b)
        val s = cbrt(M1_20 * r + M1_21 * g + M1_22 * b)
        lab[0] = M2_00 * l + M2_01 * m + M2_02 * s
        lab[1] = M2_10 * l + M2_11 * m + M2_12 * s
        lab[2] = M2_20 * l + M2_21 * m + M2_22 * s
    }

    /** Zapisuje do [out] kolor (gamma) o L, a, b; poza gamutem cofa chromę do granicy gamutu. */
    fun toRgb(l: Float, a: Float, b: Float, out: FloatArray) {
        if (!fits(l, a, b)) {
            var lo = 0f
            var hi = 1f
            repeat(BISECTIONS) {
                val mid = (lo + hi) * HALF
                if (fits(l, a * mid, b * mid)) lo = mid else hi = mid
            }
            linear(l, a * lo, b * lo)
        }
        for (i in 0..2) out[i] = gamma(lin[i])
    }

    private fun fits(l: Float, a: Float, b: Float): Boolean {
        linear(l, a, b)
        return lin.all { it in -GAMUT_EPS..1f + GAMUT_EPS }
    }

    private fun linear(l: Float, a: Float, b: Float) {
        val l3 = cube(l + I1_01 * a + I1_02 * b)
        val m3 = cube(l + I1_11 * a + I1_12 * b)
        val s3 = cube(l + I1_21 * a + I1_22 * b)
        lin[0] = I2_00 * l3 + I2_01 * m3 + I2_02 * s3
        lin[1] = I2_10 * l3 + I2_11 * m3 + I2_12 * s3
        lin[2] = I2_20 * l3 + I2_21 * m3 + I2_22 * s3
    }

    /**
     * Pierwiastek sześcienny z tablicy indeksowanej √x (x^(1/3) = (√x)^(2/3)): gęstsza przy zerze, gdzie funkcja jest
     * najbardziej stroma; kilka razy szybszy niż [Math.cbrt] na milionach pikseli.
     */
    private fun cbrt(x: Float): Float {
        if (x <= 0f) return 0f
        val u = sqrt(x) * CBRT_SCALE
        val i = u.toInt()
        if (i >= CBRT_SIZE) return Math.cbrt(x.toDouble()).toFloat()
        val t = u - i
        return cbrtLut[i] + (cbrtLut[i + 1] - cbrtLut[i]) * t
    }

    private fun lin(v: Float): Float = toLinear[(v.coerceIn(0f, 1f) * LUT + HALF).toInt()]

    private fun gamma(v: Float): Float = toGamma[(v.coerceIn(0f, 1f) * LUT + HALF).toInt()]

    private fun cube(v: Float) = v * v * v

    @Suppress("MagicNumber")
    companion object {
        private const val LUT = 8192
        private const val CBRT_SIZE = 4096
        private const val CBRT_SCALE = 3500f
        private const val HALF = 0.5f
        private const val BISECTIONS = 8
        private const val GAMUT_EPS = 1e-3f

        /** Kąt barwy OKLab (stopnie 0..360) dla (a, b). */
        fun hueDegrees(a: Float, b: Float): Float {
            val deg = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble())).toFloat()
            return if (deg < 0f) deg + 360f else deg
        }

        private const val M1_00 = 0.4122214708f
        private const val M1_01 = 0.5363325363f
        private const val M1_02 = 0.0514459929f
        private const val M1_10 = 0.2119034982f
        private const val M1_11 = 0.6806995451f
        private const val M1_12 = 0.1073969566f
        private const val M1_20 = 0.0883024619f
        private const val M1_21 = 0.2817188376f
        private const val M1_22 = 0.6299787005f
        private const val M2_00 = 0.2104542553f
        private const val M2_01 = 0.7936177850f
        private const val M2_02 = -0.0040720468f
        private const val M2_10 = 1.9779984951f
        private const val M2_11 = -2.4285922050f
        private const val M2_12 = 0.4505937099f
        private const val M2_20 = 0.0259040371f
        private const val M2_21 = 0.7827717662f
        private const val M2_22 = -0.8086757660f
        private const val I1_01 = 0.3963377774f
        private const val I1_02 = 0.2158037573f
        private const val I1_11 = -0.1055613458f
        private const val I1_12 = -0.0638541728f
        private const val I1_21 = -0.0894841775f
        private const val I1_22 = -1.2914855480f
        private const val I2_00 = 4.0767416621f
        private const val I2_01 = -3.3077115913f
        private const val I2_02 = 0.2309699292f
        private const val I2_10 = -1.2684380046f
        private const val I2_11 = 2.6097574011f
        private const val I2_12 = -0.3413193965f
        private const val I2_20 = -0.0041960863f
        private const val I2_21 = -0.7034186147f
        private const val I2_22 = 1.7076147010f
    }
}
