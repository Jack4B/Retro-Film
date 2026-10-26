package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HslToningTest {
    private fun mix(adj: HslAdjust, r: Float, g: Float, b: Float): FloatArray {
        val rgb = floatArrayOf(r, g, b)
        HslMixer(adj).apply(rgb)
        return rgb
    }

    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]

    @Test fun neutralAndGrayAreUntouched() {
        assertTrue(HslAdjust().isNeutral)
        val gray = mix(HslAdjust().with(0, HslAdjust.SATURATION, 1f), 0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, gray[0], 1e-4f)
        assertEquals(0.5f, gray[2], 1e-4f)
        val same = mix(HslAdjust(), 0.8f, 0.3f, 0.2f)
        assertEquals(0.8f, same[0], 1e-3f)
        assertEquals(0.3f, same[1], 1e-3f)
        assertEquals(0.2f, same[2], 1e-3f)
    }

    @Test fun bandAffectsOnlyItsOwnColor() {
        val adj = HslAdjust().with(3, HslAdjust.SATURATION, -1f) // zielony odbarwiony
        val green = mix(adj, 0.2f, 0.7f, 0.2f)
        val red = mix(adj, 0.8f, 0.2f, 0.2f)
        assertTrue("zielony traci nasycenie", green[1] - green[0] < 0.7f - 0.2f - 0.2f)
        assertEquals("czerwony bez zmian", 0.8f, red[0], 1e-3f)
        assertEquals(0.2f, red[1], 1e-3f)
    }

    @Test fun hueShiftRotatesColor() {
        val adj = HslAdjust().with(0, HslAdjust.HUE, 1f) // czerwony +30° w stronę pomarańczowego
        val out = mix(adj, 0.9f, 0.1f, 0.1f)
        assertTrue("zielony składnik rośnie", out[1] > 0.1f + 0.05f)
        assertTrue("nadal czerwono-pomarańczowy", out[0] > out[1] && out[1] > out[2])
    }

    @Test fun lightnessBrightensAndDarkensBand() {
        val up = mix(HslAdjust().with(5, HslAdjust.LIGHTNESS, 1f), 0.2f, 0.3f, 0.7f)
        val down = mix(HslAdjust().with(5, HslAdjust.LIGHTNESS, -1f), 0.2f, 0.3f, 0.7f)
        assertTrue(up[2] > 0.7f)
        assertTrue(down[2] < 0.7f)
    }

    @Test fun splitToningTintsShadowsAndHighlightsWithoutChangingLuma() {
        val warmHighlights = SplitToning(0f, 0f, 40f, 1f, 0f)
        val bright = floatArrayOf(0.9f, 0.9f, 0.9f)
        val before = luma(bright)
        warmHighlights.apply(bright, before)
        assertTrue("jasne tony cieplej", bright[0] > bright[2] + 0.02f)
        assertEquals(before, luma(bright), 0.02f)
        val dark = floatArrayOf(0.1f, 0.1f, 0.1f)
        warmHighlights.apply(dark, luma(dark))
        assertEquals("cienie bez tonowania świateł", dark[0], dark[2], 0.005f)
    }

    @Test fun balanceMovesTheBoundary() {
        val mid = floatArrayOf(0.5f, 0.5f, 0.5f)
        val shadowsBlue = SplitToning(220f, 1f, 0f, 0f, -1f)
        val a = mid.copyOf()
        shadowsBlue.apply(a, 0.5f)
        val neutral = SplitToning(220f, 1f, 0f, 0f, 0f)
        val b = mid.copyOf()
        neutral.apply(b, 0.5f)
        assertTrue("minus balansu = więcej tonu cieni w półtonach", a[2] - a[0] > b[2] - b[0])
    }

    @Test fun oklabRoundTripIsAccurate() {
        val ok = OkLab()
        val out = FloatArray(3)
        for (c in listOf(
            floatArrayOf(0.8f, 0.3f, 0.2f),
            floatArrayOf(0.1f, 0.6f, 0.9f),
            floatArrayOf(0.5f, 0.5f, 0.5f)
        )) {
            ok.fromRgb(c)
            ok.toRgb(ok.lab[0], ok.lab[1], ok.lab[2], out)
            for (i in 0..2) assertEquals(c[i], out[i], 0.004f)
        }
    }

    @Test fun oklabOutOfGamutChromaIsReducedNotClipped() {
        val ok = OkLab()
        val out = FloatArray(3)
        ok.fromRgb(floatArrayOf(0.9f, 0.1f, 0.1f))
        val hue = OkLab.hueDegrees(ok.lab[1], ok.lab[2])
        ok.toRgb(ok.lab[0], ok.lab[1] * 3f, ok.lab[2] * 3f, out)
        assertTrue(out.all { it in 0f..1f })
        ok.fromRgb(out)
        assertEquals("odcień zachowany", hue, OkLab.hueDegrees(ok.lab[1], ok.lab[2]), 3f)
    }

    @Test fun hslBandsSitAtTheirOklchPrimaries() {
        // zielony (pierwotny sRGB) ma kąt ≈142° w OKLab: leży w środku zakresu nr 3
        val ok = OkLab()
        ok.fromRgb(floatArrayOf(0f, 1f, 0f))
        assertEquals(HslAdjust.BAND_HUES[3].toFloat(), OkLab.hueDegrees(ok.lab[1], ok.lab[2]), 4f)
        ok.fromRgb(floatArrayOf(0f, 0f, 1f))
        assertEquals(HslAdjust.BAND_HUES[5].toFloat(), OkLab.hueDegrees(ok.lab[1], ok.lab[2]), 4f)
    }
}
