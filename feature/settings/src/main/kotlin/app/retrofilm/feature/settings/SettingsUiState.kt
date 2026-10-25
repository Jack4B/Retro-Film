package app.retrofilm.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.retrofilm.core.ui.ThemeMode
import app.retrofilm.core.ui.supportsDynamicColor

/** Komponenty wymagające atrybucji, z tekstem licencji w assets core:ui. */
enum class ThirdPartyLicense(
    @StringRes val title: Int,
    val author: String,
    val license: String,
    val assetPath: String
) {
    FILM_SIMULATOR(
        title = R.string.license_film_simulator_title,
        author = "YahiaAngelo/Film-Simulator",
        license = "MIT",
        assetPath = "licenses/MIT-Film-Simulator.txt"
    ),
    HALDCLUT(
        title = R.string.license_haldclut_title,
        author = "RawTherapee · Pat David, Pavlov Dmitry, Michael Ezra",
        license = "CC BY-SA 4.0",
        assetPath = "licenses/CC-BY-SA-4.0.txt"
    ),
    CAVEAT(
        title = R.string.license_caveat_title,
        author = "The Caveat Project Authors",
        license = "SIL OFL 1.1",
        assetPath = "licenses/OFL-Caveat.txt"
    ),
    MARCK_SCRIPT(
        title = R.string.license_marck_script_title,
        author = "Denis Masharov, Marck Fogel",
        license = "SIL OFL 1.1",
        assetPath = "licenses/OFL-MarckScript.txt"
    ),
    INDIE_FLOWER(
        title = R.string.license_indie_flower_title,
        author = "The Indie Flower Authors",
        license = "SIL OFL 1.1",
        assetPath = "licenses/OFL-IndieFlower.txt"
    ),
    SACRAMENTO(
        title = R.string.license_sacramento_title,
        author = "Brian J. Bonislawsky (Astigmatic)",
        license = "SIL OFL 1.1",
        assetPath = "licenses/OFL-Sacramento.txt"
    )
}

/** Stan ekranu Ustawienia. Ekran jest bezstanowy – stan trzyma wywołujący. */
@Immutable
data class SettingsUiState(
    /** Motyw (ciemny / jasny / systemowy); `null` ukrywa wybór (np. w zrzutach bez tej opcji). */
    val themeMode: ThemeMode? = null,
    /** Dynamiczne kolory (Material You), domyślnie wyłączone. */
    val dynamicColor: Boolean = false,
    /** Przełącznik widoczny tylko na API 31+. */
    val dynamicColorSupported: Boolean = supportsDynamicColor(),
    /** Wersja aplikacji (BuildConfig). */
    val versionName: String = BuildConfig.APP_VERSION
)

/** Trasy nawigacji modułu feature:settings. */
object SettingsRoutes {
    const val SETTINGS = "ustawienia"
    const val LICENSES = "ustawienia/licencje"

    /** Ekran pobierania filtrów (LutDownloadScreen). */
    const val FILTERS = "ustawienia/filtry"
}
