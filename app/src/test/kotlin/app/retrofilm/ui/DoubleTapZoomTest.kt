package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
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

/** Q: dwukrotne dotknięcie ląduje na jednej skali – bez skoku 2,5×→ROI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoubleTapZoomTest {
    private fun zoom() = ZoomState().apply { size = IntSize(1000, 800) }

    @Test fun awaitingRoiUsesIdentityUntilRoiReady() {
        val z = zoom()
        z.toggle(Offset(300f, 300f))
        assertEquals(ZoomState.DOUBLE_TAP_SCALE, z.scale, 0.001f)
        // zanim ROI: identity (nie pokazuj 2,5× na pełnym)
        assertEquals(
            ComposeZoomLayer(1f, 0f, 0f),
            composeZoomLayer(roiActive = false, scale = z.scale, offsetX = 1f, offsetY = 2f, awaitingRoi = true)
        )
        // ROI gotowe na bazie double-tap: identity; dalszy pinch = względny (RoiLiveZoomTest)
        assertEquals(
            ComposeZoomLayer(1f, 0f, 0f),
            composeZoomLayer(
                roiActive = true,
                scale = z.scale,
                offsetX = 1f,
                offsetY = 2f,
                awaitingRoi = false,
                roiBaseScale = z.scale,
                roiBaseOffsetX = 1f,
                roiBaseOffsetY = 2f
            )
        )
    }

    @Test fun framedDoubleTapKeepsComposeScaleAfterPreviewRebuild() = runBlocking {
        val s = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
            it.setPhotoForTest(
                Bitmap.createBitmap(IntArray(800 * 600) { Color.GRAY }, 800, 600, Bitmap.Config.ARGB_8888)
            )
            it.frame = it.frame.copy(enabled = true, margin = 0.08f)
            it.adjustments = Adjustments(sharpness = 0.5f)
        }
        s.refreshPreview()
        val before = s.preview!!
        s.updateDetail(true)
        s.updateZoomViewport(ZoomState.DOUBLE_TAP_SCALE, 0f, 0f, 400, 300)
        s.refreshPreview()
        assertFalse(s.previewRoiActive)
        val after = s.preview!!
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        // z ramką: Compose od razu 2,5× (awaitingRoi=false)
        assertEquals(
            ComposeZoomLayer(ZoomState.DOUBLE_TAP_SCALE, 12f, -8f),
            composeZoomLayer(false, ZoomState.DOUBLE_TAP_SCALE, 12f, -8f, awaitingRoi = false)
        )
    }

    @Test fun framelessRoiReadyUsesIdentityLayer() = runBlocking {
        val s = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
            it.setPhotoForTest(
                Bitmap.createBitmap(IntArray(1200 * 900) { Color.GRAY }, 1200, 900, Bitmap.Config.ARGB_8888)
            )
            it.adjustments = Adjustments(sharpness = 0.6f, sharpSmart = true)
        }
        val z = zoom()
        z.toggle(Offset(200f, 200f))
        s.updateDetail(true)
        s.updateZoomViewport(z.scale, z.offset.x, z.offset.y, z.size.width, z.size.height)
        s.refreshPreview()
        assertTrue(s.previewRoiActive)
        assertEquals(
            ComposeZoomLayer(1f, 0f, 0f),
            composeZoomLayer(
                s.previewRoiActive,
                z.scale,
                z.offset.x,
                z.offset.y,
                awaitingRoi = false,
                roiBaseScale = s.previewRoiBaseScale,
                roiBaseOffsetX = s.previewRoiBaseOffsetX,
                roiBaseOffsetY = s.previewRoiBaseOffsetY
            )
        )
        // gesty nadal mają scale>1 – viewport może się odświeżyć (pan/pinch po ROI)
        assertTrue(z.scale > 1f)
    }

    @Test fun settleForRoiBaseKeepsDetailWithoutComposeScale() {
        val z = zoom()
        z.toggle(Offset(300f, 300f))
        z.settleForRoiBase()
        assertEquals(1f, z.scale, 0f)
        assertTrue(z.isDetailed)
        z.toggle(Offset(300f, 300f))
        assertFalse(z.isDetailed)
    }
}
