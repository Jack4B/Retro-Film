package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
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
class AdjustmentsTest {
    private val eps = 1e-4f

    @Test
    fun neutralAdjustmentsGiveIdentityMatrixAndAreFlaggedNeutral() {
        assertTrue(Adjustments.None.isNeutral)
        assertFalse(Adjustments(exposure = 0.1f).isNeutral)
        assertArrayEquals(AdjustmentMatrix.identity(), AdjustmentMatrix.build(Adjustments.None), eps)
    }

    @Test
    fun exposurePlusOneEvDoublesChannels() {
        val m = AdjustmentMatrix.build(Adjustments(exposure = 1f))
        assertEquals(2f, m[0], eps)
        assertEquals(2f, m[6], eps)
        assertEquals(2f, m[12], eps)
    }

    @Test
    fun contrastScalesAroundMidGray() {
        val m = AdjustmentMatrix.build(Adjustments(contrast = 0.5f))
        assertEquals(1.5f, m[0], eps)
        assertEquals(127.5f * (1f - 1.5f), m[4], eps)
        // 127,5 zostaje 127,5
        assertEquals(127.5f, m[0] * 127.5f + m[4], eps)
    }

    @Test
    fun temperatureWarmsRedAndCoolsBlue() {
        val m = AdjustmentMatrix.build(Adjustments(temperature = 1f))
        assertEquals(1.15f, m[0], eps)
        assertEquals(1f, m[6], eps)
        assertEquals(0.85f, m[12], eps)
    }

    @Test
    fun fullDesaturationMakesChannelsEqualForAnyColor() {
        val m = AdjustmentMatrix.build(Adjustments(saturation = -1f))
        fun channel(row: Int, r: Float, g: Float, b: Float) = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b
        val gray = channel(0, 200f, 40f, 90f)
        assertEquals(gray, channel(1, 200f, 40f, 90f), eps * 255)
        assertEquals(gray, channel(2, 200f, 40f, 90f), eps * 255)
    }

    @Test
    fun applierReturnsSameBitmapWhenNeutralAndChangesPixelsOtherwise() {
        val src = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        src.setPixel(0, 0, Color.rgb(60, 60, 60))
        src.setPixel(1, 0, Color.rgb(200, 100, 50))
        assertSame(src, AdjustmentsApplier.apply(src, Adjustments.None))
        val out = AdjustmentsApplier.apply(src, Adjustments(exposure = 1f))
        assertTrue(abs(Color.red(out.getPixel(0, 0)) - 120) <= 2)
        assertEquals(255, Color.red(out.getPixel(1, 0)))
        assertEquals(60, Color.red(src.getPixel(0, 0)))
    }
}
