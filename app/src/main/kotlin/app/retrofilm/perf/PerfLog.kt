package app.retrofilm.perf

import android.os.SystemClock
import android.util.Log

/**
 * Czasy kluczowych etapów w logcat (tag [TAG], poziom INFO, także w wydaniu). Koszt: jeden odczyt zegara i
 * jeden `Log.i` na etap – bez bibliotek, bez wysyłania czegokolwiek z telefonu (aplikacja pozostaje offline).
 * Odczyt: `adb logcat -s RetroFilmPerf:I`.
 */
object PerfLog {
    const val TAG = "RetroFilmPerf"

    fun now(): Long = SystemClock.elapsedRealtime()

    fun since(name: String, start: Long) = log(name, now() - start)

    fun log(name: String, ms: Long) {
        Log.i(TAG, "${name}_ms=$ms")
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
