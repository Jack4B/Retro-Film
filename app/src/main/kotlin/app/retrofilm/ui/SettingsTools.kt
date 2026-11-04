package app.retrofilm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.retrofilm.backup.Backup
import app.retrofilm.backup.BackupCodec
import app.retrofilm.backup.BackupError
import app.retrofilm.backup.BackupResult
import app.retrofilm.backup.BackupStore
import app.retrofilm.backup.RestoreMode
import app.retrofilm.engine.AppGraph
import app.retrofilm.feature.settings.RestorePrompt
import app.retrofilm.feature.settings.SettingsToolsActions
import app.retrofilm.feature.settings.SettingsToolsState
import app.retrofilm.feature.settings.ToolsNotice
import app.retrofilm.perf.PerfLog
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stan + akcje sekcji „Kopia zapasowa” i „Diagnostyka” (SAF; bez sieci). */
class SettingsTools(val state: SettingsToolsState, val actions: SettingsToolsActions)

@Composable
fun rememberSettingsTools(graph: AppGraph, perfLogEnabled: Boolean): SettingsTools {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(graph) { BackupStore(context.applicationContext, graph.appSettings) }
    var pending by remember { mutableStateOf<Backup?>(null) }
    var notice by remember { mutableStateOf<ToolsNotice?>(null) }
    var clearTick by remember { mutableIntStateOf(0) }

    val launchers = rememberToolLaunchers(context, store, onNotice = { notice = it }, onPending = { pending = it })
    val today = LocalDate.now().toString()
    val actions = SettingsToolsActions(
        onPerfLogChange = { on ->
            PerfLog.recorder = if (on) PerfLog.ring else null
            scope.launch { graph.appSettings.setPerfLogEnabled(on) }
        },
        onExportPerfLog = { launchers.exportLog.launch("retrofilm-czasy-$today.csv") },
        onSharePerfLog = { sharePerfLog(context) },
        onClearPerfLog = {
            PerfLog.ring.clear()
            clearTick++
        },
        onExportBackup = { launchers.exportBackup.launch("retrofilm-kopia-$today.json") },
        onRestoreBackup = {
            launchers.openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        },
        onRestoreConfirm = { replace ->
            val b = pending
            pending = null
            if (b != null) {
                scope.launch {
                    store.restore(b, if (replace) RestoreMode.Replace else RestoreMode.Merge)
                    notice = ToolsNotice.BackupRestored
                }
            }
        },
        onDismiss = {
            pending = null
            notice = null
        }
    )
    val state = SettingsToolsState(
        perfLogEnabled = perfLogEnabled,
        perfLogCount = clearTick.let { PerfLog.ring.size },
        restorePrompt = pending?.let { b ->
            b.summary().let { RestorePrompt(b.appVersion, it.settings, it.presets, it.favorites) }
        },
        notice = notice
    )
    return SettingsTools(state, actions)
}

/** Launchery SAF (CreateDocument / OpenDocument) – plik wybiera użytkownik, bez uprawnień do pamięci. */
private class ToolLaunchers(
    val exportBackup: ManagedActivityResultLauncher<String, Uri?>,
    val openBackup: ManagedActivityResultLauncher<Array<String>, Uri?>,
    val exportLog: ManagedActivityResultLauncher<String, Uri?>
)

@Composable
private fun rememberToolLaunchers(
    context: Context,
    store: BackupStore,
    onNotice: (ToolsNotice) -> Unit,
    onPending: (Backup) -> Unit
): ToolLaunchers {
    val scope = rememberCoroutineScope()
    val exportBackup = rememberLauncherForActivityResult(CreateDocument(BackupCodec.MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { onNotice(if (store.export(uri)) ToolsNotice.BackupSaved else ToolsNotice.IoError) }
    }
    val openBackup = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = store.read(uri)) {
                null -> onNotice(ToolsNotice.IoError)
                is BackupResult.Invalid -> onNotice(r.error.notice())
                is BackupResult.Ok -> onPending(r.backup)
            }
        }
    }
    val exportLog = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = writeText(context, uri, PerfLog.ring.toCsv())
            onNotice(if (ok) ToolsNotice.PerfLogSaved else ToolsNotice.IoError)
        }
    }
    return ToolLaunchers(exportBackup, openBackup, exportLog)
}

private fun BackupError.notice() = when (this) {
    BackupError.NotJson -> ToolsNotice.NotJson
    BackupError.NotRetroFilm -> ToolsNotice.NotRetroFilm
    BackupError.NewerSchema -> ToolsNotice.NewerSchema
    BackupError.Corrupt -> ToolsNotice.Corrupt
}

private suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Arkusz udostępniania z CSV jako tekstem (bez FileProvidera). */
private fun sharePerfLog(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "RetroFilm – log czasów")
        putExtra(Intent.EXTRA_TEXT, PerfLog.ring.toCsv())
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
