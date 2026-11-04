package app.retrofilm.core.engine

import kotlin.math.pow

/** Krzywa przenoszenia sRGB (IEC 61966-2-1), wartości 0..1. */
object Srgb {
    private const val LINEAR_KNEE = 0.04045f
    private const val ENCODED_KNEE = 0.0031308f
    private const val SLOPE = 12.92f
    private const val OFFSET = 0.055f
    private const val SCALE = 1.055f
    private const val GAMMA = 2.4f

    fun toLinear(c: Float): Float = if (c <= LINEAR_KNEE) c / SLOPE else ((c + OFFSET) / SCALE).pow(GAMMA)

    fun fromLinear(l: Float): Float {
        val c = l.coerceIn(0f, 1f)
        return if (c <= ENCODED_KNEE) c * SLOPE else SCALE * c.pow(1f / GAMMA) - OFFSET
    }
}
