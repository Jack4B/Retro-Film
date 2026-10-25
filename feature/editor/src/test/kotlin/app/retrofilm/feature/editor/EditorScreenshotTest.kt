package app.retrofilm.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.ui.preview.PreviewFilmEngine
import app.retrofilm.core.ui.preview.RetroFilmPreview
import app.retrofilm.feature.editor.frame.FrameScreen
import app.retrofilm.feature.editor.frame.FrameSettings
import app.retrofilm.feature.editor.frame.FrameUiState
import app.retrofilm.feature.editor.frame.SIGNATURE_TOO_LONG_A11Y
import app.retrofilm.feature.editor.frame.SIGNATURE_TOO_LONG_MESSAGE
import app.retrofilm.feature.editor.frame.SeriesPhoto
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class EditorScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { RetroFilmPreview { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val single = listOf(SeriesPhoto("a", 0, 1.5f))
    private val on = FrameSettings(enabled = true, signatureEnabled = true)

    private fun frame(name: String, state: FrameUiState) =
        shot("ramka-$name") { FrameScreen(state, {}, {}, {}, {}, {}, {}) }

    @Test fun editor_default() = shot("edytor") {
        EditorScreen(sampleEditorState(), PreviewFilmEngine, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun frame_off_default() {
        val s = FrameUiState(photos = single)
        assertEquals(false, s.settings.enabled)
        assertEquals(FrameAspect.Square, s.settings.aspect)
        frame("wylaczona", s)
    }

    @Test fun frame_on_no_signature() = frame(
        "bez-podpisu",
        FrameUiState(FrameSettings(enabled = true), photos = single)
    )

    @Test fun frame_on_with_signature() = frame(
        "podpis-tatry",
        FrameUiState(on, seriesSignature = "Tatry", photos = single)
    )

    @Test fun frame_long_signature_39() {
        val text = "Poranna mgła nad Jeziorem Bachotek 2026"
        assertEquals(39, text.length)
        frame(
            "podpis-39-40",
            FrameUiState(
                on.copy(aspect = FrameAspect.Portrait45, margin = 0.15f),
                seriesSignature = text,
                photos = single
            )
        )
    }

    @Test fun frame_signature_40_error() {
        val text = "Wschód słońca nad Jeziorem Bachotek, maj"
        assertEquals(40, text.length)
        frame("podpis-40-40-blad", FrameUiState(on, seriesSignature = text, photos = single))
    }

    /**
     * Stan błędu „podpis za długi”. Prawdziwy podpis ≤ 40 znaków mieści się zawsze (najgorszy przypadek
     * 40×„W” zmniejsza się najwyżej do 54% bazowego), więc stan wymuszamy tekstem ponad limitem
     * (np. podpis z importu), aby zweryfikować komunikat i to, że podpis NIE jest rysowany.
     */
    @Test fun frame_signature_too_long_error() {
        val text = "W".repeat(FrameGeometry.SIGNATURE_MAX_CHARS * 3)
        val state = FrameUiState(on.copy(margin = 0.15f), seriesSignature = text, photos = single)
        frame("podpis-za-dlugi", state)
        compose.onNodeWithText(SIGNATURE_TOO_LONG_MESSAGE).assertIsDisplayed()
        compose.onNode(hasContentDescription(SIGNATURE_TOO_LONG_A11Y)).assertExists()
    }

    @Test fun frame_series_per_photo_signature() = frame(
        "seria-podpis-zdjecia",
        FrameUiState(on, seriesSignature = "Tatry", photos = SampleSeries, currentIndex = 1)
    )
}
