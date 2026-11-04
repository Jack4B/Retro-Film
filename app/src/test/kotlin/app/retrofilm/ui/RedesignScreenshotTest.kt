package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.engine.AppGraph
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zrzuty Roborazzi nowego UI: pasek zakładek z ikonami, pasek serii, arkusz zapisu, szyna nawigacji (tablet). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class RedesignScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun photo(): Bitmap {
        val b = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(90, 120, 150))
        c.drawRect(0f, 460f, 600f, 800f, Paint().apply { color = Color.rgb(120, 100, 80) })
        return b
    }

    private fun state(count: Int): MainState {
        val s = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT)
        val p = photo()
        s.setPhotoForTest(p)
        s.setPreviewForTest(p)
        if (count > 1) s.setSeriesForTest(List(count) { i -> Uri.parse("content://test/$i") })
        return s
    }

    private fun editor(s: MainState) {
        compose.setContent {
            RetroFilmTheme(dynamicColor = false, darkTheme = true) {
                EditorScreen(s, onBack = {}, onSave = {}, onOpenLibrary = {}, onSelectPhoto = {}, snackbar = {})
            }
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun editorSingle() {
        editor(state(1))
        shot("redesign-edytor")
        compose.onNodeWithText("Efekty").performClick()
        shot("redesign-efekty")
    }

    @Test fun editorSeries() {
        editor(state(3))
        shot("redesign-seria-to-zdjecie")
        compose.onNodeWithText("Cała seria").performClick()
        shot("redesign-seria-cala")
    }

    @Test fun saveSheet() {
        val s = state(3)
        compose.setContent {
            RetroFilmTheme(dynamicColor = false, darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box {
                        SaveSheetContent(
                            s,
                            UiMessage.SavedMany(saved = 2, failed = 1),
                            SaveActions({}, {}, {}, {}, {}, {})
                        )
                    }
                }
            }
        }
        shot("redesign-arkusz-zapisu")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-night-mdpi")
    fun tabletRail() {
        editor(state(3))
        shot("redesign-tablet")
    }
}
