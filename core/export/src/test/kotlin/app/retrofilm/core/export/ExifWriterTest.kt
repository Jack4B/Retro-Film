package app.retrofilm.core.export

import android.media.ExifInterface
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExifWriterTest {
    private val data = ExifData(
        make = "Fujifilm",
        model = "X100V",
        lensModel = "23mm F2",
        dateTimeOriginal = "2026:11:04 12:30:00",
        exposureTime = 1 / 250.0,
        fNumber = 5.6,
        iso = 400,
        focalLength = 23.0
    )

    private fun jpeg(): ByteArray {
        val px = IntArray(16 * 16) { 0xFF808080.toInt() }
        val tiff = ExifWriter.write(data, 16, 16, "Retro Film")
        return JpegEncoder.encode(16, 16, 90, IccProfiles.srgb(), tiff) { y0, n, out ->
            px.copyInto(out, 0, y0 * 16, (y0 + n) * 16)
        }
    }

    @Test
    fun whitelistedTagsSurvive() {
        val exif = ExifInterface(ByteArrayInputStream(jpeg()))
        assertEquals("Fujifilm", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("X100V", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("2026:11:04 12:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(400, exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0))
        assertEquals(5.6, exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0), 1e-3)
        assertEquals(0.004, exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0), 1e-4)
        assertEquals(1, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertEquals(16, exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0))
    }

    @Test
    fun noGpsNoMakerNote() {
        val bytes = jpeg()
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKER_NOTE))
        assertFalse(JpegMetadata.hasMetadata(JpegMetadata.strip(bytes)))
    }
}
