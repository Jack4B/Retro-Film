package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

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

        /** Środki zakresów (stopnie): czerwony, pomarańczowy, żółty, zielony, turkusowy, niebieski, fiolet, purpura. */
        val BAND_HUES = intArrayOf(0, 30, 60, 120, 180, 240, 280, 320)
    }
}

/**
 * Mikser HSL jak w programach do RAW: wpływ zakresu spada płynnie (cos²) z odległością od jego środka na kole
 * barw, szare piksele (bez nasycenia) nie są ruszane. Wagi liczone raz do tablic po 360 stopni, więc
 * koszt na piksel to konwersja RGB↔HSV i trzy odczyty tablic. Pracuje na wartościach kodowanych 0..1.
 */
@Suppress("MagicNumber") // numery sektorów koła barw HSV (0..5) i indeksy kanałów
class HslMixer(adj: HslAdjust) {
    private val hueShift = FloatArray(DEGREES)
    private val satScale = FloatArray(DEGREES)
    private val lumScale = FloatArray(DEGREES)

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
        val mx = maxOf(rgb[0], rgb[1], rgb[2])
        val mn = minOf(rgb[0], rgb[1], rgb[2])
        val delta = mx - mn
        if (delta < EPS) return
        val s = delta / mx
        val hue = hueOf(rgb, mx, delta)
        val idx = hue.toInt().coerceIn(0, DEGREES - 1)
        val strength = minOf(1f, s * CHROMA_RAMP)
        val h2 = hue + hueShift[idx] * strength
        val s2 = (s * (1f + satScale[idx] * strength)).coerceIn(0f, 1f)
        val v2 = (mx * (1f + lumScale[idx] * LUM_GAIN * strength)).coerceIn(0f, 1f)
        hsvToRgb(h2, s2, v2, rgb)
    }

    private fun hueOf(rgb: FloatArray, mx: Float, delta: Float): Float {
        val sector = when (mx) {
            rgb[0] -> (rgb[1] - rgb[2]) / delta
            rgb[1] -> 2f + (rgb[2] - rgb[0]) / delta
            else -> 4f + (rgb[0] - rgb[1]) / delta
        }
        val h = sector * SECTOR
        return if (h < 0f) h + DEGREES else h
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
        val hh = ((h % DEGREES) + DEGREES) % DEGREES / SECTOR
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        when (i) {
            0 -> set(out, v, t, p)
            1 -> set(out, q, v, p)
            2 -> set(out, p, v, t)
            3 -> set(out, p, q, v)
            4 -> set(out, t, p, v)
            else -> set(out, v, p, q)
        }
    }

    private fun set(out: FloatArray, r: Float, g: Float, b: Float) {
        out[0] = r
        out[1] = g
        out[2] = b
    }

    private fun circular(a: Int, b: Int): Float {
        val d = abs(a - b) % DEGREES
        return minOf(d, DEGREES - d).toFloat()
    }

    private companion object {
        const val DEGREES = 360
        const val SECTOR = 60f
        const val HALF_WIDTH = 45f
        const val HUE_RANGE = 30f
        const val LUM_GAIN = 0.5f
        const val CHROMA_RAMP = 3f
        const val EPS = 1e-6f
    }
}
