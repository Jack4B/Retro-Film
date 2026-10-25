package app.retrofilm.feature.settings.lut

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.retrofilm.core.ui.lut.FailReason
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.PackState
import kotlin.math.roundToInt

/** Wspólna nota prywatności (też na ekranie Eksport). */
const val NETWORK_PRIVACY_NOTE = "Internet służy tylko do pobierania filtrów. Zdjęcia nie opuszczają telefonu."

@Immutable
data class LutDownloadUiState(
    val packs: List<LutPack>,
    val freeSpaceBytes: Long,
    /** Ustawienie UI, domyślnie włączone. */
    val onlyWifi: Boolean = true,
    /** Brak jakiegokolwiek połączenia. */
    val offline: Boolean = false,
    /** Połączenie jest, ale nie Wi‑Fi. */
    val wifiAvailable: Boolean = true
) {
    val toDownload: List<LutPack> get() = packs.filter { !it.bundled && it.state != PackState.Ready }
    val missingBytes: Long get() = toDownload.sumOf { it.sizeBytes }
    val downloadedBytes: Long get() = packs.filter { !it.bundled && it.state == PackState.Ready }.sumOf { it.sizeBytes }
    val readyCount: Int get() = packs.count { it.bundled || it.state == PackState.Ready }
    val waitingForWifi: Boolean get() = onlyWifi && !wifiAvailable && !offline
}

/** Komunikat błędu po polsku. */
fun FailReason.message(): String = when (this) {
    FailReason.NETWORK -> "Brak połączenia"
    FailReason.CHECKSUM -> "Plik uszkodzony – odrzucono, spróbuj ponownie"
    FailReason.STORAGE -> "Za mało miejsca"
}

private const val BYTES_IN_KB = 1024.0
private const val PERCENT = 100

/** Rozmiar po polsku, np. „820 KB”, „215 MB”, „1,2 GB”. */
fun formatBytes(bytes: Long): String {
    val kb = BYTES_IN_KB
    val mb = kb * BYTES_IN_KB
    val gb = mb * BYTES_IN_KB
    return when {
        bytes <= 0L -> "0 MB"
        bytes >= gb -> String.format(java.util.Locale.forLanguageTag("pl"), "%.1f GB", bytes / gb)
        bytes >= mb -> "${(bytes / mb).roundToInt()} MB"
        else -> "${(bytes / kb).roundToInt().coerceAtLeast(1)} KB"
    }
}

