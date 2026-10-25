package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorationTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun guidedFilterKeepsStepEdgeButSmoothsNoise() {
        val w = 40
        val h = 20
        val step = FloatArray(w * h) { if (it % w < 20) 0.2f else 0.8f }
        val noisy = FloatArray(w * h) { step[it] + if ((it * 7) % 5 == 0) 0.03f else -0.01f }
        val out = GuidedFilter.filter(step, noisy, w, h, 3, 0.001f)
        assertEquals(0.2f, out[10 * w + 5], 0.03f)
        assertEquals(0.8f, out[10 * w + 34], 0.03f)
        assertTrue("krawędź nie rozmyta", out[10 * w + 22] - out[10 * w + 17] > 0.5f)
    }

    @Test fun dehazeIncreasesContrastOfHazyImage() {
        val w = 64
        val h = 64
        val px = IntArray(w * h) { if ((it % w) < 32) rgb(190, 195, 200) else rgb(150, 160, 170) }
        val before = 200 - 150
        Dehaze.apply(px, w, h, 1f)
        val span = ((px[10] shr 16) and 0xFF) - ((px[w - 10] shr 16) and 0xFF)
        assertTrue("kontrast rośnie", span > before)
    }

    @Test fun dehazeZeroIsIdentity() {
        val px = IntArray(100) { rgb(it, 100, 200 - it) }
        val copy = px.copyOf()
        Dehaze.apply(px, 10, 10, 0f)
        assertTrue(px.contentEquals(copy))
    }

    @Test fun chromaDenoiseSmoothsColourSpecklesKeepsLuma() {
        val w = 40
        val h = 40
        val px = IntArray(w * h) { if ((it + it / w) % 2 == 0) rgb(140, 120, 120) else rgb(120, 120, 140) }
        val lumaBefore = 0.299 * 140 + 0.587 * 120 + 0.114 * 120
        ChromaDenoise.apply(px, w, h, 1f)
        val c = px[20 * w + 20]
        val spread = kotlin.math.abs(((c shr 16) and 0xFF) - (c and 0xFF))
        assertTrue("plamki kolorów wygładzone", spread < 10)
        val y = 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        assertEquals(lumaBefore, y, 12.0)
    }

    @Test fun newFieldsAreNotNeutral() {
        assertTrue(Adjustments(dehaze = 0.3f).hasRestoration)
        assertTrue(!Adjustments(dehaze = 0.3f).isNeutral && !Adjustments(denoise = 0.3f).isNeutral)
    }
}
