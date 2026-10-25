package app.retrofilm.pipeline

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFilterTest {
    private fun edge(w: Int, h: Int) = IntArray(w * h) { i ->
        val v = if (i % w < w / 2) 80 else 170
        Color.rgb(v, v, v)
    }

    private fun red(c: Int) = (c shr 16) and 0xFF

    @Test fun neutralLeavesPixelsUntouched() {
        val px = edge(64, 8)
        val copy = px.copyOf()
        DetailFilter.apply(px, 64, 8, 0f, 0f)
        assertTrue(px.contentEquals(copy))
    }

    @Test fun sharpnessAddsOvershootAtAnEdge() {
        val w = 64
        val px = edge(w, 8)
        DetailFilter.apply(px, w, 8, 1f, 0f)
        assertTrue("ciemna strona przy krawędzi ciemniejsza", red(px[4 * w + w / 2 - 1]) < 80)
        assertTrue("jasna strona przy krawędzi jaśniejsza", red(px[4 * w + w / 2]) > 170)
        assertEquals("daleko od krawędzi bez zmian", 80, red(px[4 * w + 2]))
    }

    @Test fun sharpnessIgnoresTinyNoise() {
        val px = IntArray(64 * 8) { i ->
            val v = 120 + (i % 2)
            Color.rgb(v, v, v)
        }
        val copy = px.copyOf()
        DetailFilter.apply(px, 64, 8, 1f, 0f)
        assertTrue(px.contentEquals(copy))
    }

    @Test fun clarityAddsLocalContrastInMidtonesAndNegativeSoftens() {
        val w = 200
        val up = IntArray(w * 20) { i ->
            val v = if ((i % w) / 10 % 2 == 0) 110 else 146
            Color.rgb(v, v, v)
        }
        val down = up.copyOf()
        DetailFilter.apply(up, w, 20, 0f, 1f)
        DetailFilter.apply(down, w, 20, 0f, -1f)
        fun spread(p: IntArray) = (0 until w).maxOf { red(p[5 * w + it]) } - (0 until w).minOf { red(p[5 * w + it]) }
        assertTrue(spread(up) > 36)
        assertTrue(spread(down) < 36)
    }

    @Test fun boxBlurKeepsMeanAndSmoothsStep() {
        val w = 32
        val src = FloatArray(w * 4) { if (it % w < w / 2) 0f else 100f }
        val out = DetailFilter.boxBlur(src, w, 4, 3)
        assertEquals(src.average(), out.average(), 1.0)
        assertTrue(out[w / 2] > 5f && out[w / 2] < 95f)
    }
}
