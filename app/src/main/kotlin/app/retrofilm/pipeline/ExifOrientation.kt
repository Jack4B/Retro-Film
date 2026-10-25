package app.retrofilm.pipeline

/**
 * Orientacja EXIF (tag 0x0112, wartości 1..8) zastosowana do pikseli. Czysty Kotlin – testowalny na JVM.
 * Piksele to dowolne 32-bitowe wartości w kolejności wierszy; funkcja tylko je przestawia.
 */
object ExifOrientation {
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /** Czy orientacja zamienia szerokość z wysokością (obrót o 90°/270° lub transpozycja). */
    fun swapsAxes(orientation: Int): Boolean = orientation in TRANSPOSE..ROTATE_270

    /** Wymiary (szerokość, wysokość) po zastosowaniu orientacji. */
    fun outputSize(width: Int, height: Int, orientation: Int): Pair<Int, Int> =
        if (swapsAxes(orientation)) height to width else width to height

    /**
     * Zwraca nową tablicę pikseli obrazu wyświetlanego zgodnie z [orientation].
     * Wartości spoza 1..8 traktowane jak [NORMAL] (kopia bez zmian).
     */
    fun apply(pixels: IntArray, width: Int, height: Int, orientation: Int): IntArray {
        require(pixels.size == width * height) { "Rozmiar tablicy pikseli niezgodny z wymiarami" }
        if (orientation !in FLIP_HORIZONTAL..ROTATE_270) return pixels.copyOf()
        val (ow, oh) = outputSize(width, height, orientation)
        val out = IntArray(pixels.size)
        for (y in 0 until oh) {
            for (x in 0 until ow) {
                out[y * ow + x] = pixels[sourceIndex(x, y, width, height, orientation)]
            }
        }
        return out
    }

    /** Indeks piksela źródłowego dla piksela wyjściowego (x, y). */
    private fun sourceIndex(x: Int, y: Int, w: Int, h: Int, orientation: Int): Int {
        val (sx, sy) = when (orientation) {
            FLIP_HORIZONTAL -> (w - 1 - x) to y
            ROTATE_180 -> (w - 1 - x) to (h - 1 - y)
            FLIP_VERTICAL -> x to (h - 1 - y)
            TRANSPOSE -> y to x
            ROTATE_90 -> y to (h - 1 - x)
            TRANSVERSE -> (w - 1 - y) to (h - 1 - x)
            ROTATE_270 -> (w - 1 - y) to x
            else -> x to y
        }
        return sy * w + sx
    }
}
