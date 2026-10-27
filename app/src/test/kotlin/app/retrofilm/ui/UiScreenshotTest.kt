package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.PhotoLoader
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Zrzuty prawdziwych ekranów aplikacji do przeglądu UI/UX (workflow ui-screenshots.yml). Bez zmiennej UI_OUT test się
 * pomija, więc zwykły CI go nie uruchamia. Zdjęcie bierze z STOCK_PHOTOS/street_bus.jpg albo rysuje scenę zastępczą.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class UiScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val out: File? = System.getenv("UI_OUT")?.let { File(it).apply { mkdirs() } }

    private fun photo(): Bitmap {
        val file = System.getenv("STOCK_PHOTOS")?.let { File(it, "street_bus.jpg") }
        if (file != null && file.exists()) return PhotoLoader.load({ file.inputStream() })
        val b = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(90, 120, 150))
        c.drawRect(0f, 700f, 900f, 1200f, Paint().apply { color = Color.rgb(120, 100, 80) })
        return b
    }

    private fun state(count: Int) = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT).also {
        it.setPhotoForTest(photo())
        if (count > 1) it.setSeriesForTest(List(count) { i -> Uri.parse("content://test/$i") })
        runBlocking { it.refreshPreview() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun editor(s: MainState) = compose.setContent {
        RetroFilmTheme {
            EditorScreen(s, onBack = {}, onSave = {}, onOpenLibrary = {}, onSelectPhoto = {}, snackbar = {})
        }
    }

    private fun tab(label: String, name: String) {
        compose.onNodeWithText(label).performClick()
        Thread.sleep(SETTLE_MS)
        shot(name)
    }

    @Test fun editorTabs() {
        assumeNotNull(out)
        editor(state(1))
        shot("01-korekty")
        tab("Kadr", "02-kadr")
        tab("Film", "03-film")
        tab("Wykończenie", "04-wykonczenie")
        tab("Ramka", "05-ramka")
    }

    @Test fun seriesAndPicker() {
        assumeNotNull(out)
        editor(state(3))
        shot("10-seria-korekty")
        compose.onNodeWithText("1/3 ▾").performClick()
        shot("11-seria-menu-zdjec")
    }

    private companion object {
        const val SETTLE_MS = 1500L
    }
}
