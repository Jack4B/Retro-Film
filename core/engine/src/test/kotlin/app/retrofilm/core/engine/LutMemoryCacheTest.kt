package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun concurrentGetOrPutLoadsOnce() {
        val c = LutMemoryCache(maxBytes = 4 * one)
        val loads = AtomicInteger()
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = (0 until threads).map {
            pool.submit<Lut?> {
                start.await()
                c.getOrPut("a") {
                    loads.incrementAndGet()
                    Thread.sleep(20)
                    lut()
                }
            }
        }
        start.countDown()
        val luts = results.map { it.get(5, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, loads.get())
        assertTrue(luts.all { it === luts[0] })
    }

    @Test
    fun concurrentMixedAccessKeepsInvariants() {
        val c = LutMemoryCache(maxBytes = 3 * one)
        val pool = Executors.newFixedThreadPool(8)
        val tasks = (0 until 8).map { t ->
            pool.submit {
                repeat(2000) { i ->
                    val id = "k${(i * 7 + t) % 6}"
                    c.getOrPut(id) { lut() }
                    c.get("k${i % 6}")
                    c.keys()
                }
            }
        }
        tasks.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(c.keys().size <= 3)
        assertEquals(c.keys().size * one, c.sizeBytes())
    }
}
