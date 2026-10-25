package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class GeometryTest {
    @Test fun neutralKeepsSizeAndBitmap() {
        assertTrue(Geometry().isNeutral)
        assertTrue(Geometry(quarterTurns = 4).isNeutral)
        assertFalse(Geometry(straighten = 1f).isNeutral)
        assertEquals(400 to 300, GeometryMath.outputSize(400, 300, Geometry()))
        val src = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        assertSame(src, GeometryApplier.apply(src, Geometry()))
    }

    @Test fun quarterTurnSwapsSides() {
        assertEquals(300 to 400, GeometryMath.outputSize(400, 300, Geometry(quarterTurns = 1)))
        assertEquals(300 to 400, GeometryMath.outputSize(400, 300, Geometry(quarterTurns = 3)))
        assertEquals(400 to 300, GeometryMath.outputSize(400, 300, Geometry(quarterTurns = 2)))
    }

    @Test fun presetAspectsFollowOrientationAndFitInside() {
        val (w, h) = GeometryMath.outputSize(4000, 3000, Geometry(aspect = CropAspect.Square))
        assertEquals(w, h)
        assertEquals(3000, w)
        val (pw, ph) = GeometryMath.outputSize(3000, 4000, Geometry(aspect = CropAspect.Classic32))
        assertEquals(2f / 3f, pw.toFloat() / ph, 0.01f)
        assertTrue(pw <= 3000 && ph <= 4000)
        val (ww, wh) = GeometryMath.outputSize(4000, 3000, Geometry(aspect = CropAspect.Wide169))
        assertEquals(16f / 9f, ww.toFloat() / wh, 0.01f)
        assertEquals(4000, ww)
    }

    @Test fun straighteningShrinksCropButKeepsAspect() {
        val (w, h) = GeometryMath.outputSize(4000, 3000, Geometry(straighten = 10f))
        assertTrue(w < 4000 && h < 3000)
        assertEquals(4000f / 3000f, w.toFloat() / h, 0.01f)
        val (w0, _) = GeometryMath.outputSize(4000, 3000, Geometry(straighten = 5f))
        assertTrue("większy kąt = mniejszy kadr", w < w0)
        // symetria kąta
        assertEquals(w, GeometryMath.outputSize(4000, 3000, Geometry(straighten = -10f)).first)
    }

    @Test fun straightenedCropContainsOnlyImagePixels() {
        val src = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(200, 100, 50))
        val out = GeometryApplier.apply(src, Geometry(straighten = 8f))
        val (w, h) = GeometryMath.outputSize(200, 120, Geometry(straighten = 8f))
        assertEquals(w, out.width)
        assertEquals(h, out.height)
        // rogi kadru nie mogą być przezroczyste (kadr jest wpisany w obrócone zdjęcie)
        listOf(3 to 3, w - 4 to 3, 3 to h - 4, w - 4 to h - 4).forEach { (x, y) ->
            assertTrue("nieprzezroczysty róg ($x,$y)", Color.alpha(out.getPixel(x, y)) > 250)
        }
    }

    @Test fun rotationMovesPixelsClockwise() {
        val src = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.BLUE)
        src.setPixel(0, 0, Color.RED) // lewy górny róg
        val out = GeometryApplier.apply(src, Geometry(quarterTurns = 1))
        assertEquals(2, out.width)
        assertEquals(4, out.height)
        assertEquals(Color.RED, out.getPixel(1, 0)) // po obrocie w prawo trafia w prawy górny róg
    }
}
