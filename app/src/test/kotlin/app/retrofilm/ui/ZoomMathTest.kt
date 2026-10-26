package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test fun fitScaleIsPreviewOverExportAndZoomMultiplies() {
        // Podgląd 1000×750 w polu 1000×1000: dopasowanie 1:1 względem podglądu; zapis ma 4000 px dłuższego boku.
        assertEquals(25, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 1f))
        assertEquals(100, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 4f))
        assertEquals(50, ZoomMath.percent(Dim(1000, 1000), Dim(1000, 750), 4000, 2f))
    }

    @Test fun previewSmallerThanBoxIsScaledUpToFit() {
        // Podgląd 500×500 w polu 1000×1000 wyświetla się 2× większy; zapis 1000 px → 200%.
        assertEquals(200, ZoomMath.percent(Dim(1000, 1000), Dim(500, 500), 1000, 1f))
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
}
