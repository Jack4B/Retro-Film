package app.retrofilm.core.export

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.log10
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Własny koder JPEG: 4:4:4, profil ICC i EXIF w pliku, wynik czytelny dla niezależnego dekodera (ImageIO). */
class JpegEncoderTest {
    private val w = 67
    private val h = 45
    private val px = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val r = x * 255 / (w - 1)
        val g = y * 255 / (h - 1)
        val b = if ((x / 4 + y / 4) % 2 == 0) 230 else 20
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun encode(q: Int = 95, icc: ByteArray? = null, exif: ByteArray? = null) =
        JpegEncoder.encode(w, h, q, icc, exif) { y0, rows, out -> px.copyInto(out, 0, y0 * w, (y0 + rows) * w) }

    @Test
    fun usesFullChromaResolution() {
        val jpeg = encode()
        assertEquals(listOf(0x11, 0x11, 0x11), JpegInspector.samplingFactors(jpeg))
    }

    @Test
    fun decodesCloseToSource() {
        val img = ImageIO.read(ByteArrayInputStream(encode()))
        assertEquals(w, img.width)
        assertEquals(h, img.height)
        var se = 0.0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = px[y * w + x]
                val b = img.getRGB(x, y)
                for (s in intArrayOf(16, 8, 0)) {
                    val d = ((a shr s) and 0xFF) - ((b shr s) and 0xFF)
                    se += d * d
                }
            }
        }
        val psnr = 20 * log10(255 / sqrt(se / (w * h * 3)))
        assertTrue("PSNR $psnr", psnr > 38)
    }

    @Test
    fun embedsIccAndExif() {
        val icc = IccProfiles.srgb()
        val exif = byteArrayOf(0x4D, 0x4D, 0, 42, 0, 0, 0, 8, 0, 0)
        val jpeg = encode(icc = icc, exif = exif)
        assertTrue(JpegMetadata.hasIcc(jpeg))
        assertTrue(JpegMetadata.hasExif(jpeg))
        assertArrayEquals(icc, JpegInspector.icc(jpeg))
    }

    @Test
    fun lowerQualityIsSmaller() {
        assertTrue(encode(60).size < encode(95).size)
    }
}
