package app.retrofilm.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Etapy zapisu: osobne wpisy z prefiksem save_, szczyt pamięci i zdarzenie spadku przy braku pamięci. */
class SaveTraceTest {
    private val logged = mutableListOf<Triple<String, Long, String>>()
    private var mem = 100L * SaveTrace.MB

    private fun trace() = SaveTrace(sink = { n, ms, note -> logged += Triple(n, ms, note) }, usedBytes = { mem })

    @Test
    fun logsEachStageAndPeakMemory() {
        val t = trace()
        val v = t.stage("decode") {
            mem = 300L * SaveTrace.MB
            42
        }
        t.stage("encode_jpeg444") { mem = 150L * SaveTrace.MB }
        t.finish()
        assertEquals(42, v)
        assertEquals(listOf("save_decode", "save_encode_jpeg444", "save_peak_mem"), logged.map { it.first })
        assertEquals("mb=300", logged.last().third)
    }

    @Test
    fun stageIsLoggedEvenWhenItThrows() {
        val t = trace()
        runCatching { t.stage("lut_render") { throw OutOfMemoryError() } }
        t.event("oom_fallback", "side=4096")
        assertEquals(listOf("save_lut_render", "save_oom_fallback"), logged.map { it.first })
        assertTrue(logged.last().third == "side=4096")
    }
}
