package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Odbandowanie wejścia filmu. Zdjęcie 8-bitowe ma w gładkich gradientach (niebo, tła) schodki co poziom; film (LUT) o
 * nachyleniu > 1 rozciąga je do 2–4 poziomów i zostają widoczne kontury (pomiar A0: 8–24% schodków osi neutralnej
 * rośnie o ≥ 2 poziomy). Tu wartości podpoziomowe są odtwarzane przez uśrednienie oknem szerszym niż schodek, ale
 * tylko tam, gdzie obraz jest płaski (lokalne odchylenie standardowe σ ≤ [SIGMA_FLAT] poziomu; powyżej [SIGMA_EDGE]
 * bez zmian), więc krawędzie i tekstura (także szum, który sam maskuje pasmowanie) zostają nietknięte. Poprawka jest
 * ograniczona do ±[MAX_DELTA] poziomów, a końcowa kwantyzacja z ditheringiem rozprasza resztę błędu. Działa na
 * kanałach float 0..255.
 */
internal object Deband {
    private const val MIN_RADIUS = 8
    private const val RADIUS_DIV = 120
    private const val SIGMA_FLAT = 1.0f
    private const val SIGMA_EDGE = 2.0f
    private const val MAX_DELTA = 3f
    private const val SMOOTH_A = 3f
    private const val SMOOTH_B = 2f
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    /** Promień okna (piksele) dla zdjęcia o dłuższym boku [longSide]; to także zapas wierszy wokół pasa. */
    fun radiusFor(longSide: Int): Int = max(MIN_RADIUS, longSide / RADIUS_DIV)

    /** Odbandowuje pas [count] pikseli (całe wiersze o szerokości [width]) oknem o promieniu [radius]. */
    fun applyPlanes(planes: Planes, count: Int, width: Int, radius: Int) {
        val rows = count / width
        val luma = FloatArray(count) { LUMA_R * planes.r[it] + LUMA_G * planes.g[it] + LUMA_B * planes.b[it] }
        val mean = GuidedFilter.boxMean(luma, width, rows, radius)
        val square = GuidedFilter.boxMean(FloatArray(count) { luma[it] * luma[it] }, width, rows, radius)
        val weight = FloatArray(count) { flatness(sqrt(max(square[it] - mean[it] * mean[it], 0f))) }
        val shape = Window(width, rows, radius)
        smooth(planes.r, count, shape, weight)
        smooth(planes.g, count, shape, weight)
        smooth(planes.b, count, shape, weight)
    }

    private class Window(val width: Int, val rows: Int, val radius: Int)

    private fun smooth(plane: FloatArray, count: Int, shape: Window, weight: FloatArray) {
        val blurred = GuidedFilter.boxMean(plane.copyOf(count), shape.width, shape.rows, shape.radius)
        for (i in 0 until count) {
            val d = (weight[i] * (blurred[i] - plane[i])).coerceIn(-MAX_DELTA, MAX_DELTA)
            plane[i] = (plane[i] + d).coerceIn(0f, MAX)
        }
    }

    /** 1 w płaskim obszarze (σ ≤ [SIGMA_FLAT]), 0 na krawędzi lub teksturze (σ ≥ [SIGMA_EDGE]), płynnie pomiędzy. */
    private fun flatness(sigma: Float): Float {
        val t = ((sigma - SIGMA_FLAT) / (SIGMA_EDGE - SIGMA_FLAT)).coerceIn(0f, 1f)
        return 1f - t * t * (SMOOTH_A - SMOOTH_B * t)
    }

    private const val MAX = 255f
}
