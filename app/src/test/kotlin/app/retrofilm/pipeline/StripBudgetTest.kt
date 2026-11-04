package app.retrofilm.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** H: bufory pasów wszystkich wątków mieszczą się w budżecie; wynik nie zależy od wysokości pasa ani liczby wątków. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripBudgetTest {
    private val mb = 1L shl 20
    private val sharp = Adjustments(sharpness = 1f, grain = 0.4f, vignette = 0.3f)

    // najgorszy przypadek 12 MP z G: zapas wyostrzania (dekonwolucja) + odbandowanie
    private fun plans(halo: Int, sharpMargin: Int, deband: Int) =
        StripPlans(null, null, null, null, null, null, null, halo, 4096, deband, sharpMargin)

    @Test fun previewSizeKeepsAllEightThreads() {
        // I: H obcinało tu wątki do 2–3 (regres open_full_preview 568 → 1227 ms na realme)
        val p = plans(halo = 70, sharpMargin = 30, deband = 17)
        val l = StripBudget.layout(1536, 2048, p, sharp, StripBudget.bytes(512 * mb), workers = 8)
        assertTrue("$l", l.threads == 8)
    }

    @Test fun twelveMpTypicalUsesMostCores() {
        val p = plans(halo = 70, sharpMargin = 30, deband = 34)
        val l = StripBudget.layout(3072, 4096, p, sharp, StripBudget.bytes(512 * mb), workers = 8)
        assertTrue("$l", l.threads >= 6)
    }

    @Test fun twelveMpFitsBudgetOfTypicalHeap() {
        val p = plans(halo = 156, sharpMargin = 122, deband = 34)
        val budget = StripBudget.bytes(512 * mb)
        val l = StripBudget.layout(3072, 4096, p, sharp, budget)
        val total = StripBudget.workerBytes(StripBudget.tileWidth(3072, l.tilesX, 156), l.core, p, true) * l.threads
        assertTrue("total=${total / mb} MB budget=${budget / mb} MB $l", total <= budget && l.threads >= 1)
        assertTrue(budget <= 256 * mb)
    }

    @Test fun oldLayoutWasOverAGigabyte() {
        // G: rdzeń 6 × zapas, 8 wątków – źródło OOM przy zapisie
        val p = plans(halo = 156, sharpMargin = 122, deband = 34)
        val g = StripBudget.workerBytes(3072, StripBudget.HALO_FACTOR * 156, p, true) * 8
        assertTrue("G=${g / mb} MB", g > 1024 * mb)
    }

    @Test fun fiftyMpStillOneThreadMinimumStrip() {
        val p = plans(halo = 200, sharpMargin = 160, deband = 68)
        val l = StripBudget.layout(6144, 8192, p, sharp, StripBudget.MIN_BYTES)
        assertTrue("$l", l.threads == 1 && l.core == StripBudget.MIN_CORE)
    }

    @Test fun resultIndependentOfBudget() {
        val w = 300
        val h = 700
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x xor y) and 0xFF)
        }
        val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val adj = Adjustments(exposure = 0.2f, sharpness = 0.8f, grain = 0.3f, vignette = 0.3f)
        val wide = StripPipeline.run(src, adj, true, emptyList(), StripIo(budget = Long.MAX_VALUE))
        val tiny = StripPipeline.run(src, adj, true, emptyList(), StripIo(budget = 1))
        val a = IntArray(w * h).also { wide.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { tiny.getPixels(it, 0, w, 0, 0, w, h) }
        assertArrayEquals(a, b)
    }

    @Test fun phoneHeapBudgetIsCappedAt128Mb() {
        org.junit.Assert.assertEquals(128L * mb, StripBudget.bytes(512 * mb))
    }
}
