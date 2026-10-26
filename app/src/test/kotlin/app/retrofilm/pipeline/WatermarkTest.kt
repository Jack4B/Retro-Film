package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarkTest {
    private fun gray(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(128, 128, 128)) }

    @Test fun withoutWatermarkPhotoIsUntouched() {
        val src = gray(400, 300)
        val out = FrameRenderer.render(src, FrameOptions(), android.graphics.Typeface.DEFAULT)
        assertSame(src, out)
    }

    @Test fun watermarkDrawsQrInBottomRightOnACopy() {
        val src = gray(400, 300)
        val out = FrameRenderer.render(src, FrameOptions(watermark = true), android.graphics.Typeface.DEFAULT)
        assertNotSame(src, out)
        assertEquals(Color.rgb(128, 128, 128), src.getPixel(380, 280))
        var dark = 0
        var white = 0
        for (y in 200 until 300) {
            for (x in 300 until 400) {
                val c = out.getPixel(x, y)
                if (Color.red(c) < 40) dark++
                if (Color.red(c) > 240) white++
            }
        }
        assertTrue("są ciemne i białe moduły kodu", dark > 100 && white > 100)
        assertEquals(Color.rgb(128, 128, 128), out.getPixel(10, 10))
    }

    @Test fun iconAndNameSitAboveTheQrCode() {
        val icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(255, 0, 0)) }
        Watermark.icon = icon
        try {
            val out = FrameRenderer.render(
                gray(800, 600),
                FrameOptions(watermark = true),
                android.graphics.Typeface.DEFAULT
            )
            var red = 0
            var nameInk = 0
            for (y in 380 until 520) {
                for (x in 600 until 800) {
                    val c = out.getPixel(x, y)
                    if (Color.red(c) > 200 && Color.green(c) < 60) red++
                    if (Color.red(c) < 80 && Color.green(c) < 80 && Color.blue(c) < 80 && y < 440) nameInk++
                }
            }
            assertTrue("ikona jest widoczna na karcie", red > 20)
            assertTrue("nazwa jest widoczna na karcie", nameInk > 20)
        } finally {
            Watermark.icon = null
        }
    }

    @Test fun matrixIsSquareQrVersion3() {
        val urlLen = Watermark.URL.length
        assertTrue(urlLen in 15..42)
    }
}
