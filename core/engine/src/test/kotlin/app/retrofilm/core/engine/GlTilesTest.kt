package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlTilesTest {
    private fun coverage(w: Int, h: Int, max: Int): IntArray {
        val seen = IntArray(w * h)
        GlTiles.plan(w, h, max).forEach { t ->
            val tw = t[GlTiles.W]
            val th = t[GlTiles.H]
            assertTrue(tw in 1..max && th in 1..max)
            for (yy in t[GlTiles.Y] until t[GlTiles.Y] + th) {
                for (xx in t[GlTiles.X] until t[GlTiles.X] + tw) seen[yy * w + xx]++
            }
        }
        return seen
    }

    @Test
    fun tilesCoverImageExactlyOnceWithinLimit() {
        for ((w, h, max) in listOf(Triple(8160, 6120, 4096), Triple(300, 200, 64), Triple(17, 5, 100))) {
            assertTrue("pokrycie ${w}x$h", coverage(w, h, max).all { it == 1 })
        }
        assertEquals(4, GlTiles.plan(8160, 6120, 4096).size)
        assertEquals(1, GlTiles.plan(17, 5, 100).size)
    }
}
