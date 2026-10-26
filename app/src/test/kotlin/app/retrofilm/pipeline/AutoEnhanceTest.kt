package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEnhanceTest {
    private val w = 120
    private val h = 90

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Poziomy gradient szarości od [lo] do [hi] (z lekkim wzorem pionowym, żeby nie było pasów). */
    private fun gradient(lo: Int, hi: Int, cast: Triple<Float, Float, Float> = Triple(1f, 1f, 1f)): IntArray =
        IntArray(w * h) {
            val x = it % w
            val y = it / w
            val v = lo + (hi - lo) * (x + (y % 7)).coerceAtMost(w - 1) / (w - 1f)
            argb((v * cast.first).toInt(), v.toInt(), (v * cast.third).toInt())
        }

    private fun suggest(px: IntArray) = AutoEnhance.suggest(px, w, h)

    @Test fun darkImageGetsBrighterAndBrightImageDarker() {
        assertTrue("ciemne → plus EV", suggest(gradient(5, 70)).exposure > 0.5f)
        assertTrue("jasne → minus EV", suggest(gradient(175, 250)).exposure < -0.3f)
        assertEquals("średnie ≈ bez zmian", 0f, suggest(gradient(20, 235)).exposure, 0.4f)
    }

    @Test fun flatImageGetsContrastAndFullRangeDoesNot() {
        assertTrue(suggest(gradient(110, 150)).contrast > 0.25f)
        assertTrue(suggest(gradient(0, 255)).contrast < 0.1f)
    }

    @Test fun deepShadowsAndClippedHighlightsAreRecovered() {
        val dark = IntArray(w * h) { if (it % 10 < 4) argb(6, 6, 6) else argb(170, 170, 170) }
        assertTrue("cienie podniesione", suggest(dark).shadows > 0.3f)
        val bright = IntArray(w * h) { if (it % 10 < 4) argb(252, 252, 252) else argb(110, 110, 110) }
        assertTrue("światła obniżone", suggest(bright).highlights < -0.3f)
    }

    @Test fun neutralImageKeepsWhiteBalance() {
        val r = suggest(gradient(20, 235))
        assertEquals(0f, r.temperature, 0.06f)
        assertEquals(0f, r.tint, 0.06f)
        assertEquals("zdjęcie bez barw: bez zmiany nasycenia", 0f, r.saturation, 0f)
    }

    @Test fun warmCastIsReducedByTheSuggestedWhiteBalance() {
        val px = gradient(40, 220, Triple(1.2f, 1f, 0.85f))
        val r = suggest(px)
        assertTrue("zimniej (minus)", r.temperature < -0.05f)
        val mean = DoubleArray(3)
        for (c in px) {
            mean[0] += AdjustmentProcessor.srgbToLinear(((c shr 16) and 0xFF) / 255f)
            mean[1] += AdjustmentProcessor.srgbToLinear(((c shr 8) and 0xFF) / 255f)
            mean[2] += AdjustmentProcessor.srgbToLinear((c and 0xFF) / 255f)
        }
        val m = WhiteBalance.matrix(r.temperature, r.tint)
        val red = m[0] * mean[0] + m[1] * mean[1] + m[2] * mean[2]
        val blue = m[6] * mean[0] + m[7] * mean[1] + m[8] * mean[2]
        assertTrue(
            "cast red/blue mniejszy niż przed",
            Math.abs(red / blue - 1.0) < Math.abs(mean[0] / mean[2] - 1.0) * 0.8
        )
    }

    @Test fun coolCastGoesTheOtherWay() {
        val r = suggest(gradient(40, 220, Triple(0.85f, 1f, 1.2f)))
        assertTrue("cieplej (plus)", r.temperature > 0.05f)
    }

    @Test fun washedOutColorsGetSaturationAndOversaturatedLoseIt() {
        val pale = IntArray(w * h) { argb(150 + it % 5, 128, 108 - it % 5) }
        assertTrue(suggest(pale).saturation > 0.1f)
        val loud = IntArray(w * h) { if (it % 2 == 0) argb(250, 20, 20) else argb(20, 40, 250) }
        assertTrue(suggest(loud).saturation < 0f)
    }

    @Test fun resultIsDeterministicBoundedAndKeepsOtherAdjustments() {
        val px = gradient(0, 255, Triple(1.3f, 1f, 0.7f))
        val a = suggest(px)
        assertEquals(a, suggest(px))
        assertTrue(a.exposure in -1.5f..1.5f && a.contrast in -0.15f..0.45f)
        assertTrue(a.shadows in 0f..0.7f && a.highlights in -0.7f..0f && a.saturation in -0.2f..0.35f)
        assertTrue(a.temperature in -0.4f..0.4f && a.tint in -0.4f..0.4f)
        val base = Adjustments(grain = 0.3f, vignette = 0.2f, clarity = 0.1f)
        val out = a.applyTo(base)
        assertEquals(0.3f, out.grain, 0f)
        assertEquals(0.2f, out.vignette, 0f)
        assertEquals(a.exposure, out.exposure, 0f)
        assertNotEquals(base, out)
    }

    @Test fun tinyImageDoesNotCrash() {
        val r = AutoEnhance.suggest(IntArray(16) { argb(40, 60, 80) }, 4, 4)
        assertEquals(0f, r.temperature, 0f)
        assertTrue(r.exposure > 0f)
    }
}
