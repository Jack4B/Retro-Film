package app.retrofilm.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** I (audyt efektów): halacja – czerwono-pomarańczowa poświata wokół świateł, jak odbicie od podłoża filmu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HalationTest {
    private val w = 240
    private val h = 160

    private fun spot(): Bitmap {
        val px = IntArray(w * h) { i ->
            val dx = i % w - w / 2
            val dy = i / w - h / 2
            if (dx * dx + dy * dy < 100) -1 else 0xFF202020.toInt()
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pixels(b: Bitmap) = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }

    @Test fun glowIsWarmAroundHighlight() {
        val out = pixels(StripPipeline.run(spot(), Adjustments(halation = 1f)))
        val near = out[(h / 2) * w + w / 2 + 16]
        val far = out[5 * w + 5]
        val r = (near shr 16) and 0xFF
        val b = near and 0xFF
        assertTrue("near=${Integer.toHexString(near)}", r > 0x20 + 6 && r > b + 4)
        assertTrue("far=${Integer.toHexString(far)}", (far shr 16 and 0xFF) <= 0x22)
    }

    @Test fun zeroIsIdentity() {
        val src = spot()
        assertArrayEquals(pixels(src), pixels(StripPipeline.run(src, Adjustments(exposure = 0f, halation = 0f))))
    }

    @Test fun independentOfTiling() {
        val adj = Adjustments(halation = 0.8f, grain = 0.2f, vignette = 0.4f)
        val a = StripPipeline.run(spot(), adj, io = StripIo(budget = Long.MAX_VALUE))
        val b = StripPipeline.run(spot(), adj, io = StripIo(budget = 1))
        assertArrayEquals(pixels(a), pixels(b))
    }

    @Test fun vignetteIsCircularNotStretched() {
        // naturalna winieta zależy od odległości od osi, nie od proporcji kadru (H: elipsa po normalizacji osi)
        val flat = Bitmap.createBitmap(IntArray(w * h) { 0xFF808080.toInt() }, w, h, Bitmap.Config.ARGB_8888)
        val out = pixels(StripPipeline.run(flat, Adjustments(vignette = 1f)))
        val right = out[(h / 2) * w + w / 2 + 70] and 0xFF
        val down = out[(h / 2 + 70) * w + w / 2] and 0xFF
        assertTrue("right=$right down=$down", kotlin.math.abs(right - down) <= 1)
    }
}
