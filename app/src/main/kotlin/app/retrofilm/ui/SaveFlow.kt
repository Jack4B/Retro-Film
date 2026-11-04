package app.retrofilm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.retrofilm.R
import app.retrofilm.pipeline.ExportPipeline
import kotlinx.coroutines.launch

/**
 * Zapis przez arkusz: „Zapisz” w edytorze otwiera [SaveSheet]; zapis idzie do MediaStore (API 29+, seria = wszystkie
 * zdjęcia) albo przez SAF (API 26–28). Zwraca akcję otwierającą arkusz; arkusz rysuje się sam, gdy jest otwarty.
 */
@Suppress("LongMethod") // tylko okablowanie stanu i launcher-ów, bez logiki
@Composable
internal fun rememberSaveFlow(state: MainState, show: (UiMessage?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<UiMessage?>(null) }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportPipeline.MIME)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                state.savedUris.clear()
                result = state.export { ExportPipeline.saveToUri(context.contentResolver, it, uri) }
                if (result == UiMessage.Saved) state.savedUris += uri
            }
        }
    }
    val toMediaStore: suspend (ByteArray) -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val name = ExportPipeline.fileName(System.currentTimeMillis())
            state.savedUris += ExportPipeline.saveToMediaStore(context.contentResolver, it, name)
        }
    }
    val save: () -> Unit = {
        result = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                state.savedUris.clear()
                result = if (state.uris.size > 1) state.exportAll(context, toMediaStore) else state.export(toMediaStore)
            }
        } else {
            createDoc.launch(ExportPipeline.fileName(System.currentTimeMillis()))
        }
        Unit
    }
    if (open) {
        SaveSheet(
            state,
            result,
            SaveActions(
                onSave = save,
                onRetry = {
                    val failed = state.failedIndices
                    result = null
                    scope.launch { result = state.exportAll(context, toMediaStore, failed) }
                },
                onShare = { share(context, state.savedUris.toList()) },
                onSaveDefaults = {
                    scope.launch {
                        state.saveAsDefaults()
                        show(null)
                    }
                },
                onResetDefaults = { scope.launch { state.resetDefaults() } },
                onDismiss = {
                    open = false
                    result?.let(show)
                    result = null
                }
            )
        )
    }
    return { open = true }
}

/** „Udostępnij”: systemowy wybór aplikacji dla zapisanych plików (uprawnienie do odczytu nadawane odbiorcy). */
private fun share(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.setType(ExportPipeline.MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, context.getString(R.string.save_sheet_share_chooser))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
