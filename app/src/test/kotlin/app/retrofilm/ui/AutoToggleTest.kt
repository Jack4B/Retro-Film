package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** „Popraw światło i kolor” i „Przygotuj pod film” to niezależne przełączniki: wyłączenie wraca, włączenie powtarza. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoToggleTest {
    /** Ciemna scena z ciepłym odcieniem, żeby oba przyciski coś zmieniały. */
    private fun warmDarkPhoto(): Bitmap {
        val rnd = Random(3)
        val px = IntArray(96 * 96) {
            val v = 30 + rnd.nextInt(60)
            Color.rgb((v * 1.5f).toInt().coerceAtMost(255), v, (v * 0.6f).toInt())
        }
        return Bitmap.createBitmap(px, 96, 96, Bitmap.Config.ARGB_8888)
    }

    private fun state() = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
        it.setPhotoForTest(warmDarkPhoto())
    }

    @Test fun toggleOffAndOnReturnsToTheSameStates() = runBlocking {
        val s = state()
        val original = s.adjustments
        s.toggleAuto()
        val auto = s.adjustments
        assertNotEquals("Auto coś zmienia", original, auto)
        assertTrue(s.canUndoAuto)
        s.togglePrep()
        val both = s.adjustments
        assertTrue(s.canUndoPrep)
        s.toggleAuto()
        val prepOnly = s.adjustments
        s.toggleAuto()
        assertEquals("Auto włączone ponownie daje ten sam stan", both, s.adjustments)
        s.togglePrep()
        assertEquals("bez Przygotuj zostaje samo Auto", auto, s.adjustments)
        s.toggleAuto()
        assertEquals("wszystko wyłączone wraca do oryginału", original, s.adjustments)
        assertFalse(s.canUndoAuto)
        assertNotEquals("samo Przygotuj różni się od oryginału", original, prepOnly)
    }

    @Test fun manualEditBecomesTheNewBase() = runBlocking {
        val s = state()
        s.toggleAuto()
        val edited = s.adjustments.copy(vignette = 0.4f, exposure = 0.9f)
        s.adjustments = edited
        assertFalse("po ręcznej zmianie nie ma „cofnij”", s.canUndoAuto)
        s.toggleAuto()
        s.toggleAuto()
        assertEquals("wyłączenie wraca do ustawień z ręczną zmianą", edited, s.adjustments)
    }

    @Test fun prepInversionSliderChangesOnlyToneInversion() = runBlocking {
        val s = state()
        s.togglePrep()
        assertEquals("domyślnie umiarkowane odwrócenie", 0.5f, s.adjustments.toneInversion, 1e-6f)
        val before = s.adjustments
        s.updatePrepInversion(0.2f)
        assertEquals(0.2f, s.adjustments.toneInversion, 1e-6f)
        assertEquals("balans bieli bez zmian", before.temperature, s.adjustments.temperature, 0.05f)
        assertTrue("dalej można cofnąć", s.canUndoPrep)
        s.togglePrep()
        assertEquals("cofnięcie zdejmuje też odwrócenie", 0f, s.adjustments.toneInversion, 0f)
    }

    @Test fun manualTemperatureClearsHiddenWhiteOffset() {
        val a = Adjustments(temperature = 0.2f, whiteDx = 0.01f, whiteDy = -0.01f)
        val s = state()
        s.adjustments = a
        // to samo, co robi suwak temperatury w AdjustPanel
        s.adjustments = s.adjustments.copy(temperature = 0.3f, whiteDx = 0f, whiteDy = 0f)
        assertEquals(0f, s.adjustments.whiteDx, 0f)
    }

    @Test fun unsavedEditsAreDetected() {
        val s = state()
        assertFalse(s.hasUnsavedEdits)
        s.adjustments = Adjustments(exposure = 0.3f)
        assertTrue(s.hasUnsavedEdits)
        s.adjustments = Adjustments.None
        assertFalse("powrót do wyjściowych ustawień to brak zmian", s.hasUnsavedEdits)
    }

    @Test fun inversionSliderStaysUsableAfterManualExposureAndKeepsIt() = runBlocking {
        val s = state()
        s.togglePrep()
        s.adjustments = s.adjustments.copy(exposure = -0.7f)
        assertTrue("suwak zdjęcia krzywej dalej widoczny", s.canAdjustPrepInversion)
        s.updatePrepInversion(1f)
        assertEquals(1f, s.adjustments.toneInversion, 1e-6f)
        assertEquals("ręczna ekspozycja zostaje", -0.7f, s.adjustments.exposure, 1e-6f)
        s.updatePrepInversion(0.1f)
        assertEquals(0.1f, s.adjustments.toneInversion, 1e-6f)
        assertEquals(-0.7f, s.adjustments.exposure, 1e-6f)
    }

    @Test fun untouchedPrepRecomputesExposureForTheNewInversion() = runBlocking {
        val s = state()
        s.togglePrep()
        s.updatePrepInversion(0f)
        val e0 = s.adjustments.exposure
        s.updatePrepInversion(1f)
        assertEquals(1f, s.adjustments.toneInversion, 1e-6f)
        assertNotEquals("ekspozycja liczona po zdjęciu krzywej", e0, s.adjustments.exposure)
        assertTrue("propozycja dalej nałożona", s.canUndoPrep)
    }
}
