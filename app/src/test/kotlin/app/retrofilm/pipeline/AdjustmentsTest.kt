package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustmentsTest {
    private fun px(r: Int, g: Int, b: Int, adj: Adjustments) = AdjustmentsApplier.applyToPixel(Color.rgb(r, g, b), adj)

    @Test fun neutralIsFlaggedAndReturnsSameBitmap() {
        assertTrue(Adjustments.None.isNeutral)
        assertFalse(Adjustments(exposure = 0.1f).isNeutral)
        val src = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        assertSame(src, AdjustmentsApplier.apply(src, Adjustments.None))
    }

    @Test fun exposureWorksInLinearLight() {
        // +1 EV podwaja światło liniowe: sRGB 0,5 (liniowo ~0,214) -> liniowo ~0,428 -> sRGB ~0,684.
        val out = Color.red(px(128, 128, 128, Adjustments(exposure = 1f)))
        assertEquals(0.684f * 255, out.toFloat(), 3f)
        assertTrue(Color.red(px(128, 128, 128, Adjustments(exposure = -1f))) < 128 - 20)
    }

    @Test fun highlightsRollOffInsteadOfHardClipping() {
        val a = Color.red(px(200, 200, 200, Adjustments(exposure = 1f)))
        val b = Color.red(px(230, 230, 230, Adjustments(exposure = 1f)))
        assertTrue("jasne tony nadal się różnią po +1 EV", b > a)
        assertTrue(b <= 255)
    }

    @Test fun contrastKeepsBlackMidAndWhite() {
        val adj = Adjustments(contrast = 0.8f)
        assertEquals(0, Color.red(px(0, 0, 0, adj)))
        assertEquals(255, Color.red(px(255, 255, 255, adj)))
        assertEquals(128f, Color.red(px(128, 128, 128, adj)).toFloat(), 2f)
        assertTrue(Color.red(px(64, 64, 64, adj)) < 64)
        assertTrue(Color.red(px(192, 192, 192, adj)) > 192)
    }

    @Test fun negativeContrastFlattens() {
        val adj = Adjustments(contrast = -0.8f)
        assertTrue(Color.red(px(30, 30, 30, adj)) > 30)
        assertTrue(Color.red(px(220, 220, 220, adj)) < 220)
    }

    @Test fun saturationMinusOneGivesGrayAndGrayStaysGray() {
        val gray = px(200, 80, 40, Adjustments(saturation = -1f))
        assertEquals(Color.red(gray).toFloat(), Color.green(gray).toFloat(), 1f)
        assertEquals(Color.green(gray).toFloat(), Color.blue(gray).toFloat(), 1f)
        val boosted = px(120, 120, 120, Adjustments(saturation = 1f))
        assertEquals(120f, Color.red(boosted).toFloat(), 1f)
        assertEquals(Color.red(boosted), Color.blue(boosted))
    }

    @Test fun vibranceBoostsMutedMoreThanSaturated() {
        val muted = px(150, 120, 110, Adjustments(saturation = 0.8f))
        val vivid = px(250, 40, 20, Adjustments(saturation = 0.8f))
        // względny przyrost rozpiętości kanałów (R−B): stonowany kolor zyskuje względnie więcej
        val mutedRatio = (Color.red(muted) - Color.blue(muted)) / (150f - 110f)
        val vividRatio = (Color.red(vivid) - Color.blue(vivid)) / (250f - 20f)
        assertTrue("stonowany kolor zyskuje względnie więcej ($mutedRatio vs $vividRatio)", mutedRatio > vividRatio)
        assertTrue(Color.red(vivid) <= 255)
    }

    @Test fun warmTemperatureShiftsRedUpBlueDownKeepingBrightness() {
        val warm = px(128, 128, 128, Adjustments(temperature = 1f))
        assertTrue(Color.red(warm) > Color.blue(warm) + 20)
        val luma = 0.2126f * Color.red(warm) + 0.7152f * Color.green(warm) + 0.0722f * Color.blue(warm)
        assertTrue("jasność zbliżona", abs(luma - 128f) < 12f)
        val cool = px(128, 128, 128, Adjustments(temperature = -1f))
        assertTrue(Color.blue(cool) > Color.red(cool) + 20)
    }

    @Test fun applyKeepsSizeAndPreservesSource() {
        val src = Bitmap.createBitmap(130, 70, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(100, 150, 200))
        val out = AdjustmentsApplier.apply(src, Adjustments(exposure = 0.5f, contrast = 0.3f, saturation = 0.2f))
        assertNotSame(src, out)
        assertEquals(130, out.width)
        assertEquals(70, out.height)
        assertEquals(Color.rgb(100, 150, 200), src.getPixel(0, 0))
        assertTrue(out.getPixel(129, 69) != src.getPixel(129, 69))
    }

    private fun gray(v: Int, w: Int = 9, h: Int = 9) = IntArray(w * h) { Color.rgb(v, v, v) }

    @Test fun shadowsLiftAndHighlightsRecover() {
        assertTrue(Color.red(px(40, 40, 40, Adjustments(shadows = 1f))) > 40 + 15)
        assertTrue(Color.red(px(40, 40, 40, Adjustments(shadows = -1f))) < 40)
        assertTrue(Color.red(px(220, 220, 220, Adjustments(highlights = -1f))) < 220 - 8)
        // cienie nie ruszają jasnych tonów i odwrotnie
        assertEquals(230f, Color.red(px(230, 230, 230, Adjustments(shadows = 1f))).toFloat(), 3f)
        assertEquals(20f, Color.red(px(20, 20, 20, Adjustments(highlights = 1f))).toFloat(), 2f)
    }

    @Test fun fadeLiftsBlackAndDimsWhite() {
        val black = Color.red(px(0, 0, 0, Adjustments(fade = 1f)))
        val white = Color.red(px(255, 255, 255, Adjustments(fade = 1f)))
        assertTrue(black in 25..40)
        assertTrue(white < 255 && white > 230)
    }

    @Test fun vignetteDarkensCornersNotCenter() {
        val px = gray(128)
        AdjustmentProcessor(Adjustments(vignette = 1f)).process(px, px.size, PixelSpan(9, 0, 9))
        val center = Color.red(px[4 * 9 + 4])
        val corner = Color.red(px[0])
        assertEquals(128f, center.toFloat(), 3f)
        assertTrue("róg ciemniejszy", corner < center - 20)
    }

    @Test fun grainIsDeterministicNoisyAndKeepsAverageBrightness() {
        val a = gray(128, 40, 40)
        val b = gray(128, 40, 40)
        val adj = Adjustments(grain = 1f)
        AdjustmentProcessor(adj).process(a, a.size, PixelSpan(40, 0, 40))
        AdjustmentProcessor(adj).process(b, b.size, PixelSpan(40, 0, 40))
        assertTrue("to samo ziarno dla tego samego obrazu", a.contentEquals(b))
        assertTrue("piksele się różnią", a.distinct().size > 10)
        val mean = a.map { Color.red(it) }.average()
        assertEquals(128.0, mean, 4.0)
    }

    @Test fun noiseStaysInRangeAndIsStable() {
        for (x in 0 until 50) {
            for (y in 0 until 50) {
                val n = AdjustmentProcessor.noise(x, y, 1)
                assertTrue(n >= -1f && n <= 1f)
                assertEquals(n, AdjustmentProcessor.noise(x, y, 1), 0f)
            }
        }
    }

    @Test fun newFieldsMakeAdjustmentsNonNeutral() {
        assertFalse(Adjustments(grain = 0.1f).isNeutral)
        assertFalse(Adjustments(vignette = 0.1f).isNeutral)
        assertFalse(Adjustments(fade = 0.1f).isNeutral)
    }

    @Test fun shadowsLiftDoesNotLeakAcrossEdgesIntoBrightAreas() {
        val w = 200
        val src = Bitmap.createBitmap(w, 40, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) {
            for (y in 0 until 40) {
                src.setPixel(
                    x,
                    y,
                    if (x <
                        w / 2
                    ) {
                        Color.rgb(40, 40, 40)
                    } else {
                        Color.rgb(230, 230, 230)
                    }
                )
            }
        }
        val out = AdjustmentsApplier.apply(src, Adjustments(shadows = 1f))
        val far = Color.red(out.getPixel(2, 20))
        val near = Color.red(out.getPixel(w / 2 - 3, 20))
        assertTrue("cień rośnie", far > 40 + 15)
        assertEquals("filtr zachowuje krawędź: cień przy jasnym tak samo jak daleko", far.toFloat(), near.toFloat(), 4f)
        val bright = Color.red(out.getPixel(w / 2 + 3, 20))
        assertEquals("jasny obszar przy krawędzi bez zmian", 230f, bright.toFloat(), 4f)
    }

    @Test fun tintMovesGreenMagentaAndStaysNeutralAtZero() {
        val neutral = px(128, 128, 128, Adjustments(tint = 0f, exposure = 0.0001f))
        assertEquals(Color.red(neutral).toFloat(), Color.green(neutral).toFloat(), 2f)
        val magenta = px(128, 128, 128, Adjustments(tint = 1f))
        assertTrue(Color.green(magenta) < Color.red(magenta) - 5)
        val green = px(128, 128, 128, Adjustments(tint = -1f))
        assertTrue(Color.green(green) > Color.red(green) + 2)
    }

    @Test fun toneWeightsAreSmoothAndComplementary() {
        assertEquals(0f, AdjustmentProcessor.shadowWeight(0f), 0.001f)
        assertEquals(1f, AdjustmentProcessor.shadowWeight(0.1f), 0.001f)
        assertEquals(0f, AdjustmentProcessor.shadowWeight(0.9f), 0.001f)
        assertEquals(0f, AdjustmentProcessor.highlightWeight(0.1f), 0.001f)
        assertEquals(1f, AdjustmentProcessor.highlightWeight(1f), 0.001f)
        var last = 2f
        for (i in 3..20) {
            val w = AdjustmentProcessor.shadowWeight(i / 20f)
            assertTrue("waga cieni nie rośnie z jasnością", w <= last + 1e-6f)
            last = w
        }
    }

    @Test fun shadowLiftKeepsHueOrderAndDoesNotAmplifyChroma() {
        val out = px(30, 60, 90, Adjustments(shadows = 1f))
        val ratioBefore = 90f / 30f
        val ratioAfter = Color.blue(out).toFloat() / Color.red(out)
        // chroma rośnie wolniej niż jasność (szum barwny w cieniach), więc proporcja maleje, ale odcień zostaje
        assertTrue("kolejność kanałów", Color.blue(out) > Color.green(out) && Color.green(out) > Color.red(out))
        assertTrue("barwa nie rośnie ponad oryginał ($ratioAfter)", ratioAfter in 1.6f..ratioBefore + 0.05f)
        assertTrue(Color.red(out) in 31..255 && Color.blue(out) <= 255)
    }

    @Test fun grainIsSmoothBetweenNeighbours() {
        var maxStep = 0f
        for (x in 0 until 60) {
            maxStep = maxOf(maxStep, kotlin.math.abs(FilmGrain.luma(x + 1, 5, 4f) - FilmGrain.luma(x, 5, 4f)))
        }
        assertTrue("sąsiednie piksele ziarna nie skaczą", maxStep < 0.6f)
    }

    @Test fun grainFieldIsGaussianWithUnitVarianceAndNoPeriodicSeam() {
        var sum = 0.0
        var sq = 0.0
        var n = 0
        for (y in 0 until 300 step 3) {
            for (x in 0 until 300 step 3) {
                val g = FilmGrain.luma(x, y, 1f).toDouble()
                sum += g
                sq += g * g
                n++
            }
        }
        val mean = sum / n
        assertEquals("średnia 0", 0.0, mean, 0.15)
        assertEquals("wariancja ≈ 1", 1.0, sq / n - mean * mean, 0.3)
        // niezależność kanałów barwnych: korelacja bliska zera
        var cov = 0.0
        for (i in 0 until 2000) {
            cov += FilmGrain.chroma(i, i / 7, 1f, 0) * FilmGrain.chroma(i, i / 7, 1f, 1)
        }
        assertTrue("kanały nieskorelowane", kotlin.math.abs(cov / 2000) < 0.15)
    }

    @Test fun grainAmplitudeFollowsCrystalModel() {
        val mid = FilmGrain.stops(0.5f, 1f)
        assertTrue("półtony ziarniste najmocniej", mid > FilmGrain.stops(0.05f, 1f) && mid > FilmGrain.stops(0.97f, 1f))
        assertTrue("zostaje podłoga w cieniach", FilmGrain.stops(0.02f, 1f) > 0.05f)
        assertEquals("bez ziarna mnożnik ≈ 1", 1f, FilmGrain.gain(0f, 1.3f), 1e-6f)
    }

    @Test fun grainMultiplierKeepsMeanExposureOverManySamples() {
        var sum = 0.0
        val n = 20000
        for (i in 0 until n) sum += FilmGrain.gain(0.5f, FilmGrain.luma(i % 400, i / 400, 1f))
        assertEquals("średnia ekspozycji bez zmian", 1.0, sum / n, 0.03)
    }

    @Test fun vibranceBoostsSkinLessThanBlue() {
        val skin = px(200, 140, 110, Adjustments(saturation = 1f))
        val blue = px(110, 140, 200, Adjustments(saturation = 1f))
        val skinGain = Color.red(skin) - Color.blue(skin) - (200 - 110)
        val blueGain = Color.blue(blue) - Color.red(blue) - (200 - 110)
        assertTrue("skóra mniej wzmocniona niż niebieski", skinGain < blueGain)
    }
}
