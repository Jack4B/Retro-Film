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

    @Test fun biggerSignatureGetsProportionallyTallerBottom() {
        fun bottom(scale: Float): Float {
            val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 4000f, 3000f, 0.03f, true, 2000f, scale)
            val l = TightFrame.layout(SizeF(w, h), photo, 0.03f, signature = true, signatureScale = scale)
            return (h - l.photo.bottom) / minOf(w, h)
        }
        assertTrue(bottom(1.0f) > bottom(0.8f))
        assertTrue(bottom(0.8f) > bottom(0.4f))
    }

    private fun wide(chars: Int) = SignatureMeasurer { size, wrap ->
        val full = chars * size * 0.5f
        if (wrap == null || full <= wrap) {
            TextBlock(full, size * 1.2f, 1)
        } else {
            val n = kotlin.math.ceil(full / wrap).toInt()
            TextBlock(wrap, size * 1.2f * n, n)
        }
    }

    @Test fun sliderAlwaysChangesSignatureSizeEvenForLongText() {
        fun size(scale: Float): Float {
            val m = wide(30)
            val lines = TightFrame.signatureLines(FrameAspect.Original, 4000f, 3000f, 0.03f, scale, m)
            val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 4000f, 3000f, 0.03f, true, 2000f, scale, lines)
            val l = TightFrame.layout(SizeF(w, h), photo, 0.03f, true, scale, lines)
            val band = requireNotNull(l.signatureBand)
            return TightFrame.fitSignature(l.signatureBaseSize * scale, l.signatureMaxWidth, band.height, m).sizePx /
                minOf(w, h)
        }
        assertTrue(size(0.6f) < size(0.8f))
        assertTrue(size(0.8f) < size(1.0f))
        assertTrue(size(1.0f) < size(1.4f))
    }

    @Test fun longTextWrapsAndTallerBandKeepsRequestedSize() {
        val m = wide(60)
        val lines = TightFrame.signatureLines(FrameAspect.Original, 4000f, 3000f, 0.03f, 1.0f, m)
        assertTrue(lines >= 2)
        val (w, h) = TightFrame.canvasSize(FrameAspect.Original, 4000f, 3000f, 0.03f, true, 2000f, 1.0f, lines)
        val l = TightFrame.layout(SizeF(w, h), photo, 0.03f, true, 1.0f, lines)
        val fit = TightFrame.fitSignature(
            l.signatureBaseSize,
            l.signatureMaxWidth,
            requireNotNull(l.signatureBand).height,
            m
        )
        assertTrue(fit.fits)
        assertEquals(l.signatureBaseSize, fit.sizePx, 0.01f)
    }
}
