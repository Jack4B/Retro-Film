package app.retrofilm.pipeline

import kotlin.math.ceil
import kotlin.math.max

/**
 * Lokalna jasność obrazu (rozmyta siatka niskiej rozdzielczości) do cieni i świateł: wpływ zależy od jasności
 * otoczenia, nie samego piksela, więc detale w cieniu nie spłaszczają się, a krawędzie nie dostają aureoli
 * (zasada znana z narzędzi „Shadow/Highlight”). Siatka ma ok. [GRID_LONG] komórek po dłuższym boku.
 */
class ToneMask private constructor(
    private val gridW: Int,
    private val gridH: Int,
    private val cell: Int,
    private val data: FloatArray
) {
    /** Lokalna jasność (0..1, kodowanie gamma) w punkcie ([x], [y]) obrazu, interpolacja dwuliniowa. */
    fun sample(x: Int, y: Int): Float {
        val fx = ((x + HALF) / cell - HALF).coerceIn(0f, (gridW - 1).toFloat())
        val fy = ((y + HALF) / cell - HALF).coerceIn(0f, (gridH - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, gridW - 1)
        val y1 = minOf(y0 + 1, gridH - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = data[y0 * gridW + x0] * (1 - tx) + data[y0 * gridW + x1] * tx
        val bottom = data[y1 * gridW + x0] * (1 - tx) + data[y1 * gridW + x1] * tx
        return (top * (1 - ty) + bottom * ty) / BYTE_MAX
    }

    /** Zbiera jasność z kolejnych porcji pikseli, potem [build] rozmywa siatkę. */
    class Builder(private val width: Int, private val height: Int) {
        private val cell = max(1, max(width, height) / GRID_LONG)
        private val gridW = ceil(width / cell.toFloat()).toInt()
        private val gridH = ceil(height / cell.toFloat()).toInt()
        private val sums = FloatArray(gridW * gridH)
        private val counts = IntArray(gridW * gridH)

        fun add(px: IntArray, count: Int, rowWidth: Int, firstRow: Int) {
            for (i in 0 until count) {
                val c = px[i]
                val luma = LUMA_R * ((c shr RED_SHIFT) and MASK) + LUMA_G * ((c shr GREEN_SHIFT) and MASK) +
                    LUMA_B * (c and MASK)
                val idx = ((firstRow + i / rowWidth) / cell) * gridW + (i % rowWidth) / cell
                sums[idx] += luma
                counts[idx]++
            }
        }

        fun build(): ToneMask {
            val avg = FloatArray(sums.size) { if (counts[it] > 0) sums[it] / counts[it] else 0f }
            val blurred = DetailFilter.boxBlur(avg, gridW, gridH, max(2, GRID_LONG / BLUR_DIV))
            return ToneMask(gridW, gridH, cell, blurred)
        }
    }

    private companion object {
        const val HALF = 0.5f
        const val BYTE_MAX = 255f
        const val GRID_LONG = 512
        const val BLUR_DIV = 40
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val MASK = 0xFF
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}
