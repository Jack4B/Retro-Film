package app.retrofilm.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceTest {
    private fun white(m: FloatArray) = floatArrayOf(
        m[0] + m[1] + m[2],
        m[3] + m[4] + m[5],
        m[6] + m[7] + m[8]
    )

    private fun luma(w: FloatArray) = 0.2126f * w[0] + 0.7152f * w[1] + 0.0722f * w[2]

    @Test fun neutralIsIdentity() {
        assertArrayEquals(WhiteBalance.IDENTITY, WhiteBalance.matrix(0f, 0f), 0f)
    }

    @Test fun daylightLocusMatchesD65() {
        val (x, y) = WhiteBalance.daylightXy(6504.0)
        assertEquals(0.3127, x, 0.002)
        assertEquals(0.3290, y, 0.002)
    }

    @Test fun warmAndCoolShiftRedBlueAndKeepWhiteLuminance() {
        val warm = white(WhiteBalance.matrix(1f, 0f))
        val cool = white(WhiteBalance.matrix(-1f, 0f))
        assertTrue("ciepły: czerwony > niebieski", warm[0] > warm[2] + 0.15f)
        assertTrue("zimny: niebieski > czerwony", cool[2] > cool[0] + 0.15f)
        assertEquals(1f, luma(warm), 0.01f)
        assertEquals(1f, luma(cool), 0.01f)
    }

    @Test fun temperatureIsMonotonic() {
        var last = 0f
        for (t in listOf(-1f, -0.5f, 0.0001f, 0.5f, 1f)) {
            val w = white(WhiteBalance.matrix(t, 0f))
            val ratio = w[0] / w[2]
            assertTrue("R/B rośnie z temperaturą", ratio > last)
            last = ratio
        }
    }

    @Test fun positiveTintAddsMagentaNegativeAddsGreen() {
        val magenta = white(WhiteBalance.matrix(0f, 1f))
        val green = white(WhiteBalance.matrix(0f, -1f))
        assertTrue(magenta[1] < 1f && magenta[1] < magenta[0] && magenta[1] < magenta[2])
        assertTrue(green[1] > 1f)
        assertEquals(1f, luma(magenta), 0.01f)
    }
}
