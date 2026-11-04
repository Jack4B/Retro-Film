package app.retrofilm.perf

import android.content.Context
import android.util.Log
import java.io.File

/**
 * H: ostatni błąd krytyczny (wyjątek + stos) w pliku aplikacji. Po ponownym uruchomieniu trafia do logu czasów jako
 * wpis `last_crash`, więc da się go wyeksportować z Ustawienia › Diagnostyka (CSV). Wszystko lokalnie, bez sieci.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    private const val FRAMES = 14
    private const val TAG = "RetroCrash"

    @Volatile private var dir: File? = null

    fun install(context: Context) {
        if (dir != null) return
        dir = context.filesDir
        file()?.takeIf { it.exists() }?.let { runCatching { PerfLog.always("last_crash", it.readText()) } }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file()?.writeText("thread=${thread.name} ${describe(e)}") }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Błąd złapany przy zapisie: do logcat, logu czasów i pliku (bez zamykania aplikacji). */
    fun record(where: String, e: Throwable) {
        val text = "where=$where ${describe(e)}"
        runCatching {
            Log.e(TAG, text, e)
            PerfLog.always("save_error", text)
            file()?.writeText(text)
        }
    }

    fun describe(e: Throwable): String = buildString {
        append(e.javaClass.name).append(": ").append(e.message.orEmpty())
        e.stackTrace.take(FRAMES).forEach { append(" | at ").append(it) }
        e.cause?.let { append(" | cause ").append(it.javaClass.name).append(": ").append(it.message.orEmpty()) }
    }

    private fun file(): File? = dir?.let { File(it, FILE) }
}