/**
 * Ekran „Filtry (LUT)”: pobieranie pakietu z GitHub Release `luts-v1`. Bezstanowy.
 * „Pobierz wszystkie” = [onDownload] dla każdego niegotowego pakietu (robi to wywołujący).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LutDownloadScreen(
    state: LutDownloadUiState,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onOnlyWifiChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val busy = state.packs.any { it.state is PackState.Downloading }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = { Text("Filtry (LUT)", maxLines = 1) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                GroupCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${state.readyCount} z ${state.packs.size} filmów gotowych",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Do pobrania: ${formatBytes(state.missingBytes)} · pobrane: " +
                                "${formatBytes(
                                    state.downloadedBytes
                                )} · wolne miejsce: ${formatBytes(state.freeSpaceBytes)}",
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.toDownload.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Wszystkie filmy pobrane – działają bez internetu", color = cs.primary)
                            }
                        } else {
                            Button(
                                onClick = onDownloadAll,
                                enabled = !busy && !state.offline,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Pobierz wszystkie (~${formatBytes(state.missingBytes)})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            item(key = "wifi") {
                GroupCard {
                    ListItem(
                        headlineContent = { Text("Tylko przez Wi‑Fi") },
                        supportingContent = { Text("Nie zużywa danych komórkowych") },
                        leadingContent = { Icon(Icons.Outlined.Wifi, null) },
                        trailingContent = { Switch(checked = state.onlyWifi, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 56.dp).toggleable(
                            value = state.onlyWifi,
                            role = Role.Switch,
                            onValueChange = onOnlyWifiChange
                        )
                    )
                }
            }
            item(key = "privacy") {
                Notice(icon = { Icon(Icons.Outlined.Lock, null) }, text = NETWORK_PRIVACY_NOTE)
            }
            if (state.offline || state.waitingForWifi) {
                item(key = "network") {
                    Notice(
                        icon = { Icon(if (state.offline) Icons.Outlined.CloudOff else Icons.Outlined.Wifi, null) },
                        text = if (state.offline) {
                            "Jesteś offline. Filmy oznaczone „W aplikacji” i pobrane działają bez internetu. " +
                                "Pobieranie wznowi się po połączeniu."
                        } else {
                            "Czekam na Wi‑Fi. Pobieranie ruszy po połączeniu z Wi‑Fi " +
                                "(albo wyłącz „Tylko przez Wi‑Fi”)."
                        }
                    )
                }
            }
            item(key = "films-label") {
                Text(
                    "FILMY",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() }
                )
            }
            items(state.packs, key = { it.id }) { pack ->
                PackRow(pack, state.waitingForWifi, onDownload, onCancel, onDelete)
            }
            item(key = "licenses") {
                GroupCard {
                    ListItem(
                        headlineContent = { Text("Licencje i autorzy") },
                        supportingContent = {
                            Text("LUT-y: CC BY-SA 4.0 · RawTherapee Film Simulation Collection")
                        },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 56.dp)
                            .clickable(onClickLabel = "Otwórz licencje i autorów", onClick = onOpenLicenses)
                    )
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: LutPack,
    waitingForWifi: Boolean,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val st = pack.state
    val status = statusText(pack, waitingForWifi)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = "${pack.displayName}, $status"
                        if (st is PackState.Failed) liveRegion = LiveRegionMode.Polite
                    }
                ) {
                    Text(pack.displayName, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (st is PackState.Failed && !pack.bundled) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = cs.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            status,
                            fontSize = 13.sp,
                            color = when {
                                pack.bundled || st == PackState.Ready -> cs.primary
                                st is PackState.Failed -> cs.error
                                else -> cs.onSurfaceVariant
                            }
                        )
                    }
                }
                if (!pack.bundled) PackAction(pack, onDownload, onCancel, onDelete)
            }
            if (st is PackState.Downloading && !pack.bundled) {
                val pct = (st.progress * 100).roundToInt()
                LinearProgressIndicator(
                    progress = { st.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Postęp pobierania ${pack.displayName}"
                            stateDescription = if (waitingForWifi) "Czeka na Wi‑Fi, $pct procent" else "$pct procent"
                            progressBarRangeInfo = ProgressBarRangeInfo(st.progress.coerceIn(0f, 1f), 0f..1f)
                        }
                )
            }
        }
    }
}

private fun statusText(pack: LutPack, waitingForWifi: Boolean): String {
    val st = pack.state
    val size = formatBytes(pack.sizeBytes)
    return when {
        pack.bundled -> "W aplikacji"
        st is PackState.Downloading && waitingForWifi -> "Czeka na Wi‑Fi · $size"
        st is PackState.Downloading -> "Pobieranie ${(st.progress * PERCENT).roundToInt()}% · $size"
        st is PackState.Failed -> st.reason.message()
        st == PackState.Ready -> "Pobrany · $size"
        else -> "Do pobrania · $size"
    }
}

@Composable
private fun PackAction(
    pack: LutPack,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val size = formatBytes(pack.sizeBytes)
    when (pack.state) {
        PackState.NotDownloaded -> IconButton(onClick = { onDownload(pack.id) }) {
            Icon(Icons.Outlined.Download, "Pobierz ${pack.displayName} ($size)", tint = cs.primary)
        }

        is PackState.Downloading -> IconButton(onClick = { onCancel(pack.id) }) {
            Icon(Icons.Outlined.Close, "Anuluj pobieranie ${pack.displayName}")
        }

        PackState.Ready -> IconButton(onClick = { onDelete(pack.id) }) {
            Icon(Icons.Outlined.Delete, "Usuń ${pack.displayName}, zwolni $size")
        }

        is PackState.Failed -> IconButton(onClick = { onDownload(pack.id) }) {
            Icon(Icons.Outlined.Refresh, "Spróbuj ponownie: ${pack.displayName}", tint = cs.primary)
        }
    }
}

@Composable
private fun Notice(icon: @Composable () -> Unit, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
