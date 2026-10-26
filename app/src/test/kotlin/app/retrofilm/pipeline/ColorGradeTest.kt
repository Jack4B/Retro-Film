package app.retrofilm.pipeline

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorGradeTest {
    private fun lum(rgb: FloatArray) =
        0.2126f * AdjustmentProcessor.srgbToLinear(rgb[0]) + 0.7152f * AdjustmentProcessor.srgbToLinear(rgb[1]) +
            0.0722f * AdjustmentProcessor.srgbToLinear(rgb[2])

    private fun chromaOf(rgb: FloatArray): Float = maxOf(rgb[0], rgb[1], rgb[2]) - minOf(rgb[0], rgb[1], rgb[2])

    @Test fun saturationKeepsLightnessAndGrayStaysGray() {
        val c = floatArrayOf(0.55f, 0.42f, 0.33f)
        val before = lum(c)
        ChromaScaler(0.7f).apply(c)
        assertEquals("jasność zachowana", before, lum(c), before * 0.15f)
        val gray = floatArrayOf(0.4f, 0.4f, 0.4f)
        ChromaScaler(1f).apply(gray)
        assertEquals(0.4f, gray[0], 0.01f)
        assertEquals(gray[0], gray[2], 0.005f)
    }

    @Test fun fullDesaturationGivesNeutralGray() {
        val c = floatArrayOf(0.8f, 0.3f, 0.2f)
        ChromaScaler(-1f).apply(c)
        assertEquals(c[0], c[1], 0.01f)
        assertEquals(c[1], c[2], 0.01f)
    }

    @Test fun gamutIsRespectedWithoutHueFlip() {
        val red = floatArrayOf(0.95f, 0.1f, 0.08f)
        ChromaScaler(1f).apply(red)
        assertTrue(red.all { it in 0f..1f })
        assertTrue("czerwony pozostaje czerwony", red[0] > red[1] && red[0] > red[2])
    }

    @Test fun vibranceFavoursMutedOverVividAndSparesSkin() {
        val muted = floatArrayOf(0.55f, 0.48f, 0.45f)
        val vivid = floatArrayOf(0.9f, 0.2f, 0.1f)
        val mutedBefore = chromaOf(muted)
        val vividBefore = chromaOf(vivid)
        ChromaScaler(0.8f).apply(muted)
        ChromaScaler(0.8f).apply(vivid)
        assertTrue("stonowany zyskuje względnie więcej", chromaOf(muted) / mutedBefore > chromaOf(vivid) / vividBefore)
        val skin = floatArrayOf(0.78f, 0.55f, 0.43f)
        val blue = floatArrayOf(0.43f, 0.55f, 0.78f)
        val skinBefore = chromaOf(skin)
        val blueBefore = chromaOf(blue)
        ChromaScaler(1f).apply(skin)
        ChromaScaler(1f).apply(blue)
        assertTrue("skóra wzmocniona słabiej niż niebieski", chromaOf(skin) / skinBefore < chromaOf(blue) / blueBefore)
    }

    @Test fun contrastKeepsHueAnchorsAndNeverExceedsWhite() {
        val up = LuminanceContrast(0.8f)
        val c = floatArrayOf(0.45f, 0.32f, 0.2f)
        val ratioBefore = c[0] / c[2]
        up.apply(c)
        assertEquals("proporcje kanałów zachowane", ratioBefore, c[0] / c[2], ratioBefore * 0.02f)
        val mid = floatArrayOf(0.5f, 0.5f, 0.5f)
        up.apply(mid)
        assertEquals(0.5f, mid[1], 0.01f)
        val white = floatArrayOf(1f, 1f, 1f)
        up.apply(white)
        assertEquals(1f, white[0], 0.001f)
        val bright = floatArrayOf(0.9f, 0.7f, 0.1f)
        up.apply(bright)
        assertTrue(bright.all { it in 0f..1.0001f })
        val black = floatArrayOf(0f, 0f, 0f)
        up.apply(black)
        assertTrue(black.all { it == 0f })
    }

    @Test fun vignetteIsCosineFourthFalloff() {
        val v = VignetteLut(1f)
        assertEquals(1f, v.gain(0f), 1e-4f)
        assertEquals("−2 EV w rogu w gamma", Math.pow(0.25, 0.4545).toFloat(), v.gain(1f), 0.01f)
        var last = 2f
        for (i in 0..20) {
            val g = v.gain(i / 20f)
            assertTrue(g <= last + 1e-6f)
            last = g
        }
        assertTrue(abs(VignetteLut(0.5f).gain(1f) - Math.pow(0.5, 0.4545).toFloat()) < 0.02f)
    }
}
