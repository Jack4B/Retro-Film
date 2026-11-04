package app.retrofilm.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** I: postęp pojedynczego zapisu – kafle potoku wypełniają przedział, wartość nigdy się nie cofa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveProgressTest {
    @Test fun tilesFillRangeMonotonically() {
        SaveProgress.reset()
        SaveProgress.range(0.2f, 0.8f)
        SaveProgress.tiles(1, 4)
        assertEquals(0.35f, SaveProgress.value, 1e-4f)
        SaveProgress.mark(0.1f)
        assertEquals(0.35f, SaveProgress.value, 1e-4f)
        SaveProgress.close()
        assertEquals(0.8f, SaveProgress.value, 1e-4f)
        SaveProgress.tiles(1, 1) // poza zapisem: bez zmian
        assertEquals(0.8f, SaveProgress.value, 1e-4f)
    }

    @Test fun pipelineReportsTiles() {
        SaveProgress.reset()
        SaveProgress.range(0f, 1f)
        val src = Bitmap.createBitmap(IntArray(400 * 600) { -0x7f7f80 }, 400, 600, Bitmap.Config.ARGB_8888)
        StripPipeline.run(src, Adjustments(exposure = 0.3f), io = StripIo(budget = 1))
        assertTrue("${SaveProgress.value}", SaveProgress.value > 0.99f)
        SaveProgress.reset()
    }
}
