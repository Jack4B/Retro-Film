package app.retrofilm.core.engine

/**
 * Referencyjna implementacja CPU (interpolacja trójliniowa) na pikselach ARGB (niepremnożonych).
 * Czysty Kotlin – testowalna w JVM bez Androida.
 */
object CpuLut {
    private const val MAX_BYTE = 255f
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val ALPHA_MASK = -0x1000000

    fun applyToPixels(pixels: IntArray, lut: Lut, intensity: Float): IntArray {
        val t = intensity.coerceIn(0f, 1f)
        val out = IntArray(pixels.size)
        val rgb = FloatArray(Lut.CHANNELS)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr SHIFT_R and BYTE_MASK) / MAX_BYTE
            val g = (p shr SHIFT_G and BYTE_MASK) / MAX_BYTE
            val b = (p and BYTE_MASK) / MAX_BYTE
            sample(lut, r, g, b, rgb)
            val ro = toByte(r + (rgb[0] - r) * t)
            val go = toByte(g + (rgb[1] - g) * t)
            val bo = toByte(b + (rgb[2] - b) * t)
            out[i] = (p and ALPHA_MASK) or (ro shl SHIFT_R) or (go shl SHIFT_G) or bo
        }
        return out
    }

    /** Próbkuje LUT trójliniowo; wejście i wynik w zakresie 0..1. */
    fun sample(lut: Lut, r: Float, g: Float, b: Float, out: FloatArray) {
        val max = lut.size - 1
        val pr = r.coerceIn(0f, 1f) * max
        val pg = g.coerceIn(0f, 1f) * max
        val pb = b.coerceIn(0f, 1f) * max
        val r0 = pr.toInt().coerceAtMost(max)
        val g0 = pg.toInt().coerceAtMost(max)
        val b0 = pb.toInt().coerceAtMost(max)
        val r1 = (r0 + 1).coerceAtMost(max)
        val g1 = (g0 + 1).coerceAtMost(max)
        val b1 = (b0 + 1).coerceAtMost(max)
        val fr = pr - r0
        val fg = pg - g0
        val fb = pb - b0
        for (c in 0 until Lut.CHANNELS) {
            val c00 = lerp(lut.node(r0, g0, b0, c), lut.node(r1, g0, b0, c), fr)
            val c10 = lerp(lut.node(r0, g1, b0, c), lut.node(r1, g1, b0, c), fr)
            val c01 = lerp(lut.node(r0, g0, b1, c), lut.node(r1, g0, b1, c), fr)
            val c11 = lerp(lut.node(r0, g1, b1, c), lut.node(r1, g1, b1, c), fr)
            val c0 = c00 + (c10 - c00) * fg
            val c1 = c01 + (c11 - c01) * fg
            out[c] = (c0 + (c1 - c0) * fb) / MAX_BYTE
        }
    }

    private fun lerp(a: Int, b: Int, f: Float): Float = a + (b - a) * f

    private fun toByte(v: Float): Int = Math.round(v.coerceIn(0f, 1f) * MAX_BYTE)
}
