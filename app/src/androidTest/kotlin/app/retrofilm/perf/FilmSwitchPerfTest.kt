package app.retrofilm.perf

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.engine.AppGraph
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wydajność przełączania filmów na urządzeniu: [PASSES] przejścia przez filmy startowe (bundled)
 * + Kodak Ektar 100 (jeśli jest w katalogu), każdy nałożony na bitmapę ~12 MP poza głównym wątkiem.
 * Wynik JSON: `files/perf/film-switch-perf.json` (katalog zewnętrzny aplikacji) + logcat.
 */
@RunWith(AndroidJUnit4::class)
class FilmSwitchPerfTest {
    private data class Sample(
        val pass: Int,
        val film: String,
        val decodeMs: Long,
        val applyMs: Long,
        val thread: String,
        val pssKb: Long
    )

    @Test
    fun switchesThroughBundledFilms() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = AppGraph.get(ctx).filmEngine
        val ids = engine.availableLuts().map { it.id }
        val films = ids.filter { it in STARTERS } + ids.filter { it == EKTAR }
        val ektarBundled = EKTAR in ids
        assertTrue("brak filmów startowych w katalogu: $ids", films.size >= STARTERS.size)

        val src = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        fillGradient(src)
        val mainLag = AtomicLong(0)
        val watchdog = MainThreadWatchdog(mainLag).also { it.start() }
        val samples = mutableListOf<Sample>()
        val passPss = LongArray(PASSES)
        var peakPss = 0L
        try {
            runBlocking {
                for (pass in 1..PASSES) {
                    for (id in films) {
                        val s = withContext(Dispatchers.Default) {
                            val thread = Thread.currentThread().name
                            val t0 = SystemClock.elapsedRealtime()
                            val lut = checkNotNull(engine.load(id)) { "brak LUT $id" }
                            val t1 = SystemClock.elapsedRealtime()
                            val out = engine.renderer.apply(src, lut, 1f)
                            val t2 = SystemClock.elapsedRealtime()
                            if (out !== src) out.recycle()
                            Sample(pass, id, t1 - t0, t2 - t1, thread, 0L)
                        }
                        val pss = samplePssKb()
                        peakPss = maxOf(peakPss, pss)
                        samples += s.copy(pssKb = pss)
                    }
                    Runtime.getRuntime().gc()
                    passPss[pass - 1] = samplePssKb()
                }
            }
        } catch (e: OutOfMemoryError) {
            fail("OOM podczas przełączania filmów: $e")
        } finally {
            watchdog.stop()
        }

        val json = toJson(samples, passPss, peakPss, mainLag.get(), ektarBundled)
        Log.i(TAG, json.toString())
        println("FILM_SWITCH_PERF ${json.toString(2)}")
        writeJson(ctx, json)

        samples.forEach { assertNotEquals("dekodowanie ${it.film} na wątku głównym", MAIN, it.thread) }
        samples.filter { it.pass == 1 }.forEach {
            val limit = if (it.film == EKTAR) MAX_DECODE_MS_256 else MAX_DECODE_MS_144
            assertTrue("pierwsze dekodowanie ${it.film} ${it.decodeMs} ms > $limit ms", it.decodeMs < limit)
        }
        assertTrue("wątek główny zablokowany ${mainLag.get()} ms (ANR)", mainLag.get() < MAX_MAIN_BLOCK_MS)
        assertTrue("szczytowe PSS $peakPss kB >= $MAX_PEAK_PSS_KB kB", peakPss < MAX_PEAK_PSS_KB)
        val leakLimit = passPss[0] * LEAK_FACTOR
        assertTrue("PSS po przejściu $PASSES (${passPss.last()} kB) > 1.1 × przejście 1", passPss.last() <= leakLimit)
    }

    /** Ping głównego wątku co [PING_MS]; zapamiętuje najdłuższe opóźnienie obsługi (proxy ANR). */
    private class MainThreadWatchdog(private val maxLag: AtomicLong) {
        private val handler = Handler(Looper.getMainLooper())

        @Volatile private var running = true
        private val thread = Thread {
            while (running) {
                val posted = SystemClock.elapsedRealtime()
                handler.post { maxLag.accumulateAndGet(SystemClock.elapsedRealtime() - posted, ::maxOf) }
                SystemClock.sleep(PING_MS)
            }
        }

        fun start() = thread.start()

        fun stop() {
            running = false
            thread.join()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private fun samplePssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }

    private fun fillGradient(bmp: Bitmap) {
        val row = IntArray(bmp.width)
        for (y in 0 until bmp.height) {
            for (x in row.indices) {
                val r = x * BYTE / bmp.width
                val g = y * BYTE / bmp.height
                row[x] = OPAQUE or (r shl R_SHIFT) or (g shl G_SHIFT) or ((r + g) / 2)
            }
            bmp.setPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
        }
    }

    private fun toJson(samples: List<Sample>, passPss: LongArray, peak: Long, lag: Long, ektar: Boolean) =
        JSONObject().apply {
            put("bitmap", "${WIDTH}x$HEIGHT")
            put("passes", PASSES)
            put("ektarBundled", ektar)
            put("peakPssKb", peak)
            put("passPssKb", JSONArray(passPss.toList()))
            put("maxMainThreadLagMs", lag)
            put(
                "films",
                JSONArray(
                    samples.map {
                        JSONObject().put("pass", it.pass).put("film", it.film).put("decodeMs", it.decodeMs)
                            .put("applyMs", it.applyMs).put("thread", it.thread).put("pssKb", it.pssKb)
                    }
                )
            )
        }

    private fun writeJson(ctx: Context, json: JSONObject) {
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "perf").apply { mkdirs() }
        File(dir, "film-switch-perf.json").writeText(json.toString(2))
    }

    companion object {
        private const val TAG = "FilmSwitchPerf"
        private const val MAIN = "main"
        const val PASSES = 3
        const val WIDTH = 4000
        const val HEIGHT = 3000
        const val MAX_PEAK_PSS_KB = 400L * 1024
        const val LEAK_FACTOR = 1.10
        const val MAX_DECODE_MS_144 = 2000L
        const val MAX_DECODE_MS_256 = 5000L
        const val MAX_MAIN_BLOCK_MS = 5000L
        private const val PING_MS = 100L
        private const val BYTE = 255
        private const val OPAQUE = -0x1000000
        private const val R_SHIFT = 16
        private const val G_SHIFT = 8
        const val EKTAR = "negative_new/kodak_ektar_100"
        val STARTERS = setOf(
            "bw/ilford_hp_5_plus_400",
            "bw/kodak_tri_x_400",
            "colorslide/fuji_velvia_50",
            "colorslide/kodak_ektachrome_100_vs",
            "colorslide/kodak_kodachrome_64",
            "instant_consumer/polaroid_px_680",
            "negative_new/fuji_400h",
            "negative_new/kodak_portra_400",
            "negative_new/kodak_portra_800",
            "negative_old/fuji_superia_400"
        )
    }
}
