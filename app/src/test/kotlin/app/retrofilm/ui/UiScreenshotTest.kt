package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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
import java.io.File
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
 * pomija, więc zwykły CI go nie uruchamia. Zdjęcie to własna, narysowana scena.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class UiScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val out: File? = System.getenv("UI_OUT")?.let { File(it).apply { mkdirs() } }

    /** Ślad kroków dopisywany od razu na dysk, żeby po awarii natywnej było widać, gdzie test padł. */
    private fun trace(step: String) {
        out?.let { File(it, "trace.txt").appendText("$step\n") }
    }

    /** Własna scena (bez cudzych zdjęć, więc bez kłopotów z licencją): zachód słońca nad jeziorem i górami. */
    private fun photo(): Bitmap {
        val w = 900
        val h = 1200
        val horizon = h * 0.62f
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val sky = intArrayOf(Color.rgb(40, 70, 130), Color.rgb(220, 130, 110), Color.rgb(250, 200, 130))
        p.shader = LinearGradient(0f, 0f, 0f, horizon, sky, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), horizon, p)
        p.shader = null
        p.color = Color.rgb(255, 238, 195)
        c.drawCircle(w * 0.68f, horizon - 70f, 72f, p)
        val ridges = listOf(
            Color.rgb(120, 90, 110) to
                floatArrayOf(0.0f, 0.30f, 0.18f, 0.38f, 0.34f, 0.27f, 0.55f, 0.40f, 0.78f, 0.28f, 1f, 0.36f),
            Color.rgb(60, 50, 80) to floatArrayOf(0.0f, 0.12f, 0.22f, 0.20f, 0.45f, 0.10f, 0.70f, 0.22f, 1f, 0.14f)
        )
        for ((color, pts) in ridges) {
            val path = Path().apply {
                moveTo(0f, horizon)
                for (i in pts.indices step 2) lineTo(pts[i] * w, horizon - pts[i + 1] * 420f)
                lineTo(w.toFloat(), horizon)
                close()
            }
            p.color = color
            c.drawPath(path, p)
        }
        p.shader =
            LinearGradient(
                0f,
                horizon,
                0f,
                h.toFloat(),
                Color.rgb(205, 145, 125),
                Color.rgb(25, 45, 80),
                Shader.TileMode.CLAMP
            )
        c.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.argb(150, 255, 235, 190)
        for (i in 0 until 9) {
            c.drawRect(
                w * 0.68f - 70f + i * 8f,
                horizon + 20f + i * 34f,
                w * 0.68f + 70f - i * 8f,
                horizon + 30f + i * 34f,
                p
            )
        }
        return b
    }

    private fun state(count: Int): MainState {
        trace("state start")
        val s = MainState(AppGraph(RuntimeEnvironment.getApplication()), Typeface.DEFAULT)
        val photo = photo()
        s.setPhotoForTest(photo)
        s.setPreviewForTest(photo)
        trace("photo set")
        if (count > 1) s.setSeriesForTest(List(count) { i -> Uri.parse("content://test/$i") })
        trace("preview ready")
        return s
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        trace("idle $name")
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        trace("captured $name")
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun editor(s: MainState, dark: Boolean = true) {
        compose.setContent {
            RetroFilmTheme(dynamicColor = false, darkTheme = dark) {
                EditorScreen(s, onBack = {}, onSave = {}, onOpenLibrary = {}, onSelectPhoto = {}, snackbar = {})
            }
        }
        trace("content set")
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

    @Test fun lightTheme() {
        assumeNotNull(out)
        editor(state(1), dark = false)
        shot("20-jasny-korekty")
        tab("Film", "21-jasny-film")
    }

    @Test fun individualEdit() {
        assumeNotNull(out)
        val s = state(3)
        s.setIndividual(true)
        editor(s)
        shot("30-osobna-edycja")
        compose.onNodeWithText("✎ 1/3 ▾").performClick()
        shot("31-osobna-edycja-menu")
    }

    @Test fun landscape() {
        assumeNotNull(out)
        RuntimeEnvironment.setQualifiers("+w844dp-h390dp")
        editor(state(3))
        shot("40-poziomo-korekty")
        tab("Film", "41-poziomo-film")
    }

    private companion object {
        const val SETTLE_MS = 1500L
    }
}
