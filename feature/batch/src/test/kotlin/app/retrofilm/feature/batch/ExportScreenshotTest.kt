package app.retrofilm.feature.batch

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.retrofilm.core.ui.RetroFilmTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class ExportScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, state: ExportUiState) {
        compose.setContent { RetroFilmTheme { ExportScreen(state, {}, {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/eksport-$name.png")
    }

    @Test fun default_no_frame() = shot("domyslny", ExportUiState())

    @Test fun series_in_progress() = shot(
        "seria-postep",
        ExportUiState(photoCount = 12, frameEnabled = true, frameSummary = "Ramka 1:1 · podpis", progress = 3)
    )
}
