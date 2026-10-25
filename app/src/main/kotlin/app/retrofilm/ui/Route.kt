package app.retrofilm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.retrofilm.engine.AppGraph
import app.retrofilm.feature.settings.LicensesScreen
import app.retrofilm.feature.settings.SettingsRoutes
import app.retrofilm.feature.settings.SettingsScreen
import app.retrofilm.feature.settings.SettingsUiState
import app.retrofilm.feature.settings.ThirdPartyLicense
import app.retrofilm.feature.settings.lut.LutDownloadScreen
import kotlinx.coroutines.launch

/** Trasy aplikacji. Nazwy tras są stałe (deep linki / testy); podtrasy ustawień z feature:settings. */
enum class Route(val path: String) {
    Main("main"),
    Settings("settings"),
    Licenses(SettingsRoutes.LICENSES),
    Filters(SettingsRoutes.FILTERS);

    /** Trasa, do której prowadzi „Wstecz”. */
    val parent: Route? get() = when (this) {
        Main -> null
        Settings -> Main
        Licenses, Filters -> Settings
    }
}

/** Minimalna nawigacja bez dodatkowych zależności: Main → Ustawienia → Licencje / Filtry. */
@Composable
fun RetroFilmNavHost(graph: AppGraph, dynamicColor: Boolean, main: @Composable (navigate: (Route) -> Unit) -> Unit) {
    var route by rememberSaveable { mutableStateOf(Route.Main) }
    var license by rememberSaveable { mutableStateOf<ThirdPartyLicense?>(null) }
    var licensesParent by rememberSaveable { mutableStateOf(Route.Settings) }
    val scope = rememberCoroutineScope()
    val parent = if (route == Route.Licenses) licensesParent else route.parent
    val back: () -> Unit = { parent?.let { route = it } }
    parent?.let { BackHandler(onBack = back) }
    when (route) {
        Route.Main -> main { route = it }

        Route.Settings -> SettingsScreen(
            state = SettingsUiState(dynamicColor = dynamicColor),
            onBack = back,
            onDynamicColorChange = { scope.launch { graph.appSettings.setDynamicColor(it) } },
            onOpenFilters = { route = Route.Filters },
            onOpenLicense = {
                license = it
                licensesParent = Route.Settings
                route = Route.Licenses
            }
        )

        Route.Licenses -> LicensesScreen(
            selected = license,
            onBack = back,
            onSelect = { license = if (license == it) null else it }
        )

        Route.Filters -> FiltersRoute(graph, onBack = back, onOpenLicenses = {
            licensesParent = Route.Filters
            route = Route.Licenses
        })
    }
}

/** Ekran filtrów (#16) zasilany stanem z [AppGraph.lutDownloadStateHolder]. Pobieranie: LutDownloadFeature. */
@Composable
private fun FiltersRoute(graph: AppGraph, onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val holder = graph.lutDownloadStateHolder
    val state by holder.state.collectAsState()
    LutDownloadScreen(
        state = state,
        onBack = onBack,
        onDownload = holder::download,
        onCancel = holder::cancel,
        onDelete = holder::delete,
        onDownloadAll = { state.toDownload.forEach { holder.download(it.id) } },
        onOnlyWifiChange = holder::setOnlyWifi,
        onOpenLicenses = onOpenLicenses,
        onClearCacheRequest = holder::onClearCacheRequest,
        onClearCacheConfirm = holder::onClearCacheConfirm,
        onClearCacheDismiss = holder::onClearCacheDismiss
    )
}
