package app.retrofilm.core.export

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Png16WriterTest {
    @Test
    fun writesSixteenBitRgbThatDecodesExactly() {
        val w = 37
        val h = 11
        val png = Png16Writer.encode(w, h, null) { y, out ->
            for (x in 0 until w) {
                out[3 * x] = x * 1771
                out[3 * x + 1] = y * 5000 + 1
                out[3 * x + 2] = 65535 - x * 3
            }
        }
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(w, img.width)
        assertEquals(h, img.height)
        assertEquals(16, img.colorModel.getComponentSize(0))
        val px = IntArray(3)
        img.raster.getPixel(36, 10, px)
        assertEquals(36 * 1771, px[0])
        assertEquals(50001, px[1])
        assertEquals(65535 - 108, px[2])
    }

    @Test
    fun embedsIccProfile() {
        val png = Png16Writer.encode(2, 2, IccProfiles.displayP3(), "Display P3") { _, out -> out.fill(1000) }
        val text = String(png, Charsets.ISO_8859_1)
        assertTrue(text.contains("iCCPDisplay P3"))
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(2, img.width)
    }
}
