package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

/** R: twarze z pełnego kadru mapowane do wycinka ROI (0..1 względem cropu). */
class FaceBoxRoiTest {
    @Test fun mapIntoRoiRewritesNormalizedCoords() {
        // pełne 1000×1000; ROI 200..600 × 100..500; twarz na środku ROI
        val face = FaceBox(0.3f, 0.2f, 0.5f, 0.4f)
        val m = face.mapIntoRoi(PixelRect(200, 100, 600, 500), 1000, 1000)
        // 300/1000 → (300-200)/400 = 0.25; 200/1000 → (200-100)/400 = 0.25
        assertEquals(0.25f, m.left, 0.001f)
        assertEquals(0.25f, m.top, 0.001f)
        assertEquals(0.75f, m.right, 0.001f)
        assertEquals(0.75f, m.bottom, 0.001f)
    }

    @Test fun faceOutsideRoiClampsToEdge() {
        val face = FaceBox(0f, 0f, 0.1f, 0.1f)
        val m = face.mapIntoRoi(PixelRect(500, 500, 900, 900), 1000, 1000)
        assertEquals(0f, m.left, 0f)
        assertEquals(0f, m.top, 0f)
        assertEquals(0f, m.right, 0f)
        assertEquals(0f, m.bottom, 0f)
    }
}
