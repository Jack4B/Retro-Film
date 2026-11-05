package app.retrofilm.pipeline

import org.junit.Assert.assertTrue
import org.junit.Test

/** O: akumulator GuidedFilter nie może alokować ~75 MB (crash realme przy semanticMap). */
class LowresCapTest {
    @Test fun accumulatorCellsStayWithinCapForDetailPreviewSizes() {
        for (wh in listOf(2560 to 1920, 2048 to 1536, 3072 to 4096, 1280 to 960)) {
            val low = Lowres.forSize(wh.first, wh.second, SemanticSharpen.TARGET)
            val cells = low.w.toLong() * low.h
            assertTrue("$wh: low=${low.w}x${low.h} cells=$cells", cells <= Lowres.MAX_CELLS)
            assertTrue(3L * cells * 8 <= 12L * 1_000_000)
        }
    }
}
