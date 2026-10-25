package app.retrofilm.pipeline

import kotlin.math.pow

/**
 * Balans bieli jako adaptacja chromatyczna Bradforda w świetle liniowym (jak w programach do RAW):
 * suwak temperatury przesuwa założoną barwę oświetlenia w skali miredów względem D65, a tint (zielony ↔ magenta)
 * przesuwa ją prostopadle do krzywej dziennej. Wynik to macierz 3×3 (liniowe sRGB), z zachowaną luminancją bieli.
 * Użyte wzory są publiczne: przybliżenie krzywej światła dziennego CIE, macierz Bradforda, macierze sRGB D65.
 */
@Suppress("MagicNumber") // macierze 3×3 i współczynniki publicznych wzorów (CIE, Bradford, sRGB)
object WhiteBalance {
    /** Zakres temperatury przy skrajnym suwaku (±), w miredach (1 000 000 / K). */
    const val MIRED_RANGE = 70.0

    /** Przesunięcie współrzędnej y barwy bieli przy skrajnym tincie. */
    const val TINT_RANGE = 0.025

    private const val D65_KELVIN = 6504.0
    private const val MIRED_SCALE = 1_000_000.0
    private const val K_MIN = 4000.0
    private const val K_MID = 7000.0
    private const val K_MAX = 25000.0
    private const val LUM_R = 0.2126
    private const val LUM_G = 0.7152
    private const val LUM_B = 0.0722

    private val D65_XYZ = doubleArrayOf(0.95047, 1.0, 1.08883)
    private val BRADFORD = doubleArrayOf(0.8951, 0.2664, -0.1614, -0.7502, 1.7135, 0.0367, 0.0389, -0.0685, 1.0296)
    private val BRADFORD_INV = doubleArrayOf(
        0.9869929, -0.1470543, 0.1599627,
        0.4323053, 0.5183603, 0.0492912,
        -0.0085287, 0.0400428, 0.9684867
    )
    private val RGB_TO_XYZ = doubleArrayOf(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041
    )
    private val XYZ_TO_RGB = doubleArrayOf(
        3.2404542, -1.5371385, -0.4985314,
        -0.9692660, 1.8760108, 0.0415560,
        0.0556434, -0.2040259, 1.0572252
    )

    val IDENTITY: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** Macierz 3×3 (wierszami) na liniowym RGB; dla neutralnych wartości tożsamość. */
    fun matrix(temperature: Float, tint: Float): FloatArray {
        if (temperature == 0f && tint == 0f) return IDENTITY
        val mired = MIRED_SCALE / D65_KELVIN - temperature * MIRED_RANGE
        val (x, y0) = daylightXy(MIRED_SCALE / mired)
        val y = y0 + tint * TINT_RANGE
        val src = doubleArrayOf(x / y, 1.0, (1.0 - x - y) / y)
        val rhoS = mul3(BRADFORD, src)
        val rhoD = mul3(BRADFORD, D65_XYZ)
        val diag = doubleArrayOf(rhoD[0] / rhoS[0], 0.0, 0.0, 0.0, rhoD[1] / rhoS[1], 0.0, 0.0, 0.0, rhoD[2] / rhoS[2])
        val cat = mulM(BRADFORD_INV, mulM(diag, BRADFORD))
        val m = mulM(XYZ_TO_RGB, mulM(cat, RGB_TO_XYZ))
        val white = mul3(m, doubleArrayOf(1.0, 1.0, 1.0))
        val k = 1.0 / (LUM_R * white[0] + LUM_G * white[1] + LUM_B * white[2])
        return FloatArray(9) { (m[it] * k).toFloat() }
    }

    /** Współrzędne chromatyczności światła dziennego CIE dla [kelvin] (4000..25000 K, poza zakresem przycinane). */
    fun daylightXy(kelvin: Double): Pair<Double, Double> {
        val t = kelvin.coerceIn(K_MIN, K_MAX)
        val x = if (t <= K_MID) {
            -4.6070e9 / t.pow(3) + 2.9678e6 / t.pow(2) + 0.09911e3 / t + 0.244063
        } else {
            -2.0064e9 / t.pow(3) + 1.9018e6 / t.pow(2) + 0.24748e3 / t + 0.237040
        }
        return x to (-3.0 * x * x + 2.870 * x - 0.275)
    }

    private fun mul3(m: DoubleArray, v: DoubleArray) = DoubleArray(3) { r ->
        m[r * 3] * v[0] + m[r * 3 + 1] * v[1] + m[r * 3 + 2] * v[2]
    }

    private fun mulM(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        val r = i / 3
        val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }
}
