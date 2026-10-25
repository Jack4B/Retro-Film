package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LutMemoryCacheTest {
    private fun lut(size: Int = 2) = Lut(size, ByteArray(Lut.expectedBytes(size, 1)))
    private val one = Lut.expectedBytes(2, 1).toLong()

    @Test
    fun evictsLeastRecentlyUsed() {
        val c = LutMemoryCache(maxBytes = 2 * one)
        c.put("a", lut())
        c.put("b", lut())
        c.get("a")
        c.put("c", lut())
        assertEquals(listOf("a", "c"), c.keys())
        assertFalse(c.contains("b"))
        assertEquals(2 * one, c.sizeBytes())
    }

    @Test
    fun reusesCachedLutWithoutReloading() {
        val c = LutMemoryCache(maxBytes = 2 * one)
        var loads = 0
        val first = c.getOrPut("a") {
            loads++
            lut()
        }
        val second = c.getOrPut("a") {
            loads++
            lut()
        }
        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun nullLoadIsNotCached() {
        val c = LutMemoryCache()
        assertNull(c.getOrPut("x") { null })
        assertFalse(c.contains("x"))
    }

    @Test
    fun oversizedNewestEntryIsKept() {
        val c = LutMemoryCache(maxBytes = one)
        c.put("a", lut())
        c.put("big", lut(4))
        assertEquals(listOf("big"), c.keys())
        assertTrue(c.sizeBytes() > one)
    }

    @Test
    fun defaultFitsThree144Luts() {
        assertEquals(3L * Lut.expectedBytes(144, 1), LutMemoryCache.DEFAULT_MAX_BYTES)
    }
}
