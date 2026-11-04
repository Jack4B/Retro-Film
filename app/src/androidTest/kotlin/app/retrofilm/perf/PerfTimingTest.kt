package app.retrofilm.perf

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.PhotoLoader
import app.retrofilm.ui.IDENTITY_ID
import app.retrofilm.ui.MainState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pomiar czasu (nie mikrobenchmark): nałożenie filmu na podgląd i wygenerowanie siatki 12 miniatur filmów –
 * ścieżki, które użytkownik odczuwa. Ten sam plik jest uruchamiany na gałęzi bazowej (main) i na nowej, wyniki
 * w logcat pod tagiem `RetroPerf` i w statusie instrumentacji. Asercja tylko na sensowność (bez progu czasu).
 */
@RunWith(AndroidJUnit4::class)
class PerfTimingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun photo(): Bitmap {
        val w = 4000
        val h = 3000
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x / 7 xor y / 5) and 0xFF)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun report(name: String, ms: Long) {
        Log.i(TAG, "$name=$ms ms")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putLong(name, ms) })
    }

    @Test
    fun filmApplyAndThumbnailGrid() = runBlocking {
        val graph = AppGraph(context)
        val engine = graph.filmEngine
        val films = engine.availableLuts().take(GRID)
        assertTrue(films.isNotEmpty())
        val big = photo()
        var t = System.nanoTime()
        val preview = MainState.scaleDown(big, PREVIEW)
        report("preview_scale_ms", (System.nanoTime() - t) / NS)

        // siatka miniatur (zimny cache LUT): skala 160 px + LUT + potok jak w FilmThumbnails
        t = System.nanoTime()
        val thumbSrc = MainState.scaleDown(preview, THUMB)
        for (f in films) {
            val lut = engine.load(f.id)
            ExportPipeline.render(thumbSrc, lut, engine.renderer, FrameOptions(), Typeface.DEFAULT)
        }
        report("thumb_grid_${films.size}_ms", (System.nanoTime() - t) / NS)

        // nałożenie filmu na podgląd: 3 ostatnie filmy (LUT już w LRU), mediana
        val times = films.takeLast(APPLY).map { f ->
            val lut = engine.load(f.id)
            val s = System.nanoTime()
            ExportPipeline.render(preview, lut, engine.renderer, FrameOptions(), Typeface.DEFAULT)
            (System.nanoTime() - s) / NS
        }.sorted()
        report("film_apply_median_ms", times[times.size / 2])

        // dotknięcie filmu w nowym potoku: korekty liczone raz na zdjęcie, potem tylko LUT + wykończenie + ramka
        val corrected = ExportPipeline.correct(preview, Adjustments.None, true)
        val taps = films.takeLast(APPLY).map { f ->
            val lut = engine.load(f.id)
            val s = System.nanoTime()
            val looked = ExportPipeline.look(corrected, lut, engine.renderer, 1f, Adjustments.None)
            FrameRenderer.render(looked, FrameOptions(), Typeface.DEFAULT)
            (System.nanoTime() - s) / NS
        }.sorted()
        report("film_tap_cached_median_ms", taps[taps.size / 2])
        val q = System.nanoTime()
        ExportPipeline.look(
            MainState.scaleDown(corrected, QUICK),
            engine.load(films.first().id),
            engine.renderer,
            1f,
            Adjustments.None
        )
        report("film_tap_quick512_ms", (System.nanoTime() - q) / NS)
    }

    /** Pierwsze otwarcie zdjęcia 12 MP: stara vs nowa ścieżka. */
    @Test
    fun firstOpen(): Unit = runBlocking {
        val file = File(context.cacheDir, "first-open.jpg")
        file.outputStream().use {
            photo().compress(Bitmap.CompressFormat.JPEG, JPEG_Q, it)
            it.write(ByteArray(MOTION_TAIL)) // jak Motion Photo: ~9 MB wideo doklejone za JPEG-iem
        }
        val open = { file.inputStream() }
        var t = System.nanoTime()
        val old = PhotoLoader.load(open, PhotoLoader.DEFAULT_MAX_SIDE)
        report("open_decode_4096_ms", (System.nanoTime() - t) / NS)
        t = System.nanoTime()
        PhotoLoader.thumbnail(open)
        report("open_thumbnail_ms", (System.nanoTime() - t) / NS)
        t = System.nanoTime()
        val loaded = PhotoLoader.open(open, PhotoLoader.PREVIEW_MAX_SIDE)
        report("open_decode_preview_ms", (System.nanoTime() - t) / NS)
        assertTrue(loaded.sourceWidth == old.width && loaded.bitmap.width <= old.width)
        val engine = AppGraph(context).filmEngine
        t = System.nanoTime()
        val small = MainState.scaleDown(loaded.bitmap, PREVIEW)
        val lut = checkNotNull(engine.load(engine.availableLuts().first { it.id != IDENTITY_ID }.id))
        engine.renderer.apply(small, lut, 1f)
        report("open_first_preview_cold_ms", (System.nanoTime() - t) / NS)
        file.delete()
    }

    private companion object {
        const val TAG = "RetroPerf"
        const val JPEG_Q = 90
        const val MOTION_TAIL = 9 * 1024 * 1024
        const val GRID = 12
        const val APPLY = 3
        const val PREVIEW = 1280
        const val THUMB = 160
        const val QUICK = 512
        const val NS = 1_000_000L
    }
}
