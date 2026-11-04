package app.retrofilm.perf

import android.os.Build
import android.os.SystemClock
import android.util.Log

/** Kontekst bieżącego zdjęcia dopisywany do wpisów logu czasów (backend renderu, wymiary, HDR). */
data class PerfContext(val backend: String = "", val width: Int = 0, val height: Int = 0, val hdr: Boolean = false)

/**
 * Czasy kluczowych etapów w logcat (tag [TAG], poziom INFO, także w wydaniu). Koszt: jeden odczyt zegara i
 * jeden `Log.i` na etap – bez bibliotek, bez sieci (aplikacja pozostaje offline).
 * Odczyt: `adb logcat -s RetroFilmPerf:I`.
 * Gdy w Ustawieniach włączone są „Logi czasów”, wpisy trafiają też do lokalnego [recorder] (null = wył., zero kosztu).
 */
object PerfLog {
    const val TAG = "RetroFilmPerf"

    /** Bufor „Logów czasów” (w pamięci procesu); zapisywany tylko gdy [recorder] == [ring]. */
    val ring = PerfRing()

    @Volatile var recorder: PerfRing? = null

    @Volatile var context: PerfContext = PerfContext()

    private val device: String by lazy { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }

    fun now(): Long = SystemClock.elapsedRealtime()

    fun since(name: String, start: Long) = log(name, now() - start)

    fun log(name: String, ms: Long, note: String = "") {
        Log.i(TAG, if (note.isEmpty()) "${name}_ms=$ms" else "${name}_ms=$ms $note")
        val ring = recorder ?: return
        val c = context
        ring.add(PerfEntry(System.currentTimeMillis(), name, ms, c.backend, c.width, c.height, c.hdr, device, note))
    }

    inline fun <T> time(name: String, block: () -> T): T {
        val start = now()
        try {
            return block()
        } finally {
            since(name, start)
        }
    }
}
