package app.retrofilm.core.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameGeometryTest {
    @Test fun equalMarginsWithoutSignature() {
        val l = FrameGeometry.layout(SizeF(1000f, 1000f), SizeF(1000f, 1000f), 0.05f, signature = false)
        assertEquals(50f, l.photoArea.left, 0.01f)
        assertEquals(900f, l.photoArea.height, 0.01f)
        assertNull(l.signatureBand)
    }

    @Test fun signatureBandIsBottom20Percent() {
        val l = FrameGeometry.layout(SizeF(1000f, 1000f), SizeF(1000f, 1000f), 0.05f, signature = true)
        assertEquals(800f, l.signatureBand!!.top, 0.01f)
        assertEquals(750f, l.photoArea.height, 0.01f)
        assertEquals(75f, l.signatureBaseSize, 0.01f)
    }

    @Test fun photoIsContained() {
        val l = FrameGeometry.layout(SizeF(1000f, 1000f), SizeF(3000f, 2000f), 0.05f, signature = false)
        assertEquals(900f, l.photo.width, 0.01f)
        assertEquals(600f, l.photo.height, 0.01f)
        assertEquals(200f, l.photo.top, 0.01f)
    }

    @Test fun marginIsClampedTo15Percent() {
        val l = FrameGeometry.layout(SizeF(1000f, 1000f), SizeF(1000f, 1000f), 0.5f, signature = false)
        assertEquals(150f, l.photoArea.left, 0.01f)
    }

    @Test fun canvasSizeFollowsAspect() {
        assertEquals(1000f to 1000f, FrameGeometry.canvasSize(FrameAspect.Square, 3f, 2f, 1000f))
        assertEquals(800f to 1000f, FrameGeometry.canvasSize(FrameAspect.Portrait45, 3f, 2f, 1000f))
        val original = FrameGeometry.canvasSize(FrameAspect.Original, 3000f, 2000f, 1500f)
        assertEquals(1000f, original.second, 0.01f)
    }

    @Test fun signatureShrinksThenWraps() {
        assertEquals(1, FrameGeometry.fitSignature(10f, 100f) { it * 5 }.lines)
        val shrunk = FrameGeometry.fitSignature(10f, 100f) { it * 14 }
        assertEquals(1, shrunk.lines)
        val wrapped = FrameGeometry.fitSignature(10f, 100f) { it * 30 }
        assertEquals(2, wrapped.lines)
        assertEquals(6f, wrapped.sizePx, 0.01f)
    }
}
