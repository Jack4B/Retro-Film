package app.retrofilm.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** J: postęp jest per sesja zapisu – kafle potoku wypełniają przedział danej sesji, wartość się nie cofa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveProgressTest {
    @Test fun tilesFillRangeMonotonically() {
        val p = SaveProgress()
        p.range(0.2f, 0.8f)
        p.tiles(1, 4)
        assertEquals(0.35f, p.value, 1e-4f)
        p.mark(0.1f)
        assertEquals(0.35f, p.value, 1e-4f)
        p.close()
        assertEquals(0.8f, p.value, 1e-4f)
        p.tiles(1, 1) // poza zakresem: bez zmian
        assertEquals(0.8f, p.value, 1e-4f)
    }

    @Test fun sessionsAreIndependent() {
        val a = SaveProgress()
        val b = SaveProgress()
        a.range(0f, 1f)
        a.tiles(1, 2)
        assertEquals(0.5f, a.value, 1e-4f)
        assertEquals(0f, b.value, 0f)
    }

    @Test fun pipelineReportsTilesOnlyToItsSession() {
        val mine = SaveProgress().apply { range(0f, 1f) }
        val other = SaveProgress().apply { range(0f, 1f) }
        val src = Bitmap.createBitmap(IntArray(400 * 600) { -0x7f7f80 }, 400, 600, Bitmap.Config.ARGB_8888)
        StripPipeline.run(src, Adjustments(exposure = 0.3f), io = StripIo(budget = 1, progress = mine))
        StripPipeline.run(src, Adjustments(exposure = 0.3f)) // podgląd bez sesji
        assertTrue("${mine.value}", mine.value > 0.99f)
        assertEquals(0f, other.value, 0f)
    }
}
