package app.retrofilm.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
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

    @Test fun ustawieniaHdr() {
        var hdr: Boolean? = null
        compose.setContent {
            RetroFilmTheme {
                SettingsScreen(
                    SettingsUiState(defaultHdrFromPhoto = false, versionName = VERSION),
                    {},
                    {},
                    {},
                    {},
                    onDefaultHdrChange = { hdr = it }
                )
            }
        }
        compose.onNodeWithText("HDR ze zdjęcia").assertIsOff()
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia-hdr.png")
        compose.onNodeWithText("HDR ze zdjęcia").performClick()
        assertEquals(true, hdr)
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

    private fun tools(
        dark: Boolean,
        tools: SettingsToolsState,
        actions: SettingsToolsActions = SettingsToolsActions()
    ) {
        compose.setContent {
            RetroFilmTheme(dynamicColor = false, darkTheme = dark) {
                SettingsScreen(
                    SettingsUiState(defaultHdrFromPhoto = true, tools = tools, versionName = VERSION),
                    {},
                    {},
                    {},
                    {},
                    toolsActions = actions
                )
            }
        }
        scrollTo("settings-hdr-default")
    }

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test fun ustawieniaNarzedziaCiemne() {
        var perf: Boolean? = null
        var exported = false
        tools(
            dark = true,
            SettingsToolsState(perfLogEnabled = false, perfLogCount = 0),
            SettingsToolsActions(
                onPerfLogChange = { perf = it },
                onExportBackup = { exported = true }
            )
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia-narzedzia.png")
        scrollTo("perf-log-export")
        compose.onNodeWithTag("perf-log-switch").assertIsOff()
        compose.onNodeWithTag("perf-log-export").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        scrollTo("perf-log-switch")
        compose.onNodeWithTag("perf-log-switch").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(true, perf)
        scrollTo("backup-export")
        compose.onNodeWithTag("backup-export").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, perf)
        assertTrue(exported)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight-xxhdpi")
    fun ustawieniaNarzedziaJasne() {
        var cleared = false
        tools(
            dark = false,
            SettingsToolsState(perfLogEnabled = true, perfLogCount = 42),
            SettingsToolsActions(onClearPerfLog = {
                cleared =
                    true
            })
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/ustawienia-narzedzia-jasne.png")
        scrollTo("perf-log-clear")
        compose.onNodeWithTag("perf-log-switch").assertIsOn()
        compose.onNodeWithText("Wpisy w logu: 42", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("perf-log-clear").assertIsEnabled().performClick()
        assertTrue(cleared)
    }

    @Test fun przywracanieKopiiCiemne() = restoreDialog(dark = true, "ustawienia-przywracanie.png")

    @Test
    @Config(qualifiers = "w390dp-h844dp-notnight-xxhdpi")
    fun przywracanieKopiiJasne() = restoreDialog(dark = false, "ustawienia-przywracanie-jasne.png")

    private fun restoreDialog(dark: Boolean, name: String) {
        var replace: Boolean? = null
        tools(
            dark,
            SettingsToolsState(restorePrompt = RestorePrompt("0.9.0", 14, 3, 5)),
            SettingsToolsActions(onRestoreConfirm = {
                replace =
                    it
            })
        )
        compose.onNodeWithText("Kopia z wersji 0.9.0", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("restore-dialog").captureRoboImage("src/test/screenshots/$name")
        compose.onNodeWithText("Zastąp").performClick()
        assertEquals(true, replace)
    }

    @Test fun bladKopii() {
        tools(dark = true, SettingsToolsState(notice = ToolsNotice.NewerSchema))
        compose.onNodeWithText("Kopia pochodzi z nowszej wersji", substring = true).assertIsDisplayed()
    }
}
