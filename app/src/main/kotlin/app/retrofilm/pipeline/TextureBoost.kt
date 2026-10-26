package app.retrofilm.pipeline

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Tekstura: detal średniej i drobnej skali (rozkład logarytmiczny jak w [HighlightRecovery]) wzmacniany lub tłumiony
 * bez zmiany bazy tonalnej, więc nie przesuwa jasności, tylko kontrast struktur (skóra, liście, tkaniny). Filtr
 * prowadzony zachowuje krawędzie, a w cieniach działanie maleje (nie wzmacniamy szumu).
 */
internal class TextureBoost(private val texture: Float) {
    /** Mnożnik jasności piksela [own] przy lokalnej średniej [mid] i ln(średnia/gruba) = [midRatio]. */
    fun ratio(own: Float, mid: Float, midRatio: Float): Float {
        val fine = ln(max(own, MIN_LUMA) / max(mid, MIN_LUMA)).coerceIn(-FINE_LIMIT, FINE_LIMIT)
        val gate = smoothstep(GATE_FROM, GATE_TO, own)
        val x = texture * gate * (WEIGHT_MID * midRatio + WEIGHT_FINE * fine)
        return exp(x.coerceIn(-X_LIMIT, X_LIMIT))
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (THREE - TWO * t)
    }

    private companion object {
        const val MIN_LUMA = 1e-4f
        const val WEIGHT_MID = 1.0f
        const val WEIGHT_FINE = 0.8f
        const val FINE_LIMIT = 1.2f
        const val X_LIMIT = 0.8f

        /** Bramka szumu: poniżej 0,05 jasności brak działania, pełne od 0,25. */
        const val GATE_FROM = 0.05f
        const val GATE_TO = 0.25f
        const val TWO = 2f
        const val THREE = 3f
    }
}

/**
 * Krzywa białych i czerni na jasności, stała w 0 i 1 (nie robi szarej czerni ani nie ucina bieli): poprawka
 * ±[WHITES_SPAN] przy ok. 0,78 jasności (biele) i ±[BLACKS_SPAN] przy ok. 0,15 (czernie), zero na końcach.
 */
internal object EndsCurve {
    private const val WHITES_SPAN = 0.12f
    private const val BLACKS_SPAN = 0.10f
    private const val WHITE_FROM = 0.55f
    private const val BLACK_TO = 0.45f
    private const val FOUR = 4f
    private const val TWO = 2f
    private const val THREE = 3f

    fun at(y: Float, whites: Float, blacks: Float): Float {
        val t = smoothstep(WHITE_FROM, 1f, y)
        val u = 1f - smoothstep(0f, BLACK_TO, y)
        val hi = FOUR * t * (1f - t)
        val lo = FOUR * u * (1f - u)
        return (y + WHITES_SPAN * whites * hi + BLACKS_SPAN * blacks * lo).coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (THREE - TWO * t)
    }
}
