package app.retrofilm.ui

import app.retrofilm.pipeline.FaceBox
import app.retrofilm.pipeline.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Test

/** R: facesFor ścieżka – mapowanie z pełnego kadru do ROI (logika FaceBox.mapIntoRoi). */
class RoiFacesMappingTest {
    @Test fun smartSharpenFacesOnZoomedDetailUseFullPhotoCoordsMapped() {
        // symulacja: twarz na pełnym 1000×800; ROI 400..800 × 200..600
        val full = listOf(FaceBox(0.5f, 0.4f, 0.7f, 0.6f))
        val mapped = full.map { it.mapIntoRoi(PixelRect(400, 200, 800, 600), 1000, 800) }
        // 500→(500-400)/400=0.25; 320→(320-200)/400=0.3
        assertEquals(0.25f, mapped[0].left, 0.001f)
        assertEquals(0.3f, mapped[0].top, 0.001f)
        assertEquals(0.75f, mapped[0].right, 0.001f)
        assertEquals(0.7f, mapped[0].bottom, 0.001f)
    }
}
