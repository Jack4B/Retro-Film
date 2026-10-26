package app.retrofilm.pipeline

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpenPlannerTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun noiseEstimatorMatchesGaussianNoise() {
        val rnd = Random(5)
        val w = 200
        val h = 200
        val luma = FloatArray(w * h) { 128f + rnd.nextGaussian().toFloat() * 8f }
        assertEquals(8f, SharpenPlanner.noiseSigma(luma, w, h), 1f)
        assertEquals(0f, SharpenPlanner.noiseSigma(FloatArray(w * h) { 100f }, w, h), 0.01f)
    }

    @Test fun skinHeavyPhotoGetsPortraitSettings() {
        val px = IntArray(60 * 60) { rgb(224, 172, 150) }
        val s = SharpenPlanner.analyze(px, 60, 60)
        assertTrue("wykryto skórę", s.skinRatio > 0.9f)
        val p = SharpenPlanner.suggest(s)
        assertEquals(SharpenPreset.Portrait.params, p)
    }

    @Test fun edgyNonSkinPhotoGetsLandscapeAndNoiseLowersAmount() {
        val px = IntArray(80 * 80) { i ->
            if ((i % 80 / 4 + i / 80 / 4) % 2 ==
                0
            ) {
                rgb(40, 120, 40)
            } else {
                rgb(200, 220, 255)
            }
        }
        val s = SharpenPlanner.analyze(px, 80, 80)
        assertEquals(SharpenPreset.Landscape.params, SharpenPlanner.suggest(s.copy(noiseSigma = 0f)))
        val noisy = SharpenPlanner.suggest(s.copy(noiseSigma = 9f))
        assertTrue(noisy.amount < SharpenPreset.Landscape.params!!.amount)
        assertTrue(noisy.masking >= 0.5f)
    }
}
