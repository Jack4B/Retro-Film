package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test fun fitScaleIsPreviewOverExportAndZoomMultiplies() {
        assertEquals(25, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 1f))
        assertEquals(100, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 4f))
        assertEquals(50, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 2f))
    }

    @Test fun previewSmallerThanBoxIsScaledUpToFit() {
        assertEquals(100, ZoomMath.percent(Dim(1000, 1000), Dim(500, 500), 1000, 1f))
        assertEquals(200, ZoomMath.percent(Dim(1000, 1000), Dim(500, 500), 500, 1f))
    }

    @Test fun exportSizeKeepsPreviewProportions() {
        assertEquals(4000 to 3000, ZoomMath.exportSize(800, 600, 4000))
        assertEquals(3000 to 4000, ZoomMath.exportSize(600, 800, 4000))
        assertEquals(12.0f, ZoomMath.megapixels(4000, 3000), 0.001f)
    }

    @Test fun invalidInputGivesZero() {
        assertEquals(0, ZoomMath.percent(Dim(0, 100), Dim(10, 10), 100, 1f))
        assertEquals(0, ZoomMath.percent(Dim(100, 100), Dim(0, 10), 100, 1f))
    }

    @Test fun visiblePhotoRectAtIdentityCoversFittedPhoto() {
        val r = ZoomMath.visiblePhotoRect(2000, 1000, 1000, 1000, scale = 1f, offsetX = 0f, offsetY = 0f, margin = 0f)
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(2000, r.right)
        assertEquals(1000, r.bottom)
    }

    @Test fun visiblePhotoRectZoomsIntoCenter() {
        val r = ZoomMath.visiblePhotoRect(2000, 1000, 1000, 1000, scale = 2f, offsetX = 0f, offsetY = 0f, margin = 0f)
        // landscape letterbox: 2× przycina szerokość, wysokość zdjęcia nadal wypełnia widok
        assertEquals(500, r.left)
        assertEquals(0, r.top)
        assertEquals(1500, r.right)
        assertEquals(1000, r.bottom)
    }

    @Test fun visiblePhotoRectAppliesMarginAndClamp() {
        val r = ZoomMath.visiblePhotoRect(1000, 1000, 1000, 1000, scale = 4f, offsetX = 0f, offsetY = 0f, margin = 0.5f)
        // bez margin 375..625; +50% → 250..750
        assertEquals(250, r.left)
        assertEquals(250, r.top)
        assertEquals(750, r.right)
        assertEquals(750, r.bottom)
        val full = ZoomMath.visiblePhotoRect(
            1000,
            1000,
            1000,
            1000,
            scale = 4f,
            offsetX = 0f,
            offsetY = 0f,
            margin = 1f
        )
        assertEquals(125, full.left)
        assertEquals(875, full.right)
    }

    @Test fun expandAddsHaloAndClamps() {
        val r = PhotoRect(10, 10, 50, 50)
        val e = ZoomMath.expand(r, 20, 100, 80)
        assertEquals(0, e.left)
        assertEquals(0, e.top)
        assertEquals(70, e.right)
        assertEquals(70, e.bottom)
    }
}
