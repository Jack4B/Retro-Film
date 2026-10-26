package app.retrofilm.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.retrofilm.R
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.ui.library.FilmLibraryScreen
import kotlinx.coroutines.launch

private const val MAX_PICKED = 30

/**
 * Korzeń ścieżki: Start (przyciski: galeria albo pliki; wybór zdjęć nie otwiera się sam) → Edytor → zapis jednym
 * dotknięciem. Photo Picker i zapis bez uprawnień do pamięci (MediaStore od API 29, SAF na API 26–28).
 */
@Composable
fun MainScreen(state: MainState, onSettings: () -> Unit, onDownloadFilms: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val texts = UiTexts.from(context)
    fun show(msg: UiMessage?) {
        msg?.let { scope.launch { snackbar.showSnackbar(texts.of(it)) } }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch { show(state.loadAll(context, uris)) }
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { show(state.loadAll(context, uris)) }
    }
    val onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val onPickFiles = { filesPicker.launch(arrayOf("image/*")) }
    val onSave = rememberSaveAction(state, ::show)
    LaunchedEffect(Unit) { state.library.observeFavorites() }
    LaunchedEffect(Unit) { state.presetBook.load(context) }
    LaunchedEffect(Unit) { state.library.observeDownloads() }
    LaunchedEffect(
        state.photo,
        state.film,
        state.frame,
        state.intensity,
        state.adjustments,
        state.previewDetail,
        state.geometry
    ) {
        state.refreshPreview()
    }
    val editing = state.photo != null
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val requestExit = rememberExitGuard(state, editing)
    if (editing && showLibrary) BackHandler { showLibrary = false }
    if (editing && showLibrary) {
        FilmLibraryScreen(state, onBack = { showLibrary = false })
    } else if (editing) {
        EditorScreen(
            state,
            onBack = requestExit,
            onSave = onSave,
            onOpenLibrary = { showLibrary = true },
            onSelectPhoto = { i -> scope.launch { show(state.select(context, i)) } },
            snackbar = { SnackbarHost(snackbar) }
        )
    } else {
        StartScreen(
            state,
            onPick = onPick,
            onPickFiles = onPickFiles,
            onSettings = onSettings,
            onDownloadFilms = onDownloadFilms,
            snackbar = { SnackbarHost(snackbar) }
        )
    }
}

/** Zapis jednym dotknięciem: MediaStore od API 29 (seria, gdy jest kilka zdjęć), SAF na API 26-28. */
@Composable
private fun rememberSaveAction(state: MainState, show: (UiMessage?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportPipeline.MIME)
    ) { uri ->
        if (uri != null) {
            scope.launch { show(state.export { ExportPipeline.saveToUri(context.contentResolver, it, uri) }) }
        }
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val toMediaStore: suspend (ByteArray) -> Unit = {
                val name = ExportPipeline.fileName(System.currentTimeMillis())
                ExportPipeline.saveToMediaStore(context.contentResolver, it, name)
            }
            scope.launch {
                show(if (state.uris.size > 1) state.exportAll(context, toMediaStore) else state.export(toMediaStore))
            }
        } else {
            createDoc.launch(ExportPipeline.fileName(System.currentTimeMillis()))
        }
    }
}

/**
 * Wyjście z edycji: bez zmian od wczytania albo ostatniego zapisu zamyka od razu, z niezapisanymi zmianami najpierw
 * pyta. Zwraca akcję dla przycisku wstecz (systemowego i strzałki w pasku).
 */
@Composable
private fun rememberExitGuard(state: MainState, editing: Boolean): () -> Unit {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val requestExit = { if (state.hasUnsavedEdits) confirmExit = true else state.closePhoto() }
    if (editing) BackHandler(onBack = requestExit)
    if (confirmExit && editing) {
        ExitDialog(onStay = { confirmExit = false }, onExit = {
            confirmExit = false
            state.closePhoto()
        })
    }
    return requestExit
}

/** Pytanie przy wyjściu z edycji z niezapisanymi zmianami. */
@Composable
private fun ExitDialog(onStay: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.exit_title)) },
        text = { Text(stringResource(R.string.exit_message)) },
        confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.exit_confirm)) } },
        dismissButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.exit_stay)) } }
    )
}
