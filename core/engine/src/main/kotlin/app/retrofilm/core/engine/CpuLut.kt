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

    fun applyToPixels(
        pixels: IntArray,
        lut: Lut,
        intensity: Float,
        interpolation: Interpolation = Interpolation.TRILINEAR
    ): IntArray {
        val t = intensity.coerceIn(0f, 1f)
        val out = IntArray(pixels.size)
        val rgb = FloatArray(Lut.CHANNELS)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr SHIFT_R and BYTE_MASK) / MAX_BYTE
            val g = (p shr SHIFT_G and BYTE_MASK) / MAX_BYTE
            val b = (p and BYTE_MASK) / MAX_BYTE
            sample(lut, r, g, b, rgb, interpolation)
            val ro = toByte(r + (rgb[0] - r) * t)
            val go = toByte(g + (rgb[1] - g) * t)
            val bo = toByte(b + (rgb[2] - b) * t)
            out[i] = (p and ALPHA_MASK) or (ro shl SHIFT_R) or (go shl SHIFT_G) or bo
        }
        return out
    }

    /** Próbkuje LUT wybraną interpolacją; wejście i wynik w zakresie 0..1. */
    @Suppress("LongParameterList")
    fun sample(
        lut: Lut,
        r: Float,
        g: Float,
        b: Float,
        out: FloatArray,
        interpolation: Interpolation = Interpolation.TRILINEAR
    ) {
        if (interpolation == Interpolation.TETRAHEDRAL) {
            sampleTetrahedral(lut, r, g, b, out)
            return
        }
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
            val c00 = lerp(lut.nodeF(r0, g0, b0, c), lut.nodeF(r1, g0, b0, c), fr)
            val c10 = lerp(lut.nodeF(r0, g1, b0, c), lut.nodeF(r1, g1, b0, c), fr)
            val c01 = lerp(lut.nodeF(r0, g0, b1, c), lut.nodeF(r1, g0, b1, c), fr)
            val c11 = lerp(lut.nodeF(r0, g1, b1, c), lut.nodeF(r1, g1, b1, c), fr)
            val c0 = c00 + (c10 - c00) * fg
            val c1 = c01 + (c11 - c01) * fg
            out[c] = (c0 + (c1 - c0) * fb) / MAX_BYTE
        }
    }

    /** Interpolacja czworościenna; wejście i wynik w zakresie 0..1. */
    @Suppress("LongMethod")
    fun sampleTetrahedral(lut: Lut, r: Float, g: Float, b: Float, out: FloatArray) {
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
            val c000 = lut.nodeF(r0, g0, b0, c)
            val c111 = lut.nodeF(r1, g1, b1, c)
            val v = when {
                fr >= fg && fg >= fb -> {
                    val c100 = lut.nodeF(r1, g0, b0, c)
                    val c110 = lut.nodeF(r1, g1, b0, c)
                    c000 + fr * (c100 - c000) + fg * (c110 - c100) + fb * (c111 - c110)
                }

                fr >= fb && fb >= fg -> {
                    val c100 = lut.nodeF(r1, g0, b0, c)
                    val c101 = lut.nodeF(r1, g0, b1, c)
                    c000 + fr * (c100 - c000) + fb * (c101 - c100) + fg * (c111 - c101)
                }

                fb >= fr && fr >= fg -> {
                    val c001 = lut.nodeF(r0, g0, b1, c)
                    val c101 = lut.nodeF(r1, g0, b1, c)
                    c000 + fb * (c001 - c000) + fr * (c101 - c001) + fg * (c111 - c101)
                }

                fg >= fr && fr >= fb -> {
                    val c010 = lut.nodeF(r0, g1, b0, c)
                    val c110 = lut.nodeF(r1, g1, b0, c)
                    c000 + fg * (c010 - c000) + fr * (c110 - c010) + fb * (c111 - c110)
                }

                fg >= fb && fb >= fr -> {
                    val c010 = lut.nodeF(r0, g1, b0, c)
                    val c011 = lut.nodeF(r0, g1, b1, c)
                    c000 + fg * (c010 - c000) + fb * (c011 - c010) + fr * (c111 - c011)
                }

                else -> {
                    val c001 = lut.nodeF(r0, g0, b1, c)
                    val c011 = lut.nodeF(r0, g1, b1, c)
                    c000 + fb * (c001 - c000) + fg * (c011 - c001) + fr * (c111 - c011)
                }
            }
            out[c] = v / MAX_BYTE
        }
    }

    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    private fun toByte(v: Float): Int = Math.round(v.coerceIn(0f, 1f) * MAX_BYTE)
}
