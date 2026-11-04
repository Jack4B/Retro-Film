package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import app.retrofilm.engine.AppGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Siatka miniatur: powrót do zdjęcia z serii nie generuje miniatur od nowa (LRU ostatnich zdjęć). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilmThumbnailsTest {
    @Test
    fun switchingBackReusesThumbnails() = runBlocking {
        var renders = 0
        val t = FilmThumbnails(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT)
        t.renderOverride = { _, p ->
            renders++
            p
        }
        val photo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        t.switchTo("a")
        listOf("f1", "f2", "f3").forEach { t.ensure(it, photo) }
        t.switchTo("b")
        assertTrue(t.bitmaps.isEmpty())
        t.ensure("f1", photo)
        t.switchTo("a")
        listOf("f1", "f2", "f3").forEach { t.ensure(it, photo) }
        assertEquals("wróciło 3 bez renderu", 4, renders)
        assertEquals(3, t.bitmaps.size)
    }
}
