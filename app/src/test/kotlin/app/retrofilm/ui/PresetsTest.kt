package app.retrofilm.ui

import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.HslAdjust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresetsTest {
    private val rich = Adjustments(
        exposure = 0.5f,
        contrast = -0.2f,
        saturation = 0.3f,
        temperature = 0.4f,
        tint = -0.1f,
        highlights = -0.5f,
        shadows = 0.6f,
        fade = 0.2f,
        grain = 0.3f,
        vignette = 0.4f,
        sharpness = 0.5f,
        clarity = 0.25f,
        dehaze = 0.35f,
        denoise = 0.45f,
        hsl = HslAdjust().with(2, HslAdjust.SATURATION, 0.5f).with(5, HslAdjust.HUE, -0.25f),
        shadowHue = 210f,
        shadowAmount = 0.3f,
        highlightHue = 40f,
        highlightAmount = 0.2f,
        toneBalance = 0.1f
    )

    @Test fun roundTripKeepsEverything() {
        val presets =
            listOf(Preset("Lato", "kodak/portra_400", 0.7f, rich), Preset("Pusty", IDENTITY_ID, 1f, Adjustments.None))
        assertEquals(presets, PresetCodec.decode(PresetCodec.encode(presets)))
    }

    @Test fun brokenOrEmptyDataGivesNoPresets() {
        assertTrue(PresetCodec.decode("to nie jest json").isEmpty())
        assertTrue(PresetCodec.decode("[]").isEmpty())
        assertTrue(PresetCodec.decode("[{\"film\":\"x\"}]").isEmpty())
    }

    @Test fun missingFieldsFallBackToNeutral() {
        val p = PresetCodec.decode("[{\"name\":\"A\"}]").single()
        assertEquals(Preset("A", IDENTITY_ID, 1f, Adjustments.None), p)
    }

    @Test fun longNamesAreTrimmed() {
        val long = "x".repeat(100)
        val p = PresetCodec.decode(PresetCodec.encode(listOf(Preset(long, IDENTITY_ID, 1f, Adjustments.None)))).single()
        assertEquals(Preset.MAX_NAME, p.name.length)
    }
}
