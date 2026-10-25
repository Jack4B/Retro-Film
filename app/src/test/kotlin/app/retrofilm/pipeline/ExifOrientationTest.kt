package app.retrofilm.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Czysty JVM: orientacje 1..8 na siatce 3×2 (wartości = indeksy pikseli). */
class ExifOrientationTest {
    // 0 1 2
    // 3 4 5
    private val src = intArrayOf(0, 1, 2, 3, 4, 5)

    private fun run(o: Int) = ExifOrientation.apply(src, 3, 2, o)

    @Test
    fun normalAndUnknownAreCopies() {
        assertArrayEquals(src, run(ExifOrientation.NORMAL))
        assertArrayEquals(src, run(0))
        assertArrayEquals(src, run(9))
    }

    @Test
    fun rotate90Clockwise() {
        // 3 0
        // 4 1
        // 5 2
        assertArrayEquals(intArrayOf(3, 0, 4, 1, 5, 2), run(ExifOrientation.ROTATE_90))
        assertEquals(2 to 3, ExifOrientation.outputSize(3, 2, ExifOrientation.ROTATE_90))
    }

    @Test
    fun rotate270() = assertArrayEquals(intArrayOf(2, 5, 1, 4, 0, 3), run(ExifOrientation.ROTATE_270))

    @Test
    fun rotate180() = assertArrayEquals(intArrayOf(5, 4, 3, 2, 1, 0), run(ExifOrientation.ROTATE_180))

    @Test
    fun flips() {
        assertArrayEquals(intArrayOf(2, 1, 0, 5, 4, 3), run(ExifOrientation.FLIP_HORIZONTAL))
        assertArrayEquals(intArrayOf(3, 4, 5, 0, 1, 2), run(ExifOrientation.FLIP_VERTICAL))
    }

    @Test
    fun transposeAndTransverse() {
        assertArrayEquals(intArrayOf(0, 3, 1, 4, 2, 5), run(ExifOrientation.TRANSPOSE))
        assertArrayEquals(intArrayOf(5, 2, 4, 1, 3, 0), run(ExifOrientation.TRANSVERSE))
    }
}
