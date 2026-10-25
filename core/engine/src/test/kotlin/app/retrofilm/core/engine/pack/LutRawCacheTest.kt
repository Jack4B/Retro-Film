package app.retrofilm.core.engine.pack

import app.retrofilm.core.engine.Lut
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LutRawCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private val one = Lut.expectedBytes(8, 1).toLong()

    @Test
    fun lruEvictsLeastRecentlyUsed() {
        var reported = -1L
        val cache = LutRawCache(File(tmp.root, "raw"), maxBytes = one * 2) { reported = it }
        var unpacks = 0
        val unpack = {
            unpacks++
            Lut.identity(8)
        }
        cache.get("a", 8, unpack = unpack)
        cache.get("b", 8, unpack = unpack)
        cache.get("a", 8, unpack = unpack) // a świeższe niż b
        cache.get("c", 8, unpack = unpack)
        assertEquals(3, unpacks)
        assertTrue(cache.contains("a"))
        assertFalse(cache.contains("b"))
        assertTrue(cache.contains("c"))
        assertEquals(one * 2, reported)
        cache.clear()
        assertEquals(0L, cache.sizeBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongUnpackedLengthRejected() {
        LutRawCache(tmp.root).get("a", 16) { Lut.identity(8) }
    }
}
