package app.retrofilm.pipeline

/** Kanały R, G, B jako osobne tablice float (kodowanie sRGB w skali 0..255, bez zaokrągleń), wspólny bufor etapów. */
internal class Planes(val r: FloatArray, val g: FloatArray, val b: FloatArray) {
    val size: Int get() = r.size

    companion object {
        fun of(size: Int) = Planes(FloatArray(size), FloatArray(size), FloatArray(size))
    }
}
