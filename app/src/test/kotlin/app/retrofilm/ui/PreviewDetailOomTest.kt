package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import app.retrofilm.engine.AppGraph
import app.retrofilm.perf.CrashLog
import app.retrofilm.perf.PerfLog
import app.retrofilm.pipeline.Adjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * N: regresja crashu z realme – Przygotuj pod film → film → Wyostrzenie → pinch-zoom (≥1,5×)
 * włącza podgląd 2560 px; na ciasnym heapie to dawało OutOfMemoryError i zabijało proces,
 * bo refreshPreview (w przeciwieństwie do eksportu) nie łapał OOM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewDetailOomTest {
    @Before
    fun setUp() {
        PerfLog.ring.clear()
        CrashLog.install(RuntimeEnvironment.getApplication())
    }

    private fun state(): MainState {
        val bmp = Bitmap.createBitmap(
            IntArray(64 * 48) { Color.rgb(80, 90, 100) },
            64,
            48,
            Bitmap.Config.ARGB_8888
        )
        return MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
            it.setPhotoForTest(bmp)
            // Scenariusz użytkownika: przygotowanie + wyostrzanie świadome treści
            it.adjustments = Adjustments(exposure = 0.3f, toneInversion = 0.5f, sharpness = 0.6f, sharpSmart = true)
        }
    }

    @Test
    fun detailPreviewOomFallsBackToNormalAndLogsWithoutCrashing() = runBlocking {
        val s = state()
        s.refreshPreview()
        assertNotNull(s.preview)
        s.updateDetail(true)
        assertTrue(s.previewDetail)
        // Symulacja OOM przy pierwszym renderze szczegółowym (telefon: 2560 + sharpen + float)
        s.previewBomb = { throw OutOfMemoryError("simulated detail preview") }
        s.refreshPreview() // nie może wyjść poza funkcję
        assertFalse("po OOM wracamy z trybu szczegółowego", s.previewDetail)
        assertNotNull("podgląd 1280 nadal dostępny", s.preview)
        val logged = PerfLog.ring.entries().any { it.stage == "save_error" && it.note.contains("preview") }
        assertTrue("CrashLog.record(preview) trafia do logu jak last_crash/save_error", logged)
        // drugi raz bez bomby – tryb szczegółowy znów można włączyć
        s.previewBomb = null
        s.updateDetail(true)
        s.refreshPreview()
        assertTrue(s.previewDetail)
        assertNotNull(s.preview)
    }

    @Test
    fun switchingToDetailClearsStageCacheSoResolutionsDoNotStack() {
        val stages = PreviewStages()
        val small = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        stages.corrected(small, Adjustments.None, true) { small }
        assertEquals(1, stages.correctRuns)
        stages.clear()
        stages.corrected(small, Adjustments.None, true) { small }
        assertEquals("po clear etapy liczą się od zera (zwalniają pamięć 1280 przed 2560)", 2, stages.correctRuns)
    }

    @Test
    fun detailAndFallbackBothOomKeepProcessAlive() = runBlocking {
        val s = state()
        s.refreshPreview()
        val previous = s.preview
        s.updateDetail(true)
        var hits = 0
        s.previewBomb = {
            hits++
            throw OutOfMemoryError("always")
        }
        s.refreshPreview()
        assertFalse(s.previewDetail)
        assertEquals("bomba: detail + jedna próba 1280", 2, hits)
        assertNotNull("zostaje wcześniejszy podgląd", previous)
        assertEquals(previous, s.preview)
    }
}
