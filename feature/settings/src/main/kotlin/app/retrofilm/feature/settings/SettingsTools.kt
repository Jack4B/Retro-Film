package app.retrofilm.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Podsumowanie pliku kopii przed przywróceniem (liczby do okna potwierdzenia). */
@Immutable
data class RestorePrompt(val appVersion: String, val settings: Int, val presets: Int, val favorites: Int)

/** Jednorazowy komunikat sekcji Diagnostyka / Kopia zapasowa (okno z „OK”). */
enum class ToolsNotice(@StringRes val title: Int, @StringRes val body: Int) {
    BackupSaved(R.string.backup_title_done, R.string.backup_saved),
    BackupRestored(R.string.backup_title_done, R.string.backup_restored),
    PerfLogSaved(R.string.perf_log_title_done, R.string.perf_log_saved),
    NotJson(R.string.backup_title_error, R.string.backup_error_not_json),
    NotRetroFilm(R.string.backup_title_error, R.string.backup_error_not_retrofilm),
    NewerSchema(R.string.backup_title_error, R.string.backup_error_newer),
    Corrupt(R.string.backup_title_error, R.string.backup_error_corrupt),
    IoError(R.string.backup_title_error, R.string.backup_error_io)
}

/** Stan narzędzi w Ustawieniach; `null` w [SettingsUiState.tools] ukrywa obie sekcje. */
@Immutable
data class SettingsToolsState(
    val perfLogEnabled: Boolean = false,
    val perfLogCount: Int = 0,
    val restorePrompt: RestorePrompt? = null,
    val notice: ToolsNotice? = null
)

/** Akcje narzędzi (SAF i zapis wykonuje warstwa aplikacji). */
@Immutable
data class SettingsToolsActions(
    val onPerfLogChange: (Boolean) -> Unit = {},
    val onExportPerfLog: () -> Unit = {},
    val onSharePerfLog: () -> Unit = {},
    val onClearPerfLog: () -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onRestoreBackup: () -> Unit = {},
    val onRestoreConfirm: (replace: Boolean) -> Unit = {},
    val onDismiss: () -> Unit = {}
)

internal fun LazyListScope.toolsSections(tools: SettingsToolsState, actions: SettingsToolsActions) {
    item { SectionHeader(stringResource(R.string.settings_section_backup)) }
    item {
        ActionRow(
            Icons.Outlined.Save,
            R.string.backup_export,
            R.string.backup_export_subtitle,
            "backup-export",
            actions.onExportBackup
        )
    }
    item {
        ActionRow(
            Icons.Outlined.Restore,
            R.string.backup_restore,
            R.string.backup_restore_subtitle,
            "backup-restore",
            actions.onRestoreBackup
        )
    }
    item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
    item {
        SwitchRow(
            checked = tools.perfLogEnabled,
            onChange = actions.onPerfLogChange,
            title = R.string.perf_log,
            subtitle = R.string.perf_log_subtitle,
            modifier = Modifier.testTag("perf-log-switch"),
            icon = { Icon(Icons.Outlined.Timer, contentDescription = null) }
        )
    }
    val count: @Composable () -> String = { stringResource(R.string.perf_log_count, tools.perfLogCount) }
    val share: @Composable () -> String = { stringResource(R.string.perf_log_share_subtitle) }
    val clear: @Composable () -> String = { stringResource(R.string.perf_log_clear_subtitle) }
    item {
        ActionRow(
            Icons.Outlined.FileDownload,
            R.string.perf_log_export,
            count,
            "perf-log-export",
            actions.onExportPerfLog,
            tools.perfLogCount > 0
        )
    }
    item {
        ActionRow(
            Icons.Outlined.Share,
            R.string.perf_log_share,
            share,
            "perf-log-share",
            actions.onSharePerfLog,
            tools.perfLogCount > 0
        )
    }
    item {
        ActionRow(
            Icons.Outlined.DeleteSweep,
            R.string.perf_log_clear,
            clear,
            "perf-log-clear",
            actions.onClearPerfLog,
            tools.perfLogCount > 0
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    tag: String,
    onClick: () -> Unit
) = ActionRow(icon, title, { stringResource(subtitle) }, tag, onClick, enabled = true)

@Composable
private fun ActionRow(
    icon: ImageVector,
    @StringRes title: Int,
    subtitle: @Composable () -> String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(subtitle()) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = color,
            leadingIconColor = color,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
    )
}

private const val DISABLED_ALPHA = 0.38f

/** Okna narzędzi: potwierdzenie przywrócenia (Scal / Zastąp / Anuluj) i komunikaty. */
@Composable
internal fun ToolsDialogs(tools: SettingsToolsState, actions: SettingsToolsActions) {
    tools.restorePrompt?.let { p ->
        AlertDialog(
            onDismissRequest = actions.onDismiss,
            modifier = Modifier.testTag("restore-dialog"),
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_summary,
                        p.appVersion.ifEmpty {
                            "?"
                        },
                        p.settings,
                        p.presets,
                        p.favorites
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onRestoreConfirm(false)
                }) { Text(stringResource(R.string.backup_merge)) }
                TextButton(onClick = {
                    actions.onRestoreConfirm(true)
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.backup_cancel)) } }
        )
    }
    tools.notice?.let { n ->
        AlertDialog(
            onDismissRequest = actions.onDismiss,
            title = { Text(stringResource(n.title)) },
            text = { Text(stringResource(n.body)) },
            confirmButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.backup_ok)) } }
        )
    }
}
