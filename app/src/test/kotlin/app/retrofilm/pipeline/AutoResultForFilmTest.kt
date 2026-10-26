package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** „Przygotuj pod film”: zostaje balans bieli i ekspozycja, krzywa telefonu jest tylko zdejmowana. */
class AutoResultForFilmTest {
    @Test fun keepsExposureAndWhiteBalanceDropsToneAndBoosts() {
        val r = AutoResult(0.4f, 0.3f, -0.2f, 0.5f, 0.25f, 0.1f, -0.05f).forFilm()
        assertEquals(0.4f, r.exposure, 0f)
        assertEquals(0.1f, r.temperature, 0f)
        assertEquals(-0.05f, r.tint, 0f)
        assertEquals(0f, r.contrast, 0f)
        assertEquals(0f, r.highlights, 0f)
        assertEquals(0f, r.shadows, 0f)
        assertEquals(0f, r.saturation, 0f)
    }

    @Test fun keepsNegativeContrastAndSaturation() {
        val r = AutoResult(0f, -0.15f, 0f, 0f, -0.2f, 0f, 0f).forFilm()
        assertTrue(r.contrast == -0.15f && r.saturation == -0.2f)
    }
}
