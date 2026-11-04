package app.retrofilm.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import app.retrofilm.core.frame.SignatureFitStatus
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric (natywna grafika): eksport ponownie koduje bitmapę i nie zapisuje EXIF/GPS. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegExporterTest {
    @Test
    fun exportedJpegHasNoExifOrGps() {
        val bmp = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        val jpeg = JpegExporter.encode(bmp, 90)

        assertEquals(0xFF, jpeg[0].toInt() and 0xFF)
        assertEquals(0xD8, jpeg[1].toInt() and 0xFF)
        assertFalse("APP1 Exif w eksporcie", JpegMetadata.hasExif(jpeg))
        assertFalse("Metadane APPn/COM w eksporcie", JpegMetadata.hasMetadata(jpeg))
        assertFalse(String(jpeg, Charsets.ISO_8859_1).contains("GPS"))
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertNotNull(decoded)
        assertEquals(32, decoded.width)
        assertEquals(24, decoded.height)
    }

    @Test(expected = SignatureTooLongException::class)
    fun exportIsBlockedWhenSignatureTooLong() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        JpegExporter.encode(bmp, 90, SignatureFitStatus.TooLong)
    }

    @Test
    fun blockedExportWritesNothing() {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        runCatching { JpegExporter.export(bmp, out, 90, SignatureFitStatus.TooLong) }
        assertEquals(0, out.size())
        assertTrue(JpegExporter.encode(bmp, 90, SignatureFitStatus.Fits).isNotEmpty())
    }

    @Test
    fun defaultIs444WithSrgbProfile() {
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 30, 30)) }
        val jpeg = JpegExporter.encode(bmp)
        assertEquals(listOf(0x11, 0x11, 0x11), JpegInspector.samplingFactors(jpeg))
        assertTrue(IccProfiles.description(JpegInspector.icc(jpeg)!!)!!.startsWith("sRGB"))
        assertEquals(40, BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).width)
    }

    @Test
    fun option420KeepsProfileAndExif() {
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 130, 30)) }
        val tiff = ExifWriter.write(ExifData(make = "Test"), 40, 30, "Retro Film")
        val jpeg = JpegExporter.encode(bmp, JpegOptions(90, ChromaSubsampling.Half420, tiff), SignatureFitStatus.None)
        assertEquals(0x22, JpegInspector.samplingFactors(jpeg)[0])
        assertTrue(JpegMetadata.hasIcc(jpeg))
        assertTrue(JpegMetadata.hasExif(jpeg))
        assertNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
    }
}
