package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.JpegMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Na urządzeniu: prawdziwy koder JPEG zapisuje ICC dla Display P3, a strip go zostawia. */
@RunWith(AndroidJUnit4::class)
class P3ExportTest {
    @Test
    fun displayP3ExportKeepsIccAndHasNoExif() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888, false, p3)
        bmp.eraseColor(Color.rgb(200, 120, 40))
        val jpeg = JpegExporter.encode(bmp)
        assertTrue(JpegMetadata.hasIcc(jpeg))
        assertFalse(JpegMetadata.hasExif(jpeg))
    }
}
