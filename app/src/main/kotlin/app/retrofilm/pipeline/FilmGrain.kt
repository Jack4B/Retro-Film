package app.retrofilm.pipeline

import kotlin.math.floor

/**
 * Ziarno filmowe jako szum wartościowy (value noise) z interpolacją gładką w dwóch oktawach:
 * gruba daje „chmurki” srebra, drobna ostrość ziarna. Deterministyczne (ten sam obraz = to samo ziarno),
 * więc podgląd i zapis są identyczne. Siła zależy od jasności: największa w półtonach, malejąca w czerni i bieli.
 */
internal object FilmGrain {
    private const val COARSE_WEIGHT = 0.6f
    private const val FINE_WEIGHT = 0.4f
    private const val COARSE_SCALE = 2
    private const val MIDTONE_FLOOR = 0.35f
    private const val MIDTONE_RANGE = 0.65f
    private const val MIDTONE_PEAK = 4f
    private const val SMOOTH_A = 3f
    private const val SMOOTH_B = 2f
    private const val CHROMA_SALT = 7

    /** Wspólny (jasnościowy) szum −1..1 w punkcie ([x], [y]) dla komórki o boku [cell]. */
    fun luma(x: Int, y: Int, cell: Int): Float =
        COARSE_WEIGHT * smooth(x, y, cell * COARSE_SCALE, 0) + FINE_WEIGHT * smooth(x, y, cell, 1)

    /** Szum barwny −1..1 dla kanału [channel] (bez interpolacji: drobne plamki koloru). */
    fun chroma(x: Int, y: Int, cell: Int, channel: Int): Float =
        AdjustmentProcessor.noise(x / cell, y / cell, CHROMA_SALT + channel)

    /** Waga ziarna dla jasności [luma] 0..1: wąsko w czerni i bieli, pełna w półtonach. */
    fun midtoneWeight(luma: Float): Float = MIDTONE_FLOOR + MIDTONE_RANGE * MIDTONE_PEAK * luma * (1f - luma)

    private fun smooth(x: Int, y: Int, cell: Int, salt: Int): Float {
        val fx = x.toFloat() / cell
        val fy = y.toFloat() / cell
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = ease(fx - x0)
        val ty = ease(fy - y0)
        val top = lerp(AdjustmentProcessor.noise(x0, y0, salt), AdjustmentProcessor.noise(x0 + 1, y0, salt), tx)
        val bottom =
            lerp(AdjustmentProcessor.noise(x0, y0 + 1, salt), AdjustmentProcessor.noise(x0 + 1, y0 + 1, salt), tx)
        return lerp(top, bottom, ty)
    }

    private fun ease(t: Float): Float = t * t * (SMOOTH_A - SMOOTH_B * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
