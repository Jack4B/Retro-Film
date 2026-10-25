package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneMaskTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun mask(w: Int, h: Int, left: Int, right: Int): ToneMask {
        val px = IntArray(w * h) { gray(if (it % w < w / 2) left else right) }
        return ToneMask.Builder(w, h).apply { add(px, px.size, w, 0) }.build()
    }

    @Test fun uniformImageGivesUniformMask() {
        val m = mask(120, 80, 100, 100)
        assertEquals(100f / 255f, m.sample(3, 3), 0.01f)
        assertEquals(100f / 255f, m.sample(110, 70), 0.01f)
    }

    @Test fun maskIsLocalAndSmooth() {
        val m = mask(400, 100, 30, 230)
        val dark = m.sample(10, 50)
        val nearEdge = m.sample(195, 50)
        val bright = m.sample(390, 50)
        assertTrue("ciemna strona ciemna", dark < 0.25f)
        assertTrue("jasna strona jasna", bright > 0.75f)
        assertTrue("przy granicy wartość pośrednia", nearEdge > dark + 0.1f && nearEdge < bright - 0.1f)
        assertTrue(dark in 0f..1f && bright in 0f..1f)
    }
}
