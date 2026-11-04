package app.retrofilm.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraHdrJpegTest {
    private fun seg(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + payload
    }

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    private val exif = seg(0xE1, "Exif\u0000\u0000abc".toByteArray(Charsets.ISO_8859_1))
    private val xmp = seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<hdrgm:Version/>".toByteArray(Charsets.ISO_8859_1))
    private val mpf = seg(0xE2, "MPF\u0000xyz".toByteArray(Charsets.ISO_8859_1))
    private val icc = seg(0xE2, "ICC_PROFILE\u0000p".toByteArray(Charsets.ISO_8859_1))
    private val scan =
        seg(0xDA, byteArrayOf(1, 2, 3)) + byteArrayOf(5, 0xFF.toByte(), 0, 7, 0xFF.toByte(), 0xD3.toByte(), 9)

    private fun primary(vararg app: ByteArray) = app.fold(soi) { a, b -> a + b } + scan + eoi

    @Test
    fun stripsGainMapAndTrailingData() {
        val second = soi + seg(0xE0, byteArrayOf(1)) + scan + eoi
        val full = primary(exif, xmp, mpf, icc) + second + ByteArray(100)
        assertTrue(UltraHdrJpeg.hasGainMap(full))
        val sdr = UltraHdrJpeg.sdrOnly(full)
        assertArrayEquals(primary(exif, icc), sdr)
        assertFalse(UltraHdrJpeg.hasGainMap(sdr))
    }

    @Test
    fun plainJpegUntouched() {
        val plain = primary(exif, icc)
        assertFalse(UltraHdrJpeg.hasGainMap(plain))
        assertEquals(plain, UltraHdrJpeg.sdrOnly(plain))
    }
}
