package app.retrofilm.pipeline

import kotlin.math.cos
import kotlin.math.sin

/**
 * Tonowanie cieni i świateł (split toning, „klisza”) w OKLab: barwa [shadowHue]/[highlightHue] (stopnie koła
 * barw HSV, jak na suwaku) i siła 0..1 dla ciemnych i jasnych partii, [balance] −1..1 przesuwa granicę.
 * Dodaje chromę w kierunku wybranej barwy bez zmiany jasności L (więc nie zmienia ekspozycji); waga cieni i
 * świateł to kwadrat odległości jasności od granicy. Kolory poza gamutem sRGB wracają przez zmniejszenie chromy.
 */
@Suppress("MagicNumber") // numery sektorów koła barw (0..5)
class SplitToning(
    shadowHue: Float,
    shadowAmount: Float,
    highlightHue: Float,
    highlightAmount: Float,
    private val balance: Float
) {
    private val ok = OkLab()
    private val pure = FloatArray(3)
    private val shadow = tint(shadowHue, shadowAmount)
    private val highlight = tint(highlightHue, highlightAmount)

    /** Dodaje tonowanie do [rgb] (kodowanie 0..1), [luma] to jasność piksela (gamma). */
    fun apply(rgb: FloatArray, luma: Float) {
        val m = (luma + balance * BALANCE_SPAN).coerceIn(0f, 1f)
        val ws = (1f - m) * (1f - m)
        val wh = m * m
        ok.fromRgb(rgb)
        val a = ok.lab[1] + ws * shadow[0] + wh * highlight[0]
        val b = ok.lab[2] + ws * shadow[1] + wh * highlight[1]
        ok.toRgb(ok.lab[0], a, b, rgb)
    }

    /** Przesunięcie (a, b) w OKLab o kierunku czystej barwy [hue] i długości [amount]·[MAX_CHROMA]. */
    private fun tint(hue: Float, amount: Float): FloatArray {
        if (amount <= 0f) return floatArrayOf(0f, 0f)
        pureColor(hue, pure)
        val probe = OkLab()
        probe.fromRgb(pure)
        val deg = OkLab.hueDegrees(probe.lab[1], probe.lab[2])
        val rad = Math.toRadians(deg.toDouble())
        return floatArrayOf((cos(rad) * amount * MAX_CHROMA).toFloat(), (sin(rad) * amount * MAX_CHROMA).toFloat())
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
        const val MAX_CHROMA = 0.06f
        const val BALANCE_SPAN = 0.25f
    }
}
