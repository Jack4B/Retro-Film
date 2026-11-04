package app.retrofilm.pipeline

import kotlin.math.abs
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

    val IDENTITY: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /**
     * Macierz 3×3 (wierszami) na liniowym RGB; dla neutralnych wartości tożsamość. [dx], [dy] przesuwają założoną
     * barwę oświetlenia (xy) poza krzywą dzienną + tint – reszta automatycznej korekty, której suwaki nie pokazują.
     */
    fun matrix(temperature: Float, tint: Float, dx: Float = 0f, dy: Float = 0f): FloatArray {
        if (abs(temperature) + abs(tint) + abs(dx) + abs(dy) == 0f) return IDENTITY
        val m = Cat.adapt(sourceXy(temperature, tint, dx, dy), Cat.D65_XY)
        val white = Cat.mul3(m, doubleArrayOf(1.0, 1.0, 1.0))
        val k = 1.0 / (LUM_R * white[0] + LUM_G * white[1] + LUM_B * white[2])
        return FloatArray(9) { (m[it] * k).toFloat() }
    }

    /** Założona barwa oświetlenia (xy) dla suwaków i przesunięcia [dx], [dy]. */
    fun sourceXy(temperature: Float, tint: Float, dx: Float = 0f, dy: Float = 0f): Pair<Double, Double> {
        val mired = MIRED_SCALE / D65_KELVIN - temperature * MIRED_RANGE
        val (x, y0) = daylightXy(MIRED_SCALE / mired)
        return x + dx to y0 + tint * TINT_RANGE + dy
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
}
