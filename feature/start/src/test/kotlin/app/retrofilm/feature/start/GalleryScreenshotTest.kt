package app.retrofilm.feature.start

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.retrofilm.core.ui.preview.RetroFilmPreview
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
class GalleryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, state: GalleryUiState) {
        compose.setContent { RetroFilmPreview { GalleryScreen(state, {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/galeria-$name.png")
    }

    @Test fun empty_selection() = shot("brak-wyboru", GalleryUiState(SampleGalleryPhotos))

    @Test fun multi_selection() = shot("seria-3", GalleryUiState(SampleGalleryPhotos, linkedSetOf("p1", "p4", "p5")))
}

/** Przykładowe dane tylko dla testów zrzutów. */
private val SampleGalleryPhotos = List(12) { GalleryPhoto("p$it", "${it + 1}", it) }
