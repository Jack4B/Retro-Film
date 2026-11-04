package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.engine.AppGraph
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test na emulatorze: nowy edytor (zakładki z ikonami, Cofnij/Ponów, zakres serii, otwarcie arkusza zapisu). */
@RunWith(AndroidJUnit4::class)
class RedesignEditorTest {
    @get:Rule val compose = createComposeRule()

    private fun state(count: Int): MainState {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val s = MainState(AppGraph(app as android.app.Application), Typeface.DEFAULT)
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        compose.runOnIdle {
            s.setPhotoForTest(b)
            s.setPreviewForTest(b)
            if (count > 1) s.setSeriesForTest(List(count) { Uri.parse("content://test/$it") })
        }
        return s
    }

    @Test fun tabsUndoAndSaveSheet() {
        val s = state(1)
        var saveOpened = false
        compose.setContent {
            RetroFilmTheme(dynamicColor = false) {
                EditorScreen(s, onBack = {
                }, onSave = { saveOpened = true }, onOpenLibrary = {}, onSelectPhoto = {}, snackbar = {})
            }
        }
        compose.onNodeWithText("Efekty").performClick().assertIsSelected()
        compose.onNodeWithText("Ramka").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Cofnij").assertIsNotEnabled()
        compose.runOnIdle {
            s.recordHistory()
            s.intensity = 0.5f
            s.recordHistory()
        }
        compose.onNodeWithContentDescription("Cofnij").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Ponów").assertIsEnabled()
        compose.onNodeWithText("Zapisz").performClick()
        compose.runOnIdle { assertTrue(saveOpened) }
    }

    @Test fun seriesScope() {
        val s = state(3)
        compose.setContent {
            RetroFilmTheme(dynamicColor = false) {
                EditorScreen(s, onBack = {}, onSave = {}, onOpenLibrary = {}, onSelectPhoto = {}, snackbar = {})
            }
        }
        compose.onNodeWithText("1 z 3").assertExists()
        compose.onNodeWithText("Cała seria").performClick()
        compose.runOnIdle { assertTrue(s.scopeAll) }
    }
}
