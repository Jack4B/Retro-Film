package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Korekty HSL dla 8 zakresów barw: odcień, nasycenie i jasność każdego w zakresie −1..1. */
data class HslAdjust(val values: List<Float> = List(SIZE) { 0f }) {
    val isNeutral: Boolean get() = values.all { it == 0f }

    fun get(band: Int, channel: Int): Float = values[band * CHANNELS + channel]

    fun with(band: Int, channel: Int, value: Float): HslAdjust =
        HslAdjust(values.toMutableList().also { it[band * CHANNELS + channel] = value })

    companion object {
        const val BANDS = 8
        const val CHANNELS = 3
        const val HUE = 0
        const val SATURATION = 1
        const val LIGHTNESS = 2
        const val SIZE = BANDS * CHANNELS

        /** Środki zakresów (OKLCH): czerwony, pomarańcz, żółty, zielony, turkus, niebieski, fiolet, purpura. */
        val BAND_HUES = intArrayOf(29, 55, 110, 142, 195, 264, 305, 340)
    }
}

/**
 * Mikser HSL w przestrzeni OKLCH (percepcyjnie jednolitej): odcień, nasycenie i jasność dla 8 zakresów barw.
 * Wpływ zakresu spada płynnie (cos²) z odległością kąta barwy OKLab od jego środka, więc zakresy mają podobną
 * szerokość postrzeganą, a zmiana „jasności” zmienia L bez przesunięcia odcienia. Szare piksele nie są ruszane,
 * a barwy o małej chromie dostają wpływ proporcjonalny. Wagi liczone raz do tablic po 360 stopni; kolory poza
 * gamutem sRGB wracają przez zmniejszenie chromy ([OkLab.toRgb]). Pracuje na wartościach kodowanych 0..1.
 */
@Suppress("MagicNumber")
class HslMixer(adj: HslAdjust) {
    private val hueShift = FloatArray(DEGREES)
    private val satScale = FloatArray(DEGREES)
    private val lumScale = FloatArray(DEGREES)
    private val ok = OkLab()

    init {
        for (h in 0 until DEGREES) {
            var sum = 0f
            val w = FloatArray(HslAdjust.BANDS) { b ->
                val d = circular(h, HslAdjust.BAND_HUES[b])
                if (d >= HALF_WIDTH) 0f else 0.5f * (1f + cos(PI * d / HALF_WIDTH).toFloat())
            }
            w.forEach { sum += it }
            val norm = max(sum, 1f)
            for (b in 0 until HslAdjust.BANDS) {
                val k = w[b] / norm
                hueShift[h] += k * adj.get(b, HslAdjust.HUE) * HUE_RANGE
                satScale[h] += k * adj.get(b, HslAdjust.SATURATION)
                lumScale[h] += k * adj.get(b, HslAdjust.LIGHTNESS)
            }
        }
    }

    /** Zmienia [rgb] (R, G, B w 0..1) w miejscu. */
    fun apply(rgb: FloatArray) {
        ok.fromRgb(rgb)
        val l = ok.lab[0]
        val a = ok.lab[1]
        val b = ok.lab[2]
        val chroma = sqrt(a * a + b * b)
        if (chroma < MIN_CHROMA) return
        val hue = OkLab.hueDegrees(a, b)
        val idx = hue.toInt().coerceIn(0, DEGREES - 1)
        if (hueShift[idx] == 0f && satScale[idx] == 0f && lumScale[idx] == 0f) return
        val strength = minOf(1f, chroma / CHROMA_FULL)
        val h2 = Math.toRadians((hue + hueShift[idx] * strength).toDouble())
        val c2 = chroma * (1f + satScale[idx] * strength).coerceAtLeast(0f)
        val lum = lumScale[idx] * LUM_GAIN * strength
        val l2 = (if (lum >= 0f) l + lum * (1f - l) else l * (1f + lum)).coerceIn(0f, 1f)
        ok.toRgb(l2, c2 * cos(h2).toFloat(), c2 * sin(h2).toFloat(), rgb)
    }

    private fun circular(a: Int, b: Int): Float {
        val d = abs(a - b) % DEGREES
        return minOf(d, DEGREES - d).toFloat()
    }

    private companion object {
        const val DEGREES = 360
        const val HALF_WIDTH = 50f
        const val HUE_RANGE = 30f
        const val LUM_GAIN = 0.5f
        const val CHROMA_FULL = 0.05f
        const val MIN_CHROMA = 1e-4f
    }
}
