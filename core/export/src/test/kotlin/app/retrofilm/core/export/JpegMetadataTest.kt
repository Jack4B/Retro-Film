package app.retrofilm.core.export

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Czysty JVM: JPEG z dopisanym EXIF+GPS → po strip() brak APP1/EXIF/GPS, dane obrazu nietknięte. */
class JpegMetadataTest {
    @Test
    fun stripRemovesExifAndGps() {
        val withExif = insertAfterSoi(plainJpeg(), exifSegmentWithGps())
        assertTrue(JpegMetadata.hasExif(withExif))
        assertTrue(containsAscii(withExif, "GPS"))

        val stripped = JpegMetadata.strip(withExif)

        assertFalse(JpegMetadata.hasExif(stripped))
        assertFalse(JpegMetadata.hasMetadata(stripped))
        assertFalse(containsAscii(stripped, "Exif"))
        assertFalse(containsAscii(stripped, "GPS"))
        assertNotNull("Plik dalej musi się dekodować", ImageIO.read(stripped.inputStream()))
    }

    @Test
    fun stripKeepsCleanJpegIntact() {
        val clean = plainJpeg()
        assertArrayEquals(JpegMetadata.strip(clean), JpegMetadata.strip(JpegMetadata.strip(clean)))
        assertFalse(JpegMetadata.hasExif(JpegMetadata.strip(clean)))
    }

    private fun plainJpeg(): ByteArray {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 16) img.setRGB(x, y, (x * 16 shl 16) or (y * 16 shl 8))
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    /** Minimalny APP1 Exif (TIFF LE) z IFD0 → GPS IFD z GPSLatitudeRef = "N" i markerem tekstowym. */
    private fun exifSegmentWithGps(): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write("Exif".toByteArray())
            write(0)
            write(0)
            write(byteArrayOf(0x49, 0x49, 0x2A, 0, 8, 0, 0, 0)) // TIFF LE, IFD0 @8
            write(byteArrayOf(1, 0)) // 1 wpis
            write(byteArrayOf(0x25, 0x88.toByte(), 4, 0, 1, 0, 0, 0, 26, 0, 0, 0)) // GPSInfo IFD @26
            write(byteArrayOf(0, 0, 0, 0))
            write(byteArrayOf(1, 0)) // GPS IFD: 1 wpis
            write(byteArrayOf(1, 0, 2, 0, 2, 0, 0, 0, 'N'.code.toByte(), 0, 0, 0)) // GPSLatitudeRef
            write(byteArrayOf(0, 0, 0, 0))
            write("GPS-TEST".toByteArray())
        }.toByteArray()
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
    }

    private fun insertAfterSoi(jpeg: ByteArray, segment: ByteArray) =
        jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)

    private fun containsAscii(a: ByteArray, s: String) = String(a, Charsets.ISO_8859_1).contains(s)
}
