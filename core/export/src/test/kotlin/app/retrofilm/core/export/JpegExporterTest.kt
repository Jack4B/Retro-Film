package app.retrofilm.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
