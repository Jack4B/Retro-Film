package app.retrofilm.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bufor kołowy logu czasów: limit wpisów, kolejność, CSV, czyszczenie i brak zapisu przy wyłączonym logu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PerfRingTest {
    private fun entry(i: Int) =
        PerfEntry(1_700_000_000_000L + i, "stage$i", i.toLong(), "gpu", 4000, 3000, i % 2 == 0, "Pixel")

    @Test
    fun keepsOnlyNewestEntriesInOrder() {
        val ring = PerfRing(capacity = 3)
        (1..5).forEach { ring.add(entry(it)) }
        assertEquals(listOf("stage3", "stage4", "stage5"), ring.entries().map { it.stage })
        assertEquals(3, ring.size)
    }

    @Test
    fun csvHasHeaderAndEscapedRows() {
        val ring = PerfRing(capacity = 10)
        ring.add(entry(1).copy(device = "realme, GT \"7T\""))
        val lines = ring.toCsv().trim().lines()
        assertEquals("timestamp,stage,ms,backend,width,height,hdr,device,note", lines[0])
        assertTrue(lines[1], lines[1].endsWith(",stage1,1,gpu,4000,3000,off,\"realme, GT \"\"7T\"\"\","))
        assertTrue(lines[1].startsWith("2023-11-14T"))
    }

    @Test
    fun clearEmptiesBuffer() {
        val ring = PerfRing(capacity = 10)
        ring.add(entry(1))
        ring.clear()
        assertEquals(0, ring.entries().size)
    }

    @Test
    fun perfLogRecordsOnlyWhenEnabled() {
        PerfLog.recorder = null
        PerfLog.log("off_stage", 5)
        val ring = PerfRing()
        assertEquals(200, ring.capacity)
        PerfLog.recorder = ring
        PerfLog.context = PerfContext("cpu", 100, 50, hdr = true)
        PerfLog.log("on_stage", 7)
        PerfLog.recorder = null
        PerfLog.log("off_again", 9)
        val e = ring.entries().first()
        assertEquals("on_stage", e.stage)
        assertEquals(7L, e.ms)
        assertEquals("cpu", e.backend)
        assertEquals(100 to 50, e.width to e.height)
        assertTrue(e.hdr)
        PerfLog.recorder = ring
        PerfLog.log("with_note", 1, "reason=x")
        PerfLog.recorder = null
        assertEquals("reason=x", ring.entries().last().note)
        assertNull(ring.entries().firstOrNull { it.stage.startsWith("off") })
    }
}
