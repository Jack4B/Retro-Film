package app.retrofilm.pipeline

import app.retrofilm.core.engine.Lut
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmCalibrationTest {
    private fun gammaLut(gamma: Float) = Lut.generate(33) { r, g, b -> Triple(r.pow(gamma), g.pow(gamma), b.pow(gamma)) }

    @Test fun identityFilmKeepsEighteenPercent() {
        assertEquals(0.18f, FilmCalibration.of(Lut.identity()).targetGrey, 0.01f)
    }

    @Test fun darkeningFilmAsksForBrighterInputAndBrighteningForDarker() {
        val dark = FilmCalibration.of(gammaLut(1.3f)).targetGrey
        val bright = FilmCalibration.of(gammaLut(0.8f)).targetGrey
        assertTrue("ciemny film → jaśniejsze wejście: $dark", dark > 0.2f)
        assertTrue("jasny film → ciemniejsze wejście: $bright", bright < 0.16f)
    }

    @Test fun rampResultIsExactOnTheRamp() {
        // Wejście targetGrey przez LUT daje 18% (±0,01) – rampa trafia w punkt.
        val lut = gammaLut(1.2f)
        val t = FilmCalibration.of(lut).targetGrey
        val v = AdjustmentProcessor.linearToSrgb(t)
        val out = AdjustmentProcessor.srgbToLinear(v.pow(1.2f))
        assertEquals(0.18f, out, 0.01f)
    }

    @Test fun extremeFilmIsClampedToOneStop() {
        val black = Lut.generate(17) { _, _, _ -> Triple(0.05f, 0.05f, 0.05f) }
        assertEquals(FilmCalibration.MAX_TARGET, FilmCalibration.of(black).targetGrey, 1e-6f)
        val white = Lut.generate(17) { _, _, _ -> Triple(1f, 1f, 1f) }
        assertEquals(FilmCalibration.MIN_TARGET, FilmCalibration.of(white).targetGrey, 1e-6f)
    }

    @Test fun shoulderIsBelowOneForCompressingFilm() {
        val film = Lut.generate(33) { r, g, b -> Triple(minOf(r * 1.4f, 0.8f), minOf(g * 1.4f, 0.8f), minOf(b * 1.4f, 0.8f)) }
        assertTrue(FilmCalibration.of(film).shoulder < 0.6f)
    }
}
