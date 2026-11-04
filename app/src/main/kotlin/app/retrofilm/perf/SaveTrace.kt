package app.retrofilm.perf

import android.os.Debug

/**
 * Etapy jednego zapisu jako osobne wpisy `save_<etap>` w [PerfLog] oraz szczyt pamięci (sterta Javy + natywna,
 * gdzie żyją piksele bitmap), próbkowany na granicach etapów. Koszt: dwa odczyty zegara i licznika na etap.
 */
class SaveTrace(
    private val sink: (String, Long, String) -> Unit = PerfLog::log,
    private val usedBytes: () -> Long = ::processBytes
) {
    private var peak = 0L

    inline fun <T> stage(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            done(name, (System.nanoTime() - start) / NANOS_PER_MS)
        }
    }

    @PublishedApi
    internal fun done(name: String, ms: Long) {
        sample()
        sink("save_$name", ms, "")
    }

    /** Zdarzenie bez czasu (np. `oom_fallback`, `backend_fallback`). */
    fun event(name: String, note: String) = sink("save_$name", 0, note)

    fun sample() {
        peak = maxOf(peak, usedBytes())
    }

    fun finish() = sink("save_peak_mem", 0, "mb=${peak / MB}")

    companion object {
        /** Etap [name], gdy jest ślad; bez śladu (miniatury filmów, testy) samo [block]. */
        inline fun <T> SaveTrace?.timed(name: String, block: () -> T): T = if (this ==
            null
        ) {
            block()
        } else {
            stage(name, block)
        }

        const val MB = 1024L * 1024L
        const val NANOS_PER_MS = 1_000_000L

        private fun processBytes(): Long {
            val rt = Runtime.getRuntime()
            return rt.totalMemory() - rt.freeMemory() + Debug.getNativeHeapAllocatedSize()
        }
    }
}
