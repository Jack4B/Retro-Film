package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Typeface
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.JpegMetadata
import app.retrofilm.core.export.SignatureTooLongException
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.engine.AppGraph
import app.retrofilm.ui.MainState
import app.retrofilm.ui.UiMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric (natywna grafika): wczytanie z EXIF → ramka → eksport, sprawdzane na pikselach. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PipelineTest {
    private val w = 40
    private val h = 20

    /** Lewa połowa czerwona, prawa niebieska; JPEG z APP1 EXIF Orientation = [orientation]. */
    private fun jpegWithOrientation(orientation: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) bmp.setPixel(x, y, if (x < w / 2) Color.RED else Color.BLUE)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        val jpeg = out.toByteArray()
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8, // MM, IFD0 @8
            0, 1, // 1 wpis
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // Orientation SHORT
            0, 0, 0, 0 // brak kolejnego IFD
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val len = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun isRed(c: Int) = Color.red(c) > 200 && Color.blue(c) < 60
    private fun isBlue(c: Int) = Color.blue(c) > 200 && Color.red(c) < 60

    @Test
    fun orientation6IsAppliedToPixelsThroughLoadAndExport() {
        val src = jpegWithOrientation(ExifOrientation.ROTATE_90)
        assertEquals(ExifOrientation.ROTATE_90, PhotoLoader.readOrientation { ByteArrayInputStream(src) })

        val photo = PhotoLoader.load({ ByteArrayInputStream(src) })
        val rendered = FrameRenderer.render(photo, FrameOptions(), Typeface.DEFAULT)
        val jpeg = ExportPipeline.encode(photo, rendered, FrameOptions(), Typeface.DEFAULT)

        assertFalse("EXIF nie może trafić do eksportu", JpegMetadata.hasExif(jpeg))
        val out = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(h, out.width)
        assertEquals(w, out.height)
        // Obrót o 90° w prawo: lewa (czerwona) połowa ląduje na górze, prawa (niebieska) na dole.
        assertTrue(isRed(out.getPixel(h / 2, w / 4)))
        assertTrue(isBlue(out.getPixel(h / 2, w * 3 / 4)))
    }

    @Test
    fun withoutExifPixelsStayInPlace() {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) bmp.setPixel(x, y, if (x < w / 2) Color.RED else Color.BLUE)
        val buf = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val photo = PhotoLoader.load({ ByteArrayInputStream(buf) })
        assertEquals(w, photo.width)
        assertTrue(isRed(photo.getPixel(w / 4, h / 2)))
    }

    @Test
    fun frameAndSignatureFitRenderOnCanvas() {
        val photo = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val opts = FrameOptions(true, FrameAspect.Square, signatureEnabled = true, signature = "Lato 2026")
        assertEquals(SignatureFitStatus.Fits, FrameRenderer.status(opts, 300, 200, Typeface.DEFAULT))
        val out = FrameRenderer.render(photo, opts, Typeface.DEFAULT)
        assertEquals(out.width, out.height)
        assertEquals(Color.WHITE, out.getPixel(1, 1))
    }

    @Test(expected = SignatureTooLongException::class)
    fun tooLongSignatureBlocksEncode() {
        val photo = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val opts = tooLong()
        assertEquals(SignatureFitStatus.TooLong, FrameRenderer.status(opts, 300, 200, Typeface.DEFAULT))
        ExportPipeline.encode(photo, photo, opts, Typeface.DEFAULT)
    }

    @Test
    fun tooLongSignatureIsAMessageNotACrash() = runBlocking {
        val state = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT)
        val src = jpegWithOrientation(ExifOrientation.NORMAL)
        val photo = PhotoLoader.load({ ByteArrayInputStream(src) })
        state.setPhotoForTest(photo)
        state.frame = tooLong()
        var saved = false
        val msg = state.export { saved = true }
        assertEquals(UiMessage.SignatureTooLong, msg)
        assertFalse("Nic nie może zostać zapisane", saved)
    }

    @Test
    fun displayP3SurvivesFrameAndKeepsIccInExport() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val photo = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888, false, p3)
        photo.eraseColor(Color.rgb(200, 120, 40))
        val rendered = FrameRenderer.render(photo, FrameOptions(enabled = true), Typeface.DEFAULT)
        assertEquals(p3, rendered.colorSpace)
        val jpeg = JpegExporter.encode(rendered)
        assertTrue("Brak profilu ICC po eksporcie P3", JpegMetadata.hasIcc(jpeg))
        assertFalse(JpegMetadata.hasExif(jpeg))
    }

    private fun tooLong() = FrameOptions(
        enabled = true,
        aspect = FrameAspect.Classic32,
        margin = 0.15f,
        signatureEnabled = true,
        signature = "Linia 1\nLinia 2\nLinia 3\nLinia 4"
    )
}
