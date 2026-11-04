package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

/** G: [FilmGrain.pixel] (wszystkie warstwy naraz) daje bit w bit to samo co [FilmGrain.luma] i [FilmGrain.chroma]. */
class FilmGrainPixelTest {
    @Test
    fun pixelMatchesSeparateLayersExactly() {
        val out = FloatArray(4)
        for (cell in floatArrayOf(1.3f, 2.7f, 6.1f)) {
            for (y in 0 until 4096 step 37) {
                for (x in 0 until 3072 step 41) check(x, y, cell, out)
            }
        }
    }

    private fun check(x: Int, y: Int, cell: Float, out: FloatArray) {
        FilmGrain.pixel(x, y, cell, out)
        assertEquals(FilmGrain.luma(x, y, cell).toRawBits(), out[0].toRawBits())
        for (k in 0..2) assertEquals(FilmGrain.chroma(x, y, cell, k).toRawBits(), out[k + 1].toRawBits())
    }
}
