package app.retrofilm.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.retrofilm.core.ui.RetroFilmTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val VERSION = "0.1.0"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun settings(state: SettingsUiState, onToggle: (Boolean) -> Unit = {}) {
        compose.setContent {
            RetroFilmTheme(dynamicColor = state.dynamicColor) {
                SettingsScreen(state, {}, onToggle, {}, {})
            }
        }
    }

    @Test fun ustawienia() {
        var toggled: Boolean? = null
        settings(SettingsUiState(versionName = VERSION)) { toggled = it }
        compose.onNodeWithText("Dynamiczne kolory (Material You)").assertIsOff()
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia.png")
        compose.onNodeWithText("Dynamiczne kolory (Material You)").performClick()
        assertEquals(true, toggled)
    }

    @Test fun ustawienia_dynamic() {
        settings(SettingsUiState(dynamicColor = true, dynamicColorSupported = true, versionName = VERSION))
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia-dynamic.png")
    }

    @Test fun licencje() {
        compose.setContent {
            RetroFilmTheme { LicensesScreen(ThirdPartyLicense.FILM_SIMULATOR, {}, {}) }
        }
        compose.onNodeWithText("Permission is hereby granted", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia-licencje.png")
    }

    @Test fun dynamicToggleHiddenBelowApi31() {
        settings(SettingsUiState(dynamicColorSupported = false, versionName = VERSION))
        compose.onNodeWithText("Dynamiczne kolory (Material You)").assertDoesNotExist()
    }

    @Test fun defaultsAndLicenseAssetsPresent() {
        assertFalse(SettingsUiState().dynamicColor)
        assertEquals(BuildConfig.APP_VERSION, SettingsUiState().versionName)
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        ThirdPartyLicense.entries.forEach { lic ->
            val text = ctx.assets.open(lic.assetPath).bufferedReader().use { it.readText() }
            assertTrue(lic.assetPath, text.length > 500)
        }
    }

    @Test fun noExifParametersInUi() {
        // Jedyna wzmianka o EXIF to nota prywatności; żadnych parametrów (ISO, przysłona, czas...).
        settings(SettingsUiState(versionName = VERSION))
        listOf("ISO", "Przysłona", "Ogniskowa", "Czas naświetlania").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }
}
