package app.retrofilm.pipeline

/**
 * Tonowanie cieni i świateł (split toning, „klisza”): barwa [shadowHue]/[highlightHue] (stopnie) i siła 0..1 dla
 * ciemnych i jasnych partii, [balance] −1..1 przesuwa granicę. Dodaje tylko chromę (bez zmiany jasności):
 * dla każdego kanału różnicę między czystą barwą a jej luminancją, ważoną kwadratem odległości od granicy.
 */
@Suppress("MagicNumber") // numery sektorów koła barw (0..5) i indeksy kanałów RGB
class SplitToning(
    shadowHue: Float,
    shadowAmount: Float,
    highlightHue: Float,
    highlightAmount: Float,
    private val balance: Float
) {
    private val shadow = tint(shadowHue, shadowAmount)
    private val highlight = tint(highlightHue, highlightAmount)

    /** Dodaje tonowanie do [rgb] (kodowanie 0..1), [luma] to jasność piksela. */
    fun apply(rgb: FloatArray, luma: Float) {
        val m = (luma + balance * BALANCE_SPAN).coerceIn(0f, 1f)
        val ws = (1f - m) * (1f - m)
        val wh = m * m
        for (k in 0..2) rgb[k] += ws * shadow[k] + wh * highlight[k]
    }

    private fun tint(hue: Float, amount: Float): FloatArray {
        val c = FloatArray(3)
        pureColor(hue, c)
        val y = LUMA_R * c[0] + LUMA_G * c[1] + LUMA_B * c[2]
        return FloatArray(3) { (c[it] - y) * amount * GAIN }
    }

    private fun pureColor(hue: Float, out: FloatArray) {
        val hh = ((hue % FULL) + FULL) % FULL / SECTOR
        val i = hh.toInt()
        val f = hh - i
        val up = f
        val down = 1f - f
        val (r, g, b) = when (i) {
            0 -> Triple(1f, up, 0f)
            1 -> Triple(down, 1f, 0f)
            2 -> Triple(0f, 1f, up)
            3 -> Triple(0f, down, 1f)
            4 -> Triple(up, 0f, 1f)
            else -> Triple(1f, 0f, down)
        }
        out[0] = r
        out[1] = g
        out[2] = b
    }

    private companion object {
        const val FULL = 360f
        const val SECTOR = 60f
        const val GAIN = 0.35f
        const val BALANCE_SPAN = 0.25f
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}
