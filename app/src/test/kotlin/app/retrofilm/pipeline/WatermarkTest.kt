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

    private fun isRed(c: Int) = Color.red(c) > 200 && Color.green(c) < 60

    private fun isInk(c: Int) = Color.red(c) < 215 && Color.green(c) < 215 && !isRed(c)

    private fun count(bmp: Bitmap, ys: IntRange, xs: IntRange, test: (Int) -> Boolean): Int =
        ys.sumOf { y -> xs.count { x -> test(bmp.getPixel(x, y)) } }

    @Test fun iconAndNameSitAboveTheQrCode() {
        Watermark.icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(255, 0, 0)) }
        try {
            val out = FrameRenderer.render(
                gray(800, 600),
                FrameOptions(watermark = true),
                android.graphics.Typeface.DEFAULT
            )
            assertTrue("ikona jest widoczna na karcie", count(out, 506 until 521, 724 until 786, ::isRed) > 20)
            assertTrue("nazwa jest widoczna na karcie", count(out, 506 until 521, 724 until 786, ::isInk) > 10)
        } finally {
            Watermark.icon = null
        }
    }

    @Test fun matrixIsSquareQrVersion3() {
        val urlLen = Watermark.URL.length
        assertTrue(urlLen in 15..42)
    }
}
