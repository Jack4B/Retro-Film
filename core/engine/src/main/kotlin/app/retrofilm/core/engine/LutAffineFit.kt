package app.retrofilm.core.engine

/**
 * Najlepsze (MNK) przybliżenie LUT przekształceniem afinicznym: out_c = m[c][0]*r + m[c][1]*g + m[c][2]*b + m[c][3],
 * wszystko w skali 0..1. Służy do taniego podglądu (macierz koloru) w UI.
 */
@Suppress("MagicNumber", "NestedBlockDepth")
object LutAffineFit {
    private const val DIM = 4
    private const val MAX_BYTE = 255f

    fun fit(lut: Lut): Array<FloatArray> {
        val ata = Array(DIM) { DoubleArray(DIM) }
        val atb = Array(Lut.CHANNELS) { DoubleArray(DIM) }
        val max = (lut.size - 1).toDouble()
        val x = DoubleArray(DIM)
        for (b in 0 until lut.size) {
            for (g in 0 until lut.size) {
                for (r in 0 until lut.size) {
                    x[0] = r / max
                    x[1] = g / max
                    x[2] = b / max
                    x[3] = 1.0
                    for (i in 0 until DIM) {
                        for (j in 0 until DIM) ata[i][j] += x[i] * x[j]
                        for (c in 0 until Lut.CHANNELS) atb[c][i] += x[i] * lut.node(r, g, b, c) / MAX_BYTE
                    }
                }
            }
        }
        return Array(Lut.CHANNELS) { c -> solve(ata, atb[c]).map { it.toFloat() }.toFloatArray() }
    }

    /** Eliminacja Gaussa z częściowym wyborem elementu głównego. */
    private fun solve(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray {
        val n = b0.size
        val a = Array(n) { a0[it].copyOf() }
        val b = b0.copyOf()
        for (col in 0 until n) {
            val p = (col until n).maxBy { kotlin.math.abs(a[it][col]) }
            a[col] = a[p].also { a[p] = a[col] }
            b[col] = b[p].also { b[p] = b[col] }
            for (row in col + 1 until n) {
                val f = a[row][col] / a[col][col]
                for (k in col until n) a[row][k] -= f * a[col][k]
                b[row] -= f * b[col]
            }
        }
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var s = b[row]
            for (k in row + 1 until n) s -= a[row][k] * x[k]
            x[row] = s / a[row][row]
        }
        return x
    }
}
