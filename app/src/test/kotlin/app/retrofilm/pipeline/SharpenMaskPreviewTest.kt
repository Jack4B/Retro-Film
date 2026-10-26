package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Podgląd maski krawędzi wyostrzania pokazuje obraz maski także wtedy, gdy suwak Ilość jest na zero. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharpenMaskPreviewTest {
    private fun edgeImage(): Bitmap {
        val px = IntArray(64 * 64) { if (it % 64 < 32) Color.rgb(40, 90, 160) else Color.rgb(230, 200, 120) }
        return Bitmap.createBitmap(px, 64, 64, Bitmap.Config.ARGB_8888)
    }

    @Test fun maskIsShownEvenWithZeroAmount() {
        val adj = Adjustments(sharpMasking = 0.8f, sharpMaskPreview = true)
        assertTrue(adj.hasDetail)
        val out = AdjustmentsApplier.apply(edgeImage(), adj)
        val c = out.getPixel(10, 10)
        assertEquals("maska jest szara/biała, nie kolor zdjęcia", Color.red(c), Color.blue(c))
        assertTrue("z dala od krawędzi maska jest ciemna", Color.red(c) < 100)
    }

    @Test fun withoutPreviewNothingChangesAtZeroAmount() {
        val src = edgeImage()
        val out = AdjustmentsApplier.apply(src, Adjustments(sharpMasking = 0.8f))
        assertEquals(src.getPixel(10, 10), out.getPixel(10, 10))
    }
}
