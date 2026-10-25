package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LutFormatTest {
    @Test
    fun rgb16IsReadWithFullPrecision() {
        val n = 2
        val data = ByteArray(Lut.expectedBytes(n, 2))
        // węzeł (1,0,0), kanał R = 0x8000 (little-endian)
        val i = (1 * Lut.CHANNELS) * 2
        data[i] = 0x00
        data[i + 1] = 0x80.toByte()
        val lut = Lut.fromBinary(data, n, "rgb16")
        assertEquals(0x8000 / 257f, lut.nodeF(1, 0, 0, 0), 1e-4f)
        assertEquals(128, lut.node8(1, 0, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongLengthForCatalogSizeIsRejected() {
        Lut.fromBinary(ByteArray(Lut.expectedBytes(33, 1)), 65, "rgb8")
    }

    @Test
    fun largeGridSizesAreAccepted() {
        for (n in listOf(33, 65, 144)) {
            assertEquals(n, Lut.fromBinary(ByteArray(Lut.expectedBytes(n, 1)), n, "rgb8").size)
        }
    }

    @Test
    fun agslAtlasFitsTextureLimits() {
        assertEquals(6, AgslLutEngine.tilesPerRow(33))
        assertEquals(198, AgslLutEngine.atlasSide(33))
        assertEquals(585, AgslLutEngine.atlasSide(65))
        assertEquals(1728, AgslLutEngine.atlasSide(144))
    }

    @Test
    fun catalogReadsSizeAndFormat() {
        val json = """{"size":33,"luts":[{"id":"a","name":"A","file":"luts/a.rgb16","size":65,"format":"rgb16"},""" +
            """{"id":"b","name":"B","file":"luts/b.rgb8"}]}"""
        val e = LutCatalog.parse(json)
        assertEquals(65, e[0].size)
        assertEquals("rgb16", e[0].format)
        assertEquals(33, e[1].size)
        assertEquals("rgb8", e[1].format)
    }
}
