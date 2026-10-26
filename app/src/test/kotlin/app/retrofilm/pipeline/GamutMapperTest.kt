package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.ColorSpace
import java.nio.ByteBuffer
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GamutMapperTest {
    private val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)

    /** Bitmapa Display P3 z podanymi surowymi wartościami (r, g, b w 0..255) w kolejnych pikselach. */
    private fun p3Bitmap(vararg px: Triple<Int, Int, Int>): Bitmap {
        val bmp = Bitmap.createBitmap(px.size, 1, Bitmap.Config.ARGB_8888, true, p3)
        val buf = ByteBuffer.allocate(px.size * 4)
        for ((r, g, b) in px) {
            val bytes = if (GamutMapper.redFirst) listOf(r, g, b) else listOf(b, g, r)
            bytes.forEach { buf.put(it.toByte()) }
            buf.put(0xFF.toByte())
        }
        buf.rewind()
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    private fun channels(bmp: Bitmap, x: Int): IntArray {
        val c = bmp.getPixel(x, 0)
        return intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    }

    @Test fun supportsOnlyDisplayP3() {
        assertTrue(GamutMapper.supports(p3Bitmap(Triple(1, 2, 3))))
        assertTrue(!GamutMapper.supports(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)))
    }

    @Test fun resultIsSrgbAndKeepsSize() {
        val out = ColorPolicy.toSrgb(p3Bitmap(Triple(10, 20, 30), Triple(200, 100, 50)))
        assertEquals(2, out.width)
        assertEquals(ColorPolicy.SRGB, out.colorSpace)
    }

    @Test fun inGamutColoursMatchColorSpaceConnectorWithinOneLevel() {
        val colours = listOf(Triple(128, 128, 128), Triple(120, 90, 70), Triple(90, 110, 130), Triple(180, 170, 150))
        val mapped = GamutMapper.toSrgb(p3Bitmap(*colours.toTypedArray()))
        val connector = ColorSpace.connect(p3, ColorSpace.get(ColorSpace.Named.SRGB))
        colours.forEachIndexed { x, (r, g, b) ->
            val expected = connector.transform(r / 255f, g / 255f, b / 255f).map { Math.round(it * 255f) }
            val actual = channels(mapped, x)
            for (k in 0..2) {
                assertTrue("piksel $x kanał $k: ${actual[k]} vs ${expected[k]}", abs(actual[k] - expected[k]) <= 1)
            }
        }
    }

    @Test fun saturatedGreenIsMappedNotClippedAndStaysOpaque() {
        val src = p3Bitmap(Triple(0, 255, 0))
        val mapped = channels(GamutMapper.toSrgb(src), 0)
        val clipped = channels(ColorPolicy.toSrgbClipped(src), 0)
        // przycinanie zeruje czerwień i daje pełną zieleń; mapowanie zostawia w gamucie kolor o tym samym odcieniu
        assertTrue("zmapowany ${mapped.toList()} = przycięty ${clipped.toList()}", mapped.toList() != clipped.toList())
        assertTrue(mapped.all { it in 0..255 })
        assertEquals(0xFF, (GamutMapper.toSrgb(src).getPixel(0, 0) ushr 24) and 0xFF)
    }
}
