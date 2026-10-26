package app.retrofilm.ui

import app.retrofilm.pipeline.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGestureTest {
    private val scale = 0.5f

    @Test fun pinchZoomsWithinLimits() {
        val g = CropGesture.apply(Geometry(), 4000, 3000, CropGesture.Move(0f, 0f, 2f, scale))
        assertEquals(2f, g.zoom, 1e-4f)
        val max = CropGesture.apply(g, 4000, 3000, CropGesture.Move(0f, 0f, 100f, scale))
        assertEquals(Geometry.MAX_ZOOM, max.zoom, 1e-4f)
        val min = CropGesture.apply(g, 4000, 3000, CropGesture.Move(0f, 0f, 0.01f, scale))
        assertEquals(1f, min.zoom, 1e-4f)
    }

    @Test fun dragMovesWindowOppositeToTheFinger() {
        val zoomed = Geometry(zoom = 2f)
        val right = CropGesture.apply(zoomed, 4000, 3000, CropGesture.Move(100f, 0f, 1f, scale))
        assertTrue("palec w prawo → okno kadru w lewo", right.panX < 0f)
        val down = CropGesture.apply(zoomed, 4000, 3000, CropGesture.Move(0f, 100f, 1f, scale))
        assertTrue("palec w dół → okno w górę", down.panY < 0f)
    }

    @Test fun dragIsConvertedToTheRightImageDistance() {
        // zoom 2×: zakres 1000 px; 200 px ekranu = 400 px zdjęcia = 0,4 zakresu
        val g = CropGesture.apply(Geometry(zoom = 2f), 4000, 3000, CropGesture.Move(-200f, 0f, 1f, scale))
        assertEquals(0.4f, g.panX, 0.01f)
    }

    @Test fun cannotDragWithoutZoomAndPanIsClamped() {
        val still = CropGesture.apply(Geometry(), 4000, 3000, CropGesture.Move(300f, 300f, 1f, scale))
        assertEquals(0f, still.panX, 0f)
        assertEquals(0f, still.panY, 0f)
        val far = CropGesture.apply(Geometry(zoom = 2f), 4000, 3000, CropGesture.Move(-99999f, 0f, 1f, scale))
        assertEquals(1f, far.panX, 1e-4f)
    }

    @Test fun invalidScaleKeepsGeometry() {
        val g = Geometry(zoom = 2f, panX = 0.3f)
        assertEquals(g, CropGesture.apply(g, 4000, 3000, CropGesture.Move(10f, 10f, 1.5f, 0f)))
    }

    @Test fun dragWorksWithPerspectiveToo() {
        val g = Geometry(zoom = 2f, perspV = 0.5f)
        val moved = CropGesture.apply(g, 4000, 3000, CropGesture.Move(-100f, 0f, 1f, scale))
        assertTrue(moved.panX > 0f)
    }
}
