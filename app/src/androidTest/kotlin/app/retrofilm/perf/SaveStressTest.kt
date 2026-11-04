package app.retrofilm.perf

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.ui.MainState
import app.retrofilm.ui.UiMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * H: zapis 12 MP i 50 MP Display P3 z ziarnem, wyostrzaniem, winietą i ramką – kilka razy z rzędu (wyciek pamięci
 * między zapisami) i dwa naraz. Aplikacja nie może się zamknąć: każdy zapis kończy się komunikatem.
 * Zgłasza czas, szczyt sterty i stertę po GC (`RetroPerf`). Zdjęcia syntetyczne – bez prywatnych plików.
 */
@RunWith(AndroidJUnit4::class)
class SaveStressTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val look = Adjustments(sharpness = 1f, grain = 0.5f, vignette = 0.4f, exposure = 0.1f)

    private fun photo(w: Int, h: Int, name: String): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
        val row = IntArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                row[x] =
                    (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x xor y) and 0xFF)
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
        val f = File(context.cacheDir, "$name.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_Q, it) }
        bmp.recycle()
        return f
    }

    private fun report(name: String, v: Long) {
        Log.i(TAG, "$name=$v")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putLong(name, v) })
    }

    private fun heapAfterGcMb(): Long {
        repeat(2) { Runtime.getRuntime().gc() }
        val r = Runtime.getRuntime()
        return (r.totalMemory() - r.freeMemory()) shr MB_SHIFT
    }

    private suspend fun state(graph: AppGraph, file: File) = MainState(graph, Typeface.DEFAULT).apply {
        load(context, Uri.fromFile(file))
        film = graph.filmEngine.availableLuts().first().id
        adjustments = look
        frame = FrameOptions(enabled = true)
    }

    private fun ok(m: UiMessage) = m == UiMessage.Saved || m == UiMessage.SavedReduced

    private fun stress(w: Int, h: Int, tag: String, repeats: Int) = runBlocking {
        val graph = AppGraph(context)
        val file = photo(w, h, tag)
        val ring = PerfRing()
        PerfLog.recorder = ring
        try {
            report("${tag}_heap_max_mb", Runtime.getRuntime().maxMemory() shr MB_SHIFT)
            report("${tag}_heap_before_mb", heapAfterGcMb())
            val s = state(graph, file)
            repeat(repeats) { i ->
                ring.clear()
                val t = System.nanoTime()
                val m = s.export(context) { }
                report("${tag}_save${i}_ms", (System.nanoTime() - t) / NS_MS)
                report("${tag}_save${i}_ok", if (ok(m)) 1 else 0)
                report("${tag}_save${i}_reduced", if (m == UiMessage.SavedReduced) 1 else 0)
                ring.entries().firstOrNull { it.stage == "save_peak_mem" }?.let {
                    report("${tag}_save${i}_peak_mb", it.note.removePrefix("mb=").toLong())
                }
                ring.entries().filter { it.stage.startsWith("save_") && it.ms > 0 }
                    .forEach { report("${tag}_save${i}_${it.stage}", it.ms) }
                report("${tag}_save${i}_heap_after_gc_mb", heapAfterGcMb())
                assertTrue("$m", m !is UiMessage.LoadFailed)
            }
        } finally {
            PerfLog.recorder = null
            file.delete()
        }
    }

    @Test fun repeated12mp() = stress(W12, H12, "s12", REPEATS)

    @Test fun repeated50mp() = stress(W50, H50, "s50", 2)

    @Test fun concurrent12mp(): Unit = runBlocking {
        val graph = AppGraph(context)
        val file = photo(W12, H12, "c12")
        try {
            val a = state(graph, file)
            val b = state(graph, file)
            val t = System.nanoTime()
            val res = listOf(a, b).map { s -> async(Dispatchers.Default) { s.export(context) { } } }.awaitAll()
            report("c12_both_ms", (System.nanoTime() - t) / NS_MS)
            report("c12_ok", res.count { ok(it) }.toLong())
            report("c12_heap_after_gc_mb", heapAfterGcMb())
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TAG = "RetroPerf"
        const val W12 = 3072
        const val H12 = 4096
        const val W50 = 6144
        const val H50 = 8192
        const val REPEATS = 4
        const val JPEG_Q = 95
        const val MB_SHIFT = 20
        const val NS_MS = 1_000_000L
    }
}
