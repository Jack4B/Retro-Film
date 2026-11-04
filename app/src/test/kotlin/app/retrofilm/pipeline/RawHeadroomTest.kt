package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

/** J: zapas HDR DNG z liniowej luminancji F16 – percentyl 99,5 ponad biel SDR. */
class RawHeadroomTest {
    @Test fun sdrOnlyHasNoHeadroom() {
        assertEquals(0f, HdrAnalysis.rawHeadroom(FloatArray(1000) { it / 1000f }), 0f)
        assertEquals(0f, HdrAnalysis.rawHeadroom(FloatArray(0)), 0f)
    }

    @Test fun brightHighlightsGiveLog2OfPercentile() {
        val luma = FloatArray(1000) { if (it >= 990) 4f else 0.2f }
        assertEquals(2f, HdrAnalysis.rawHeadroom(luma), 1e-4f)
    }

    @Test fun isolatedHotPixelsAreIgnored() {
        val luma = FloatArray(1000) { if (it < 3) 64f else 0.5f }
        assertEquals(0f, HdrAnalysis.rawHeadroom(luma), 0f)
    }
}
