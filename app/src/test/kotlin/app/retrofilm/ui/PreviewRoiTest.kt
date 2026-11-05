package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * O/P: ROI przy zoomie bez ramki (OOM); z włączoną ramką ROI wyłączone – zoom Compose na zdjęciu+ramce.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewRoiTest {
    private fun photo(w: Int, h: Int) = Bitmap.createBitmap(
        IntArray(w * h) { Color.rgb(90, 100, 110) },
        w,
        h,
        Bitmap.Config.ARGB_8888
    )

    private fun state(w: Int = 800, h: Int = 600) =
        MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
            it.setPhotoForTest(photo(w, h))
            it.adjustments = Adjustments(toneInversion = 0.5f, sharpness = 0.7f, sharpSmart = true)
        }

    @Test fun detailZoomUsesRoiSmallerThanFullPhoto() = runBlocking {
        val s = state(1200, 900)
        s.refreshPreview()
        s.updateDetail(true)
        s.updateZoomViewport(scale = 3f, offsetX = 0f, offsetY = 0f, viewW = 400, viewH = 300)
        s.refreshPreview()
        assertTrue(s.previewRoiActive)
        val p = s.preview!!
        assertNotNull(p)
        assertTrue("ROI ${p.width}x${p.height}", p.width * p.height < 1200 * 900 / 2)
    }

    @Test fun leavingDetailClearsRoiFlag() = runBlocking {
        val s = state()
        s.updateDetail(true)
        s.updateZoomViewport(2.5f, 0f, 0f, 400, 300)
        s.refreshPreview()
        assertTrue(s.previewRoiActive)
        s.updateDetail(false)
        s.refreshPreview()
        assertFalse(s.previewRoiActive)
    }

    @Test fun framedDetailZoomDoesNotActivateRoi() = runBlocking {
        val s = state(1200, 900)
        s.frame = s.frame.copy(enabled = true, margin = 0.08f)
        s.refreshPreview()
        val framed = s.preview!!
        assertTrue(
            "ramka powiększa płótno względem zdjęcia",
            framed.width > 1200 || framed.height > 900
        )
        s.updateDetail(true)
        s.updateZoomViewport(scale = 3f, offsetX = 0f, offsetY = 0f, viewW = 400, viewH = 300)
        s.refreshPreview()
        assertFalse(
            "P: z ramką ROI wyłączone – zoom Compose na całości (zdjęcie+ramka)",
            s.previewRoiActive
        )
        val p = s.preview!!
        assertTrue("framed preview ${p.width}x${p.height}", p.width >= framed.width / 2)
    }

    @Test fun disablingFrameRestoresRoiOnDetailZoom() = runBlocking {
        val s = state(1200, 900)
        s.frame = s.frame.copy(enabled = true)
        s.updateDetail(true)
        s.updateZoomViewport(3f, 0f, 0f, 400, 300)
        s.refreshPreview()
        assertFalse(s.previewRoiActive)
        s.frame = s.frame.copy(enabled = false)
        s.refreshPreview()
        assertTrue("bez ramki wraca ROI z O", s.previewRoiActive)
    }
}
