package app.retrofilm.ui

import android.graphics.Bitmap
import app.retrofilm.pipeline.Adjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zmiana filmu nie powtarza korekt; zmiana korekt albo zdjęcia – tak. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewStagesTest {
    private fun bmp() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun filmSwitchReusesCorrections() = runBlocking {
        val s = PreviewStages()
        val src = bmp()
        val adj = Adjustments.None
        val first = s.corrected(src, adj, true) { bmp() }
        var looks = 0
        for (film in listOf("a", "b", "a")) {
            assertSame(first, s.corrected(src, adj, true) { bmp() })
            s.look(first, PreviewStages.LookKey(film, 1f, adj.finishing())) {
                looks++
                bmp()
            }
        }
        assertEquals(1, s.correctRuns)
        assertEquals(3, looks)
        s.corrected(src, adj.copy(exposure = 0.5f), true) { bmp() }
        s.corrected(bmp(), adj.copy(exposure = 0.5f), true) { bmp() }
        assertEquals(3, s.correctRuns)
    }
}
