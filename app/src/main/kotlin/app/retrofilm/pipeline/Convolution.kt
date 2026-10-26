package app.retrofilm.pipeline

/**
 * Splot rozdzielny jądrem o nieparzystej długości z brzegami przedłużanymi (clamp). We wnętrzu obrazu indeksy nie są
 * przycinane (szybka ścieżka); suma liczona w tej samej kolejności co na brzegach, więc wynik jest identyczny.
 */
internal object Convolution {
    /** Splot każdego wiersza [src] ([w]×[h]) jądrem [kernel] do [dst]. */
    fun rows(src: FloatArray, dst: FloatArray, w: Int, h: Int, kernel: FloatArray) {
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) dst[row + x] = rowTap(src, row, x, w, kernel)
        }
    }

    /** Splot każdej kolumny [src] ([w]×[h]) jądrem [kernel] do [dst]. */
    fun columns(src: FloatArray, dst: FloatArray, w: Int, h: Int, kernel: FloatArray) {
        for (y in 0 until h) {
            for (x in 0 until w) dst[y * w + x] = columnTap(src, x, y, w, h, kernel)
        }
    }

    private fun rowTap(src: FloatArray, row: Int, x: Int, w: Int, kernel: FloatArray): Float {
        val radius = kernel.size / 2
        var acc = 0f
        if (x >= radius && x < w - radius) {
            val from = row + x - radius
            for (k in kernel.indices) acc += src[from + k] * kernel[k]
        } else {
            for (k in -radius..radius) acc += src[row + (x + k).coerceIn(0, w - 1)] * kernel[k + radius]
        }
        return acc
    }

    @Suppress("LongParameterList") // gorąca pętla: współrzędne i wymiary bez alokacji obiektów
    private fun columnTap(src: FloatArray, x: Int, y: Int, w: Int, h: Int, kernel: FloatArray): Float {
        val radius = kernel.size / 2
        var acc = 0f
        if (y >= radius && y < h - radius) {
            val from = (y - radius) * w + x
            for (k in kernel.indices) acc += src[from + k * w] * kernel[k]
        } else {
            for (k in -radius..radius) acc += src[(y + k).coerceIn(0, h - 1) * w + x] * kernel[k + radius]
        }
        return acc
    }
}
