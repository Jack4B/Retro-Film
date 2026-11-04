package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.SignatureFont
import app.retrofilm.settings.EditDefaults
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Redesign: Cofnij/Ponów w stanie edytora, zakres „Cała seria | To zdjęcie” i domyślne ustawienia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignStateTest {
    private fun state(count: Int = 3) =
        MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
            it.setSeriesForTest(List(count) { i -> Uri.parse("content://test/$i") })
            it.setPhotoForTest(Bitmap.createBitmap(IntArray(16 * 16) { Color.GRAY }, 16, 16, Bitmap.Config.ARGB_8888))
        }

    private fun switchTo(s: MainState, i: Int) {
        runCatching { runBlocking { s.select(RuntimeEnvironment.getApplication(), i) } }
    }

    @Test fun undoAndRedoRestoreEditorState() {
        val s = state()
        s.recordHistory()
        s.adjustments = Adjustments(exposure = 0.5f)
        s.recordHistory()
        s.intensity = 0.4f
        s.recordHistory()
        s.undo()
        assertEquals(1f, s.intensity, 0f)
        assertEquals(0.5f, s.adjustments.exposure, 0f)
        s.undo()
        assertEquals(Adjustments.None, s.adjustments)
        assertFalse(s.history.canUndo)
        s.redo()
        s.redo()
        assertEquals(0.4f, s.intensity, 0f)
        assertFalse(s.history.canRedo)
    }

    @Test fun recordingAfterUndoIsANoOpUntilSomethingChanges() {
        val s = state()
        s.intensity = 0.3f
        s.recordHistory()
        s.undo()
        s.recordHistory()
        assertTrue("stan po cofnięciu nie kasuje Ponów", s.history.canRedo)
    }

    @Test fun switchingPhotosStartsAFreshHistory() {
        val s = state()
        s.intensity = 0.3f
        s.recordHistory()
        switchTo(s, 1)
        assertFalse(s.history.canUndo)
        assertFalse(s.history.canRedo)
    }

    @Test fun thisPhotoScopeAssignsChangesOnlyToTheCurrentPhoto() {
        val s = state()
        switchTo(s, 1)
        s.adjustments = Adjustments(contrast = -0.6f)
        assertTrue("„To zdjęcie” – zmiana należy do zdjęcia 2", s.individual)
        switchTo(s, 2)
        assertEquals(Adjustments.None, s.adjustments)
        switchTo(s, 1)
        assertEquals(-0.6f, s.adjustments.contrast, 0f)
    }

    @Test fun wholeSeriesScopeSendsChangesToEveryPhoto() {
        val s = state()
        switchTo(s, 1)
        s.adjustments = Adjustments(contrast = -0.6f)
        s.changeScopeAll(true)
        assertFalse(s.individual)
        s.intensity = 0.5f
        switchTo(s, 2)
        assertEquals(-0.6f, s.adjustments.contrast, 0f)
        assertEquals(0.5f, s.intensity, 0f)
        switchTo(s, 0)
        assertEquals(0.5f, s.intensity, 0f)
        for (i in 0..2) assertFalse(s.isIndividual(i))
    }

    @Test fun leavingWholeSeriesScopeKeepsTheSharedLookAsStartingPoint() {
        val s = state()
        s.changeScopeAll(true)
        s.intensity = 0.7f
        s.changeScopeAll(false)
        switchTo(s, 1)
        assertEquals(0.7f, s.intensity, 0f)
        s.intensity = 0.2f
        switchTo(s, 2)
        assertEquals("po wyjściu z „Cała seria” zmiany są osobne", 0.7f, s.intensity, 0f)
    }

    @Test fun defaultsApplyQualitySizeFrameAndSignatureButKeepWatermark() {
        val s = state()
        s.frame = s.frame.copy(watermark = true)
        s.applyDefaults(
            EditDefaults(
                quality = 80,
                maxSide = 2048,
                frameEnabled = true,
                frameAspect = "Square",
                frameMargin = 0.07f,
                signatureEnabled = true,
                signature = "Karesh",
                signatureFont = "MarckScript"
            )
        )
        assertEquals(80, s.exportQuality)
        assertEquals(2048, s.exportMaxSide)
        assertTrue(s.frame.enabled && s.frame.signatureEnabled)
        assertEquals(FrameAspect.Square, s.frame.aspect)
        assertEquals(0.07f, s.frame.margin, 0f)
        assertEquals("Karesh", s.frame.signature)
        assertEquals(SignatureFont.MarckScript, s.frame.font)
        assertTrue("znak wodny to ustawienie aplikacji", s.frame.watermark)
    }

    @Test fun currentSettingsRoundTripThroughDefaults() {
        val s = state()
        s.exportQuality = 88
        s.frame = s.frame.copy(enabled = true, signatureEnabled = true, signature = "Tatry")
        val d = s.currentAsDefaults()
        val t = state()
        t.applyDefaults(d)
        assertEquals(s.frame.copy(watermark = false), t.frame.copy(watermark = false))
        assertEquals(88, t.exportQuality)
    }

    @Test fun unknownEnumNamesFallBackToFactoryValues() {
        val s = state()
        s.applyDefaults(EditDefaults(frameAspect = "Nope", signatureFont = "Gone"))
        assertEquals(FrameAspect.Original, s.frame.aspect)
        assertEquals(SignatureFont.Caveat, s.frame.font)
    }
}
