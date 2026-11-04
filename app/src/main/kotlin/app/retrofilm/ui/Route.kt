package app.retrofilm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.retrofilm.core.ui.ThemeMode
import app.retrofilm.engine.AppGraph
import app.retrofilm.feature.settings.LicensesScreen
import app.retrofilm.feature.settings.SettingsRoutes
import app.retrofilm.feature.settings.SettingsScreen
import app.retrofilm.feature.settings.SettingsUiState
import app.retrofilm.feature.settings.ThirdPartyLicense
import app.retrofilm.feature.settings.lut.LutDownloadScreen
import app.retrofilm.perf.PerfLog
import app.retrofilm.pipeline.SaveBackend
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
fun RetroFilmNavHost(
    graph: AppGraph,
    dynamicColor: Boolean,
    themeMode: ThemeMode,
    main: @Composable (navigate: (Route) -> Unit) -> Unit
) {
    var route by rememberSaveable { mutableStateOf(Route.Main) }
    var license by rememberSaveable { mutableStateOf<ThirdPartyLicense?>(null) }
    var licensesParent by rememberSaveable { mutableStateOf(Route.Settings) }
    var filtersParent by rememberSaveable { mutableStateOf(Route.Settings) }
    val scope = rememberCoroutineScope()
    val defaultHdr by graph.appSettings.defaultHdrFromPhoto.collectAsState(initial = false)
    val perfLogOn by graph.appSettings.perfLogEnabled.collectAsState(initial = false)
    LaunchedEffect(perfLogOn) { PerfLog.recorder = if (perfLogOn) PerfLog.ring else null }
    val gpuSave by graph.appSettings.gpuSave.collectAsState(initial = true)
    LaunchedEffect(gpuSave) { SaveBackend.gpuEnabled = gpuSave }
    val parent = when (route) {
        Route.Licenses -> licensesParent
        Route.Filters -> filtersParent
        else -> route.parent
    }
    val back: () -> Unit = { parent?.let { route = it } }
    parent?.let { BackHandler(onBack = back) }
    when (route) {
        Route.Main -> main {
            if (it == Route.Filters) filtersParent = Route.Main
            route = it
        }

        Route.Settings -> SettingsRoute(
            graph,
            dynamicColor,
            themeMode,
            defaultHdr,
            perfLogOn to gpuSave,
            back,
            onOpenFilters = {
                filtersParent = Route.Settings
                route = Route.Filters
            },
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

@Composable
@Suppress("LongParameterList") // trasa spina stan z kilku źródeł; ekran pozostaje bezstanowy
private fun SettingsRoute(
    graph: AppGraph,
    dynamicColor: Boolean,
    themeMode: ThemeMode,
    defaultHdr: Boolean,
    diagnostics: Pair<Boolean, Boolean>,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenLicense: (ThirdPartyLicense) -> Unit
) {
    val scope = rememberCoroutineScope()
    val (perfLogOn, gpuSave) = diagnostics
    val tools = rememberSettingsTools(graph, perfLogOn, gpuSave)
    val frameOutward by graph.appSettings.frameOutward.collectAsState(initial = true)
    SettingsScreen(
        state = SettingsUiState(
            dynamicColor = dynamicColor,
            themeMode = themeMode,
            defaultHdrFromPhoto = defaultHdr,
            frameOutward = frameOutward,
            tools = tools.state
        ),
        onFrameOutwardChange = { scope.launch { graph.appSettings.setFrameOutward(it) } },
        onDefaultHdrChange = { scope.launch { graph.appSettings.setDefaultHdrFromPhoto(it) } },
        onBack = onBack,
        onDynamicColorChange = { scope.launch { graph.appSettings.setDynamicColor(it) } },
        onThemeModeChange = { scope.launch { graph.appSettings.setThemeMode(it) } },
        onOpenFilters = onOpenFilters,
        onOpenLicense = onOpenLicense,
        toolsActions = tools.actions
    )
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
