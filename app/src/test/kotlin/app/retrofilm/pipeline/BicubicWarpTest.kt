package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BicubicWarpTest {
    private fun gradient(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = x * 255 / (w - 1)
                b.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
    }

    @Test fun catmullRomWeightsSumToOneAndInterpolate() {
        val w = FloatArray(4)
        for (i in 0..10) {
            BicubicWarp.weights(i / 10f, w)
            assertEquals(1f, w.sum(), 1e-5f)
        }
        BicubicWarp.weights(0f, w)
        assertEquals(1f, w[1], 1e-6f)
        BicubicWarp.weights(1f, w)
        assertEquals(1f, w[2], 1e-5f)
    }

    @Test fun straighteningRotatesAGradientAccurately() {
        val src = gradient(400, 300)
        val g = Geometry(straighten = 4f)
        val out = GeometryApplier.apply(src, g)
        assertNotSame(src, out)
        val (w, h) = GeometryMath.outputSize(400, 300, g)
        assertEquals(w, out.width)
        assertEquals(h, out.height)
        // środek obrazu wyniku ≈ środek źródła (gradient: 127–128)
        assertEquals(127.5f, Color.red(out.getPixel(w / 2, h / 2)).toFloat(), 2f)
        // po obrocie o 4° piksel o dx w prawo od środka leży w źródle o dx·cosθ w prawo
        val dx = 60
        val expected = (200f + dx * Math.cos(Math.toRadians(4.0)).toFloat()) * 255f / 399f
        assertEquals(expected, Color.red(out.getPixel(w / 2 + dx, h / 2)).toFloat(), 3f)
        assertTrue("alfa nieprzezroczysta", Color.alpha(out.getPixel(5, 5)) == 255)
    }

    @Test fun pureCropWithoutStraighteningKeepsPixelsExact() {
        val src = gradient(200, 100)
        val out = GeometryApplier.apply(src, Geometry(zoom = 2f))
        assertEquals(100, out.width)
        // kopiowanie piksel w piksel: sąsiednie piksele różnią się jak w źródle (brak rozmycia)
        val a = Color.red(out.getPixel(10, 10))
        val b = Color.red(out.getPixel(11, 10))
        assertTrue(abs((b - a) - 255 / 199f) < 1.6f)
    }

    @Test fun resizeNeverUpscalesAndKeepsAspect() {
        val src = gradient(1000, 500)
        assertTrue(ExportPipeline.resize(src, 0) === src)
        assertTrue(ExportPipeline.resize(src, 2000) === src)
        val small = ExportPipeline.resize(src, 250)
        assertEquals(250, small.width)
        assertEquals(125, small.height)
    }
}
