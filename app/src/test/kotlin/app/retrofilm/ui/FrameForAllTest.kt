package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.FrameOptions
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

/** „Ramka do wszystkich zdjęć”: wspólny wygląd ramki, podpisy osobne, przy edycji pojedynczych zdjęć. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameForAllTest {
    private fun state() = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
        it.setSeriesForTest(List(3) { i -> Uri.parse("content://test/$i") })
        it.setPhotoForTest(Bitmap.createBitmap(IntArray(16 * 16) { Color.GRAY }, 16, 16, Bitmap.Config.ARGB_8888))
    }

    /** Wczytanie pliku z content:// nie działa w Robolectric (wyjątek), ale indeks i ustawienia są już przełączone. */
    private fun switchTo(s: MainState, i: Int) {
        runCatching { runBlocking { s.select(RuntimeEnvironment.getApplication(), i) } }
    }

    @Test fun styleIsSharedAndOnlyTheSignatureStaysPerPhoto() {
        val s = state()
        s.setFrameForAll(true)
        s.frame = s.frame.copy(enabled = true, margin = 0.1f)
        assertFalse("zmiana wyglądu ramki nie jest edycją zdjęcia", s.individual)
        switchTo(s, 1)
        assertTrue("wygląd ramki widać na innym zdjęciu", s.frame.enabled)
        assertEquals(0.1f, s.frame.margin, 0f)
        assertFalse(s.individual)
        s.frame = s.frame.copy(signatureEnabled = true, signature = "Tatry")
        assertTrue("podpis to edycja tego zdjęcia", s.individual)
        switchTo(s, 0)
        assertEquals("zdjęcie 1 bez podpisu", "", s.frame.signature)
        assertTrue("ale z tym samym wyglądem ramki", s.frame.enabled && s.frame.signatureEnabled)
        switchTo(s, 1)
        assertEquals("Tatry", s.frame.signature)
        assertTrue(s.hasUnsavedEdits)
    }

    @Test fun turningItOnMakesTheCurrentFrameTheFrameOfAllPhotos() {
        val s = state()
        s.frame = FrameOptions(enabled = true, signatureEnabled = true, signature = "Tatry")
        s.setFrameForAll(true)
        switchTo(s, 1)
        assertTrue(s.frame.enabled && s.frame.signatureEnabled)
        assertEquals("", s.frame.signature)
        switchTo(s, 0)
        assertEquals("Tatry", s.frame.signature)
    }

    @Test fun turningItOffGivesEveryPhotoItsOwnFrameStartingFromTheSharedLook() {
        val s = state()
        s.setFrameForAll(true)
        s.frame = s.frame.copy(enabled = true, signatureEnabled = true, signature = "A")
        switchTo(s, 1)
        s.setFrameForAll(false)
        assertFalse(s.seriesFrame.forAll)
        assertTrue("zdjęcie 2 zachowuje wygląd", s.frame.enabled)
        s.frame = s.frame.copy(enabled = false)
        switchTo(s, 0)
        assertTrue(s.frame.enabled)
        assertEquals("A", s.frame.signature)
        switchTo(s, 2)
        assertTrue("zdjęcie 3 też zaczyna od wspólnego wyglądu", s.frame.enabled)
        assertEquals("", s.frame.signature)
        switchTo(s, 1)
        assertFalse("własna zmiana zdjęcia 2 zostaje", s.frame.enabled)
    }

    @Test fun switchingTheModeWithoutChangesLeavesNothingUnsaved() {
        val s = state()
        s.setFrameForAll(true)
        s.setFrameForAll(false)
        assertFalse(s.hasUnsavedEdits)
    }
}
