package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        assertEquals(ComposeZoomLayer(1f, 0f, 0f), composeZoomLayer(true, 3f, 10f, -5f))
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

    @Test fun framedDetailZoomDoesNotActivateRoiAndKeepsFullComposite() = runBlocking {
        val s = state(1200, 900)
        s.frame = s.frame.copy(enabled = true, margin = 0.08f)
        s.refreshPreview()
        val framed = s.preview!!
        assertTrue("ramka powiększa płótno", framed.width > 1200 || framed.height > 900)
        s.updateDetail(true)
        s.updateZoomViewport(scale = 3f, offsetX = 40f, offsetY = -20f, viewW = 400, viewH = 300)
        s.refreshPreview()
        assertFalse("P: ROI off when frame on", s.previewRoiActive)
        val p = s.preview!!
        // pełna kompozycja z marginesami ramki – nie wycinek ROI (O regresja: p << framed)
        assertEquals("szerokość z ramką zachowana", framed.width, p.width)
        assertEquals("wysokość z ramką zachowana", framed.height, p.height)
        // Compose nakłada pinch na bitmapę z ramką (nie identity)
        assertEquals(ComposeZoomLayer(3f, 40f, -20f), composeZoomLayer(s.previewRoiActive, 3f, 40f, -20f))
        // margines ramki: narożniki płótna to tło ramki, nie piksele zdjęcia (szare 90,100,110)
        val corner = p.getPixel(2, 2)
        val photoPx = Color.rgb(90, 100, 110)
        assertTrue(
            "narożnik to margines ramki, nie wykadrowane zdjęcie",
            corner != photoPx
        )
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
        val p = s.preview!!
        assertTrue(p.width * p.height < 1200 * 900 / 2)
    }

    @Test fun composeZoomLayerIdentityOnlyWhenRoiActive() {
        assertEquals(ComposeZoomLayer(1f, 0f, 0f), composeZoomLayer(true, 2.5f, 8f, 9f))
        assertEquals(ComposeZoomLayer(2.5f, 8f, 9f), composeZoomLayer(false, 2.5f, 8f, 9f))
    }
}
