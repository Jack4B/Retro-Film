package app.retrofilm.pipeline

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Ujemne światła jako kompresja bazy w logarytmie jasności z wzmocnieniem detalu w dwóch skalach (rozkład na bazę
 * grubą, detal średni i drobny; por. Durand i Dorsey 2002, local Laplacian: Paris i in. 2011). Zamiast samego
 * przyciemnienia bazy (które zostawia obraz płaski i szary) detal ponad bazą rośnie tak, by jego bezwzględny
 * kontrast się zachował: przy przyciemnieniu bazy mnożnikiem g detal skali średniej i drobnej jest wzmacniany
 * (1 + k·(1/g − 1)) razy, czyli mnożnik piksela to g · exp(c · (k1 · ln(średnia/baza) + k0 · ln(piksel/średnia)))
 * z c = 1/g − 1. Dodatkowo lekko rośnie chroma w światłach, a
 * prawie wypalone, płaskie obszary (średnia bliska bieli) są przyciemniane słabiej, bo nie mają detalu do
 * odzyskania i zszarzałyby bez zysku.
 */
internal class HighlightRecovery(highlights: Float) {
    private val strength = -highlights.coerceIn(-1f, 0f)
    private val weight = FloatArray(LEVELS) { AdjustmentProcessor.highlightWeight(it / LEVEL_MAX) }
    private val lnGain = FloatArray(LEVELS) {
        ln(AdjustmentProcessor.toneGain(highlights, AdjustmentProcessor.HIGHLIGHT_EV, weight[it]))
    }

    /** Mnożnik jasności piksela [own] przy bazie grubej [base] i średniej [mid] (0..1); [midRatio] = ln(mid/base). */
    fun ratio(own: Float, base: Float, mid: Float, midRatio: Float): Float {
        val i = index(base)
        if (lnGain[i] == 0f) return 1f
        val fine = ln(max(own, MIN_LUMA) / max(mid, MIN_LUMA)).coerceIn(-FINE_LIMIT, FINE_LIMIT)
        val flat = smoothstep(PROTECT_FROM, PROTECT_TO, mid)
        val lg = lnGain[i] * (1f - PROTECT * flat)
        val compensation = exp(-lg) - 1f
        val x = lg + compensation * (DETAIL_MID * midRatio + DETAIL_FINE * fine)
        return exp(x.coerceIn(-X_LIMIT, X_LIMIT))
    }

    /** Dodatek do skalowania chromy: kolor w światłach nie szarzeje po przyciemnieniu. */
    fun chromaBoost(base: Float): Float = CHROMA_BOOST * strength * weight[index(base)]

    private fun index(y: Float): Int = (y * LEVEL_MAX + HALF).toInt().coerceIn(0, LEVELS - 1)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (THREE - TWO * t)
    }

    private companion object {
        const val LEVELS = 256
        const val LEVEL_MAX = 255f
        const val HALF = 0.5f
        const val MIN_LUMA = 1e-4f

        /**
         * Krotność odtwarzania utraconego przez przyciemnienie kontrastu w skali średniej i drobnej. Wartości > 1,
         * bo kontrast mierzony w poziomach bezwzględnych spada bardziej, niż wynika z samego mnożnika (część detalu
         * leży poza obiema skalami); dobrane na zdjęciach stockowych (kontrast w światłach ×0,9–1,2 oryginału).
         */
        const val DETAIL_MID = 1.4f
        const val DETAIL_FINE = 2.0f
        const val FINE_LIMIT = 1.4f
        const val X_LIMIT = 3f
        const val CHROMA_BOOST = 0.18f

        /** Ochrona wypalonych płaszczyzn: do 80% mniej przyciemnienia, gdy lokalna średnia > 0,94..0,99. */
        const val PROTECT_FROM = 0.94f
        const val PROTECT_TO = 0.99f
        const val PROTECT = 0.8f
        const val TWO = 2f
        const val THREE = 3f
    }
}
