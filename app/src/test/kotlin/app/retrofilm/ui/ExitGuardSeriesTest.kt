package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.FrameOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ostrzeżenie przed wyjściem z edycji ma działać też po przesunięciu na inne zdjęcie serii (zgłoszenie z 1.1.7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExitGuardSeriesTest {
    private fun state(count: Int) = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
        if (count > 1) it.setSeriesForTest(List(count) { i -> Uri.parse("content://test/$i") })
        it.setPhotoForTest(Bitmap.createBitmap(IntArray(16 * 16) { Color.GRAY }, 16, 16, Bitmap.Config.ARGB_8888))
    }

    /** Wczytanie pliku z content:// nie działa w Robolectric (wyjątek), ale indeks i ustawienia są już przełączone. */
    private fun switchTo(s: MainState, i: Int) {
        runCatching { runBlocking { s.select(RuntimeEnvironment.getApplication(), i) } }
    }

    @Test fun nothingChangedMeansNoWarning() {
        assertFalse(state(1).hasUnsavedEdits)
        assertFalse(state(3).hasUnsavedEdits)
    }

    @Test fun changeOnSinglePhotoWarns() {
        val s = state(1)
        s.adjustments = Adjustments(exposure = 0.5f)
        assertTrue(s.hasUnsavedEdits)
    }

    @Test fun changeOnSeriesPhotoStillWarnsAfterSwitchingToAnotherPhoto() {
        val s = state(3)
        s.adjustments = Adjustments(exposure = 0.5f)
        assertTrue(s.hasUnsavedEdits)
        switchTo(s, 1)
        assertTrue("zmiana na zdjęciu 1 nie przepadła", s.hasUnsavedEdits)
        assertTrue(s.edits.isIndividual(0))
        assertFalse("zdjęcie 2 bez zmian", s.individual)
    }

    @Test fun switchingBetweenUntouchedPhotosDoesNotWarn() {
        val s = state(3)
        switchTo(s, 1)
        switchTo(s, 2)
        assertFalse(s.hasUnsavedEdits)
    }

    @Test fun undoingTheChangeBeforeLeavingClearsTheWarning() {
        val s = state(3)
        s.adjustments = Adjustments(exposure = 0.5f)
        switchTo(s, 1)
        switchTo(s, 0)
        s.adjustments = Adjustments.None
        assertFalse(s.hasUnsavedEdits)
    }

    private val framed = FrameOptions(enabled = true, signatureEnabled = true, signature = "Tatry")

    @Test fun frameChangeOnOnePhotoStaysWithThatPhotoAndStillWarns() {
        val s = state(3)
        s.frame = framed
        assertTrue("zmiana ramki to edycja tego zdjęcia", s.individual)
        assertTrue(s.hasUnsavedEdits)
        switchTo(s, 1)
        assertTrue("ramka zdjęcia 1 nie przepadła", s.hasUnsavedEdits)
        assertFalse("zdjęcie 2 ma własną, domyślną ramkę", s.frame.enabled)
        assertFalse(s.individual)
        switchTo(s, 0)
        assertTrue("po powrocie ramka i podpis wracają", s.frame == framed)
    }

    @Test fun watermarkIsOneSettingForEveryPhoto() {
        val s = state(3)
        s.frame = s.frame.copy(watermark = true)
        assertFalse("znak wodny nie jest edycją zdjęcia", s.individual)
        switchTo(s, 1)
        assertTrue("znak wodny zostaje na innym zdjęciu", s.frame.watermark)
        assertFalse(s.individual)
    }
}
