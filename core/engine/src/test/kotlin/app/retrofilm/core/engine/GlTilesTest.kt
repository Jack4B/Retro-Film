package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlTilesTest {
    @Test
    fun tilesCoverImageExactlyOnceWithinLimit() {
        for ((w, h, max) in listOf(Triple(8160, 6120, 4096), Triple(300, 200, 64), Triple(17, 5, 100))) {
            val seen = IntArray(w * h)
            val tiles = GlTiles.plan(w, h, max)
            for ((x, y, tw, th) in tiles.map { it.toList() }) {
                assertTrue(tw in 1..max && th in 1..max)
                for (yy in y until y + th) for (xx in x until x + tw) seen[yy * w + xx]++
            }
            assertTrue("pokrycie ${w}x$h", seen.all { it == 1 })
        }
        assertEquals(4, GlTiles.plan(8160, 6120, 4096).size)
        assertEquals(1, GlTiles.plan(17, 5, 100).size)
    }
}
