package app.retrofilm.pipeline

/**
 * Dithering trójkątny (TPDF) o amplitudzie ±0,5 poziomu przy końcowej kwantyzacji do 8 bitów: rozbija
 * pasmowanie (banding) w gładkich gradientach po mocnych korektach. Ten sam szum dla wszystkich kanałów,
 * więc nie wprowadza barwnego ziarna; deterministyczny, więc podgląd i zapis są identyczne.
 */
internal object Dither {
    private const val SALT_A = 11
    private const val SALT_B = 12
    private const val SCALE = 0.25f / 255f

    /** Przesunięcie (w jednostkach 0..1) dla piksela ([x], [y]). */
    fun at(x: Int, y: Int): Float =
        (AdjustmentProcessor.noise(x, y, SALT_A) + AdjustmentProcessor.noise(x, y, SALT_B)) * SCALE
}
