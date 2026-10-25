package app.retrofilm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.retrofilm.R

/** Trasy aplikacji. Nazwy tras są stałe (deep linki / testy). */
enum class Route(val path: String) {
    Main("main"),
    Settings("settings")
}

/** Minimalna nawigacja bez dodatkowych zależności: stos jednej trasy nad [Route.Main]. */
@Composable
fun RetroFilmNavHost(main: @Composable (navigate: (Route) -> Unit) -> Unit) {
    var route by rememberSaveable { mutableStateOf(Route.Main) }
    when (route) {
        Route.Main -> main { route = it }

        Route.Settings -> {
            BackHandler { route = Route.Main }
            // Ekran Lumena (bezstanowy) podpiąć tu jedną linią zamiast placeholdera, np.:
            // SettingsScreen(onBack = { route = Route.Main })
            SettingsPlaceholder()
        }
    }
}

@Composable
private fun SettingsPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.settings_placeholder))
    }
}
