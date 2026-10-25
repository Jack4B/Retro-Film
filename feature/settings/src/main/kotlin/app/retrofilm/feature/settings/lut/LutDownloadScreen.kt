@file:Suppress("MatchingDeclarationName") // stan + ekran w jednym pliku

package app.retrofilm.feature.settings.lut

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import app.retrofilm.feature.settings.R
import kotlin.math.roundToInt

@Immutable
data class LutDownloadUiState(
    val packs: List<LutPack>,
    val freeSpaceBytes: Long,
    /** Ustawienie UI, domyślnie włączone. */
    val onlyWifi: Boolean = true,
    /** Brak jakiegokolwiek połączenia. */
    val offline: Boolean = false,
    /** Połączenie jest, ale nie Wi‑Fi. */
    val wifiAvailable: Boolean = true,
    /** [app.retrofilm.core.ui.lut.LutCacheInfo.downloadedBytes]: suma pobranych PNG, per film. */
    val downloadedBytes: Long = packs.filter { !it.bundled && it.state == PackState.Ready }.sumOf { it.sizeBytes },
    /** [app.retrofilm.core.ui.lut.LutCacheInfo.cacheBytes]: rozpakowany cache (np. 256³ ≈ 50 MB na film). */
    val cacheBytes: Long = 0L,
    /** Widoczny dialog potwierdzenia „Wyczyść pamięć podręczną”. */
    val showClearCacheDialog: Boolean = false
) {
    val toDownload: List<LutPack> get() = packs.filter { !it.bundled && it.state != PackState.Ready }
    val missingBytes: Long get() = toDownload.sumOf { it.sizeBytes }
    val readyCount: Int get() = packs.count { it.bundled || it.state == PackState.Ready }
    val waitingForWifi: Boolean get() = onlyWifi && !wifiAvailable && !offline
}

/** Komunikat błędu po polsku (zasób strings.xml). */
@StringRes
fun FailReason.message(): Int = when (this) {
    FailReason.NETWORK -> R.string.lut_fail_network
    FailReason.CHECKSUM -> R.string.lut_fail_checksum
    FailReason.STORAGE -> R.string.lut_fail_storage
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
    modifier: Modifier = Modifier,
    onClearCacheRequest: () -> Unit = {},
    onClearCacheConfirm: () -> Unit = {},
    onClearCacheDismiss: () -> Unit = {}
) {
    if (state.showClearCacheDialog) {
        ClearCacheDialog(state.cacheBytes, onClearCacheConfirm, onClearCacheDismiss)
    }
    val cs = MaterialTheme.colorScheme
    val busy = state.packs.any { it.state is PackState.Downloading }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.lut_back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.lut_title),
                        maxLines = 1,
                        modifier = Modifier.semantics { heading() }
                    )
                }
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
                            pluralStringResource(
                                R.plurals.lut_ready_count,
                                state.packs.size,
                                state.readyCount,
                                state.packs.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.lut_to_download, formatBytes(state.missingBytes)),
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SizeLine(stringResource(R.string.lut_downloaded_size), state.downloadedBytes)
                        SizeLine(stringResource(R.string.lut_cache_size), state.cacheBytes)
                        SizeLine(stringResource(R.string.lut_free_space), state.freeSpaceBytes)
                        if (state.toDownload.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.lut_all_ready), color = cs.primary)
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
                                    stringResource(R.string.lut_download_all, formatBytes(state.missingBytes)),
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
                        headlineContent = { Text(stringResource(R.string.lut_only_wifi)) },
                        supportingContent = { Text(stringResource(R.string.lut_only_wifi_subtitle)) },
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
                Notice(icon = { Icon(Icons.Outlined.Lock, null) }, text = stringResource(R.string.lut_privacy_note))
            }
            if (state.offline || state.waitingForWifi) {
                item(key = "network") {
                    Notice(
                        icon = { Icon(if (state.offline) Icons.Outlined.CloudOff else Icons.Outlined.Wifi, null) },
                        text = stringResource(if (state.offline) R.string.lut_offline else R.string.lut_waiting_wifi)
                    )
                }
            }
            item(key = "cache") {
                val cacheSize = formatBytes(state.cacheBytes)
                val clearLabel = stringResource(R.string.lut_clear_cache_label, cacheSize)
                GroupCard {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lut_clear_cache)) },
                            supportingContent = {
                                Text(stringResource(R.string.lut_clear_cache_subtitle, cacheSize))
                            },
                            leadingContent = { Icon(Icons.Outlined.DeleteSweep, null) },
                            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                            modifier = Modifier.heightIn(min = 56.dp).clickable(
                                enabled = state.cacheBytes > 0L,
                                role = Role.Button,
                                onClickLabel = clearLabel,
                                onClick = onClearCacheRequest
                            )
                        )
                    }
                }
            }
            item(key = "films-label") {
                Text(
                    stringResource(R.string.lut_films_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() }
                )
            }
            items(state.packs, key = { it.id }) { pack ->
                PackRow(pack, state.waitingForWifi, onDownload, onCancel, onDelete)
            }
            item(key = "licenses") {
                val licensesLabel = stringResource(R.string.lut_licenses_label)
                GroupCard {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lut_licenses)) },
                        supportingContent = {
                            Text(stringResource(R.string.lut_licenses_subtitle))
                        },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                        modifier = Modifier.heightIn(min = 56.dp)
                            .clickable(onClickLabel = licensesLabel, role = Role.Button, onClick = onOpenLicenses)
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
    val packCd = stringResource(R.string.lut_pack_cd, pack.displayName, status)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = packCd
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
                val progressCd = stringResource(R.string.lut_progress_cd, pack.displayName)
                val progressState = if (waitingForWifi) {
                    stringResource(R.string.lut_progress_wifi_state, pct)
                } else {
                    stringResource(R.string.lut_progress_state, pct)
                }
                LinearProgressIndicator(
                    progress = { st.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .clearAndSetSemantics {
                            contentDescription = progressCd
                            stateDescription = progressState
                            progressBarRangeInfo = ProgressBarRangeInfo(st.progress.coerceIn(0f, 1f), 0f..1f)
                        }
                )
            }
        }
    }
}

