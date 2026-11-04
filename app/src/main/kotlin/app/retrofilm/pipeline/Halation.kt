package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.pow

/**
 * Halacja (I, audyt efektów): światło przechodzące przez emulsję odbija się od podłoża filmu i wraca do warstwy
 * czułej na czerwień (potem zieleń), więc wokół jasnych źródeł powstaje ciepła, czerwono-pomarańczowa poświata.
 * Model: energia świateł w świetle liniowym (powyżej [THRESHOLD]) rozmyta szeroko w niskiej rozdzielczości
 * (dwa przebiegi pudełkowe ≈ gauss, promień stały względem kadru, więc wynik nie zależy od rozdzielczości),
 * dodana w świetle liniowym z odcieniem [TINT] metodą „screen” (nie przepala bieli).
 */
internal object Halation {
    const val TARGET = 256
    private const val THRESHOLD = 0.55f
    private const val RADIUS_DIV = 28
    private const val GAIN = 0.9f
    private const val GAMMA = 2.2f
    private val TINT = floatArrayOf(1f, 0.32f, 0.08f)

    class Map(private val low: Lowres, private val glow: FloatArray) {
        fun at(x: Int, y: Int): Float = low.sample(glow, x, y)
    }

    /** Energia światła dla jasności 0..1 w kodowaniu gamma. */
    fun energy(lumaUnit: Float): Float {
        val lin = lumaUnit.coerceIn(0f, 1f).pow(GAMMA)
        return max(0f, (lin - THRESHOLD) / (1f - THRESHOLD))
    }

    fun build(low: Lowres, energy: FloatArray): Map {
        val r = max(1, max(low.w, low.h) / RADIUS_DIV)
        val tmp = FloatArray(energy.size)
        val a = FloatArray(energy.size)
        DetailFilter.blurRows(energy, tmp, low.w, low.h, r)
        DetailFilter.blurCols(tmp, a, low.w, low.h, r)
        DetailFilter.blurRows(a, tmp, low.w, low.h, r)
        DetailFilter.blurCols(tmp, a, low.w, low.h, r)
        return Map(low, a)
    }

    /** Dodaje poświatę [glow] o sile [strength] do [rgb] (0..1, gamma). */
    fun apply(rgb: FloatArray, glow: Float, strength: Float) {
        val g = glow * strength * GAIN
        if (g <= MIN_GLOW) return
        for (k in 0..2) {
            val lin = rgb[k].coerceIn(0f, 1f).pow(GAMMA)
            val add = (g * TINT[k]).coerceAtMost(1f)
            rgb[k] = (1f - (1f - lin) * (1f - add)).pow(1f / GAMMA)
        }
    }

    private const val MIN_GLOW = 1e-4f
}
