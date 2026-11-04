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
import app.retrofilm.ui.MainState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Etapy zapisu 12 MP (3072×4096 jak realme RMX5085, Display P3 i sRGB, z filmem) przez [MainState.export] – te
 * same wpisy `save_*`, które użytkownik widzi w „Logach czasów”. Wynik w logcat `RetroPerf` (`name=N ms`).
 * Zdjęcie jest syntetyczne (gradient + szum) – bez prywatnych plików w repo.
 */
@RunWith(AndroidJUnit4::class)
class SaveStagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun photo(space: ColorSpace.Named): File {
        val w = W
        val h = H
        var seed = 1234567
        val px = IntArray(w * h) { i ->
            seed = seed * 1103515245 + 12345
            val n = (seed ushr 24) and 0x1F
            val x = i % w
            val y = i / w
            (0xFF shl 24) or (((x * 255 / w) xor n) shl 16) or (((y * 255 / h) xor n) shl 8) or ((x + y) / 31 and 0xFF)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, ColorSpace.get(space))
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val f = File(context.cacheDir, "save-${space.name}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_Q, it) }
        bmp.recycle()
        return f
    }

    private fun report(name: String, ms: Long) {
        Log.i(TAG, "$name=$ms ms")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putLong(name, ms) })
    }

    @Test
    fun saveStages12mp(): Unit = runBlocking {
        val graph = AppGraph(context)
        val film = graph.filmEngine.availableLuts().first()
        for (space in listOf(ColorSpace.Named.DISPLAY_P3, ColorSpace.Named.SRGB)) {
            val file = photo(space)
            val ring = PerfRing()
            PerfLog.recorder = ring
            try {
                val state = MainState(graph, Typeface.DEFAULT)
                state.load(context, Uri.fromFile(file))
                state.film = film.id
                repeat(2) { run ->
                    ring.clear()
                    var size = 0
                    state.export(context) { size = it.size }
                    assertTrue(size > 0)
                    val sp = if (space == ColorSpace.Named.SRGB) "srgb" else "p3"
                    val tag = "${sp}_${if (run == 0) "cold" else "warm"}"
                    ring.entries()
                        .filter { it.stage.startsWith("save_") && it.stage != "save_peak_mem" }
                        .forEach { report("${tag}_${it.stage}", it.ms) }
                    ring.entries().firstOrNull { it.stage == "save_peak_mem" }?.let {
                        report("${tag}_save_peak_mem_mb", it.note.removePrefix("mb=").toLong())
                    }
                }
            } finally {
                PerfLog.recorder = null
                file.delete()
            }
        }
    }

    private companion object {
        const val TAG = "RetroPerf"
        const val W = 3072
        const val H = 4096
        const val JPEG_Q = 95
    }
}
