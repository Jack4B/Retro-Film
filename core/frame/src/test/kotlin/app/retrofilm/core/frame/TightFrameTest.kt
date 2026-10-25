package app.retrofilm.core.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TightFrameTest {
    private val photo = SizeF(4000f, 3000f)

    @Test fun originalCanvasHugsPhotoWithEqualSideMargins() {
        val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 4000f, 3000f, 0.03f, true, 2000f)
        val l = TightFrame.layout(SizeF(w, h), photo, 0.03f, signature = true)
        val left = l.photo.left
        val right = w - l.photo.right
        val top = l.photo.top
        val bottom = h - l.photo.bottom
        assertEquals(left, right, 0.5f)
        assertEquals(left, top, 0.5f)
        assertTrue("dół szerszy niż góra", bottom > top * 2)
        assertEquals(2000f, maxOf(w, h), 0.5f)
    }

    @Test fun withoutSignatureAllMarginsAreEqual() {
        val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 3000f, 4000f, 0.05f, false, 2000f)
        val l = TightFrame.layout(SizeF(w, h), SizeF(3000f, 4000f), 0.05f, signature = false)
        assertEquals(l.photo.left, l.photo.top, 0.5f)
        assertEquals(l.photo.left, w - l.photo.right, 0.5f)
        assertEquals(l.photo.top, h - l.photo.bottom, 0.5f)
        assertNull(l.signatureBand)
    }

    @Test fun fixedAspectKeepsRatioAndBandSitsBelowPhotoArea() {
        val (w, h) = TightFrame.canvasSize(FrameAspect.Square, 4000f, 3000f, 0.05f, true, 1000f)
        assertEquals(w, h, 0.01f)
        val l = TightFrame.layout(SizeF(w, h), photo, 0.05f, signature = true)
        val band = requireNotNull(l.signatureBand)
        assertEquals(l.photoArea.bottom, band.top, 0.01f)
        assertEquals(h, band.bottom, 0.01f)
    }

    @Test fun signatureNeverLeavesTheBand() {
        val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 4000f, 3000f, 0.03f, true, 1080f)
        val l = TightFrame.layout(SizeF(w, h), photo, 0.03f, signature = true)
        val band = requireNotNull(l.signatureBand)
        val fit = FrameGeometry.fitSignature(l.signatureBaseSize, l.signatureMaxWidth, band.height) { size, _ ->
            TextBlock(size * 6f, size * 1.3f, 1)
        }
        assertTrue(fit.fits)
        val b = requireNotNull(FrameGeometry.signatureBounds(l, fit.width, fit.height))
        assertTrue(b.top >= band.top && b.bottom <= band.bottom)
    }
}
