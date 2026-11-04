package app.retrofilm.perf

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.AutoContext
import app.retrofilm.pipeline.AutoEnhance
import app.retrofilm.pipeline.FilmCalibration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Automatyczna korekta na urządzeniu: czas `suggest()` na podglądzie (próg 10 ms, mediana z ciepłych przebiegów)
 * i kalibracja szarości (rampa przez LUT) dla każdego dostępnego filmu. Wyniki w logcat (`RetroFilmPerf`).
 */
@RunWith(AndroidJUnit4::class)
class AutoSuggestTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun report(name: String, value: String) {
        Log.i(TAG, "$name $value")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString(name, value) })
    }

    @Test
    fun suggestOnPreviewTakesUnder10ms() {
        val w = PREVIEW_W
        val h = PREVIEW_H
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x / 7 xor y / 5) and 0xFF)
        }
        repeat(WARMUP) { AutoEnhance.suggest(px, w, h, emptyList(), AutoContext()) }
        val times = LongArray(RUNS) {
            val t = System.nanoTime()
            AutoEnhance.suggest(px, w, h, emptyList(), AutoContext(0.2f, 1f, -0.5f))
            (System.nanoTime() - t) / NS_PER_US
        }.sorted()
        val median = times[RUNS / 2] / 1000.0
        report("auto_suggest_${w}x$h", "median=%.2f ms max=%.2f ms".format(median, times.last() / 1000.0))
        assertTrue("suggest() mediana $median ms ≥ 10 ms", median < LIMIT_MS)
    }

    @Test
    fun everyFilmHasACalibratedGrey() {
        val engine = AppGraph(context).filmEngine
        val films = engine.availableLuts()
        assertTrue(films.isNotEmpty())
        for (f in films) {
            val lut = engine.load(f.id) ?: continue
            val t = System.nanoTime()
            val c = FilmCalibration.of(lut)
            val us = (System.nanoTime() - t) / NS_PER_US
            report("film_grey_${f.id}", "target=%.3f shoulder=%.3f (%d µs)".format(c.targetGrey, c.shoulder, us))
            assertTrue(f.id, c.targetGrey in FilmCalibration.MIN_TARGET..FilmCalibration.MAX_TARGET)
            assertTrue(f.id, c.shoulder > c.targetGrey)
        }
    }

    private companion object {
        const val TAG = "RetroFilmPerf"
        const val PREVIEW_W = 1280
        const val PREVIEW_H = 960
        const val WARMUP = 5
        const val RUNS = 15
        const val NS_PER_US = 1_000L
        const val LIMIT_MS = 10.0
    }
}