@Composable
private fun statusText(pack: LutPack, waitingForWifi: Boolean): String {
    val st = pack.state
    val size = formatBytes(pack.sizeBytes)
    return when {
        pack.bundled -> stringResource(R.string.lut_status_bundled)

        st is PackState.Downloading && waitingForWifi -> stringResource(R.string.lut_status_waiting, size)

        st is PackState.Downloading ->
            stringResource(R.string.lut_status_downloading, (st.progress * PERCENT).roundToInt(), size)

        st is PackState.Failed -> stringResource(st.reason.message())

        st == PackState.Ready -> stringResource(R.string.lut_status_ready, size)

        else -> stringResource(R.string.lut_status_not_downloaded, size)
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
            Icon(
                Icons.Outlined.Download,
                stringResource(R.string.lut_action_download, pack.displayName, size),
                tint = cs.primary
            )
        }

        is PackState.Downloading -> IconButton(onClick = { onCancel(pack.id) }) {
            Icon(Icons.Outlined.Close, stringResource(R.string.lut_action_cancel, pack.displayName))
        }

        PackState.Ready -> IconButton(onClick = { onDelete(pack.id) }) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.lut_action_delete, pack.displayName, size))
        }

        is PackState.Failed -> IconButton(onClick = { onDownload(pack.id) }) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.lut_action_retry, pack.displayName), tint = cs.primary)
        }
    }
}

@Composable
private fun SizeLine(label: String, bytes: Long) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ClearCacheDialog(cacheBytes: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val size = formatBytes(cacheBytes)
    val confirmCd = stringResource(R.string.lut_dialog_confirm_cd, size)
    val dismissCd = stringResource(R.string.lut_dialog_dismiss_cd)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteSweep, null) },
        title = { Text(stringResource(R.string.lut_dialog_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Text(stringResource(R.string.lut_dialog_text, size))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = confirmCd
                }
            ) { Text(stringResource(R.string.lut_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = dismissCd
                }
            ) { Text(stringResource(R.string.lut_dialog_dismiss)) }
        }
    )
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
