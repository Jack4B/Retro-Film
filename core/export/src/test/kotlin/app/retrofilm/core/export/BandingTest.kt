package app.retrofilm.core.export

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Metryka pasmowania: płynny, ciemny gradient (0..48 na 1536 px – ok. 32 px na poziom) po kodowaniu JPEG
 * q95 4:4:4 nie może mieć skoków większych niż 2 poziomy między sąsiednimi pikselami ani odwróceń trendu
 * większych niż 2 (pierścienie/bloki). Mierzy też liczbę unikalnych poziomów – nie może spaść poniżej wejścia.
 */
class BandingTest {
    private val w = 1536
    private val h = 16

    @Test
    fun darkGradientHasNoBands() {
        val px = IntArray(w * h) { i ->
            val v = (i % w) * 48 / (w - 1)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val jpeg = JpegEncoder.encode(w, h, JpegExporter.DEFAULT_QUALITY, null, null) { y0, rows, out ->
            px.copyInto(out, 0, y0 * w, (y0 + rows) * w)
        }
        val img = ImageIO.read(ByteArrayInputStream(jpeg))
        val y = h / 2
        val row = IntArray(w) { img.getRGB(it, y) and 0xFF }
        val maxStep = (1 until w).maxOf { abs(row[it] - row[it - 1]) }
        val maxDeviation = (0 until w).maxOf { abs(row[it] - (it * 48 / (w - 1))) }
        val levels = row.toSet().size
        assertTrue("skok $maxStep", maxStep <= 2)
        assertTrue("odchyłka $maxDeviation", maxDeviation <= 2)
        assertTrue("poziomy $levels", levels >= 48)
    }
}
