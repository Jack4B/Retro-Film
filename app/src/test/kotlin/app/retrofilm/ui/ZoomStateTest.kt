package app.retrofilm.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    private fun zoomState() = ZoomState().apply { size = IntSize(1000, 800) }

    @Test fun startsUnzoomedAndClampsScale() {
        val z = zoomState()
        assertEquals(1f, z.scale, 0f)
        z.transform(Offset(500f, 400f), Offset.Zero, 100f)
        assertEquals(ZoomState.MAX_SCALE, z.scale, 0f)
        z.transform(Offset(500f, 400f), Offset.Zero, 0.0001f)
        assertEquals(1f, z.scale, 0f)
        assertEquals(Offset.Zero, z.offset)
    }

    @Test fun zoomKeepsCentroidPointFixed() {
        val z = zoomState()
        val c = Offset(800f, 200f)
        z.transform(c, Offset.Zero, 2f)
        // punkt zawartości pod palcem przed i po przybliżeniu ma być ten sam
        val center = Offset(500f, 400f)
        val contentBefore = c - center
        val contentAfter = (c - center - z.offset) / z.scale
        assertEquals(contentBefore.x, contentAfter.x, 0.01f)
        assertEquals(contentBefore.y, contentAfter.y, 0.01f)
    }

    @Test fun panIsClampedToImageEdges() {
        val z = zoomState()
        z.transform(Offset(500f, 400f), Offset.Zero, 2f)
        z.transform(Offset(500f, 400f), Offset(10_000f, -10_000f), 1f)
        assertEquals(500f, z.offset.x, 0.01f)
        assertEquals(-400f, z.offset.y, 0.01f)
    }

    @Test fun doubleTapTogglesAndResets() {
        val z = zoomState()
        z.toggle(Offset(300f, 300f))
        assertEquals(ZoomState.DOUBLE_TAP_SCALE, z.scale, 0.001f)
        assertTrue(z.isDetailed)
        z.toggle(Offset(300f, 300f))
        assertEquals(1f, z.scale, 0f)
        assertEquals(Offset.Zero, z.offset)
        assertFalse(z.isDetailed)
    }
}
