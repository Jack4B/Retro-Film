package app.retrofilm.pipeline

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HdrAnalysisTest {
    private fun tiff(order: ByteOrder, num: Int, den: Int, tag: Int = 0xC62A): ByteArray {
        val b = ByteBuffer.allocate(64).order(order)
        b.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        b.putShort(42).putInt(8)
        b.putShort(1) // jeden wpis IFD0
        b.putShort(tag.toShort()).putShort(10).putInt(1).putInt(26)
        b.putInt(0) // brak kolejnego IFD
        b.putInt(num).putInt(den)
        return b.array()
    }

    @Test fun readsDngBaselineExposureInBothByteOrders() {
        assertEquals(-0.5f, HdrAnalysis.baselineExposure(tiff(ByteOrder.LITTLE_ENDIAN, -1, 2))!!, 1e-6f)
        assertEquals(0.75f, HdrAnalysis.baselineExposure(tiff(ByteOrder.BIG_ENDIAN, 3, 4))!!, 1e-6f)
        assertEquals(HdrAnalysis.HdrInfo(0f, 0.75f), HdrAnalysis.of(tiff(ByteOrder.BIG_ENDIAN, 3, 4)))
    }

    @Test fun missingOrBrokenTagGivesNull() {
        assertNull(HdrAnalysis.baselineExposure(tiff(ByteOrder.LITTLE_ENDIAN, 1, 2, tag = 0x0100)))
        assertNull(HdrAnalysis.baselineExposure(tiff(ByteOrder.LITTLE_ENDIAN, 1, 0)))
        assertNull(HdrAnalysis.baselineExposure(ByteArray(4)))
        assertEquals(HdrAnalysis.NONE, HdrAnalysis.of(byteArrayOf(1, 2, 3)))
    }

    @Test fun readsGainMapCapacityFromXmpAttributeOrElement() {
        val attr = "<x:xmpmeta hdrgm:Version=\"1.0\" hdrgm:GainMapMax=\"2.3\" hdrgm:HDRCapacityMax=\"2.5\"/>"
        assertEquals(2.5f, HdrAnalysis.gainMapHeadroom(attr.toByteArray()), 1e-6f)
        val elem = "<rdf:li><hdrgm:HDRCapacityMax>1.8</hdrgm:HDRCapacityMax></rdf:li>"
        assertEquals(1.8f, HdrAnalysis.gainMapHeadroom(elem.toByteArray()), 1e-6f)
        assertEquals(0f, HdrAnalysis.gainMapHeadroom("brak".toByteArray()), 0f)
        assertEquals(6f, HdrAnalysis.gainMapHeadroom("hdrgm:HDRCapacityMax=\"99\"".toByteArray()), 0f)
    }
}
