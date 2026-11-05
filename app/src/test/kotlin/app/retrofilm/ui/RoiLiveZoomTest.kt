package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R: po ROI Compose stosuje gesty względem bazy wycinka – pan/pinch bez czekania na rebuild. */
class RoiLiveZoomTest {
    @Test fun roiActiveAppliesRelativeZoomLive() {
        // baza ROI = 2.5 (double-tap); użytkownik dociąga do 3.75 → layer 1.5× na bitmapie ROI
        val z = composeZoomLayer(
            roiActive = true,
            scale = 3.75f,
            offsetX = 20f,
            offsetY = -10f,
            awaitingRoi = false,
            roiBaseScale = 2.5f,
            roiBaseOffsetX = 0f,
            roiBaseOffsetY = 0f
        )
        assertEquals(1.5f, z.scale, 0.001f)
        assertEquals(20f, z.offsetX, 0.001f)
        assertEquals(-10f, z.offsetY, 0.001f)
    }

    @Test fun roiActiveRelativePanFromBaseOffset() {
        val z = composeZoomLayer(
            true,
            scale = 2.5f,
            offsetX = 30f,
            offsetY = 5f,
            awaitingRoi = false,
            roiBaseScale = 2.5f,
            roiBaseOffsetX = 10f,
            roiBaseOffsetY = 5f
        )
        assertEquals(1f, z.scale, 0.001f)
        assertEquals(20f, z.offsetX, 0.001f)
        assertEquals(0f, z.offsetY, 0.001f)
    }

    @Test fun awaitingRoiStillIdentity() {
        assertEquals(
            ComposeZoomLayer(1f, 0f, 0f),
            composeZoomLayer(false, 2.5f, 5f, 5f, awaitingRoi = true, roiBaseScale = 2.5f)
        )
    }

    @Test fun framedPathUnchangedUsesAbsoluteScale() {
        assertEquals(
            ComposeZoomLayer(2.5f, 8f, 9f),
            composeZoomLayer(false, 2.5f, 8f, 9f, awaitingRoi = false, roiBaseScale = 2.5f)
        )
    }

    @Test fun atRoiBaseLandingIsIdentity() {
        assertEquals(
            ComposeZoomLayer(1f, 0f, 0f),
            composeZoomLayer(true, 2.5f, 12f, -8f, false, 2.5f, 12f, -8f)
        )
    }
}
