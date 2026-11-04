package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Przybliżona krzywa tonalna telefonu w kodowaniu sRGB: S(v) = v − a·sin(2πv)/(2π) (stromsze półtony, łagodniejsze
 * końce; punkty 0, ½ i 1 stałe, monotoniczna dla a < 1). [invert] odwraca ją metodą Newtona, żeby film dostał
 * bardziej „płaskie” wejście; siła 0..1 skaluje a do [MAX_A]. Szarość 18% prawie się nie zmienia.
 */
object PhoneCurve {
    const val MAX_A = 0.3f
    const val DEFAULT_STRENGTH = 0.5f
    private const val ITERATIONS = 5
    private val TWO_PI = (2 * PI).toFloat()

    fun apply(v: Float, strength: Float): Float {
        val a = MAX_A * strength
        return v - a * sin(TWO_PI * v) / TWO_PI
    }

    fun invert(v: Float, strength: Float): Float {
        if (strength <= 0f) return v
        val a = MAX_A * strength.coerceAtMost(1f)
        var x = v
        repeat(ITERATIONS) {
            val f = x - a * sin(TWO_PI * x) / TWO_PI - v
            x -= f / (1f - a * cos(TWO_PI * x))
        }
        return x.coerceIn(0f, 1f)
    }
}
