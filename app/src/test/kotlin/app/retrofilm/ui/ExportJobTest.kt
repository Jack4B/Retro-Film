package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.ExifInterface
import app.retrofilm.core.engine.CpuLutEngine
import app.retrofilm.core.export.ExifData
import app.retrofilm.core.export.JpegInspector
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.Geometry
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zapis: pełna rozdzielczość źródła, 4:4:4 domyślnie, EXIF z białej listy (bez GPS). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportJobTest {
    private fun job(maxSide: Int = 0, format: ExportFormat = ExportFormat()) = ExportJob(
        ExportLook(Geometry(), null, 1f, Adjustments()),
        CpuLutEngine(),
        FrameOptions(),
        Typeface.DEFAULT,
        maxSide,
        format
    )

    private fun photo(w: Int, h: Int) = Bitmap.createBitmap(
        IntArray(w * h) { i -> (0xFF shl 24) or ((i % w) * 255 / w shl 16) or ((i / w) * 255 / h shl 8) or 90 },
        w,
        h,
        Bitmap.Config.ARGB_8888
    )

    private fun size(jpeg: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun keepsSourceResolution() = runBlocking {
        val out = job().run(null, photo(3000, 2000))
        assertEquals(3000 to 2000, size(out.jpeg))
        assertEquals(listOf(0x11, 0x11, 0x11), JpegInspector.samplingFactors(out.jpeg))
    }

    @Test
    fun maxSideDownscales() = runBlocking {
        val out = job(maxSide = 1024).run(null, photo(2048, 1536))
        assertEquals(1024 to 768, size(out.jpeg))
    }

    @Test
    fun exifPresentWithoutGps() = runBlocking {
        val exif = ExifData(make = "Fuji", model = "X100", iso = 400)
        val jpeg = job().encode(photo(320, 240), exif)
        assertNotNull(JpegInspector.exifTiff(jpeg))
        val read = ExifInterface(ByteArrayInputStream(jpeg))
        assertEquals("Fuji", read.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("320", read.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
        assertNull(read.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test
    fun exifCanBeDisabled() = runBlocking {
        val jpeg = job(format = ExportFormat(keepExif = false)).encode(photo(64, 48), ExifData(make = "Fuji"))
        assertNull(JpegInspector.exifTiff(jpeg))
    }
}
