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

/**
 * Q: dwukrotne dotknięcie ląduje na jednej skali – bez drugiego skoku po przebudowie ROI / 2560.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoubleTapZoomTest {
    private fun zoom() = ZoomState().apply { size = IntSize(1000, 800) }

    @Test fun settleForRoiBaseKeepsDetailWithoutComposeScale() {
        val z = zoom()
        z.toggle(Offset(300f, 300f))
        assertEquals(ZoomState.DOUBLE_TAP_SCALE, z.scale, 0.001f)
        assertTrue(z.isDetailed)
        z.settleForRoiBase()
        assertEquals(1f, z.scale, 0f)
        assertEquals(Offset.Zero, z.offset)
        assertTrue("detailMode trzyma isDetailed po settle (inaczej LaunchedEffect wyłączy ROI)", z.isDetailed)
        assertEquals(ComposeZoomLayer(1f, 0f, 0f), composeZoomLayer(true, z.scale, z.offset.x, z.offset.y))
        z.toggle(Offset(300f, 300f))
        assertEquals(1f, z.scale, 0f)
        assertFalse(z.isDetailed)
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
        // Q: z ramką bez skoku 1280→2560 – ten sam bok podglądu (Compose zoom = jedyny zoom)
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        assertEquals(
            ComposeZoomLayer(ZoomState.DOUBLE_TAP_SCALE, 12f, -8f),
            composeZoomLayer(s.previewRoiActive, ZoomState.DOUBLE_TAP_SCALE, 12f, -8f)
        )
    }

    @Test fun framelessRoiSettleEndsAtIdentityLayer() = runBlocking {
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
        z.settleForRoiBase()
        assertEquals(1f, z.scale, 0f)
        assertTrue(z.isDetailed)
        assertEquals(ComposeZoomLayer(1f, 0f, 0f), composeZoomLayer(s.previewRoiActive, z.scale, 0f, 0f))
    }
}
