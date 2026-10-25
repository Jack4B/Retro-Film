package app.retrofilm.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.ui.library.FilmLibraryScreen
import kotlinx.coroutines.launch

private const val MAX_PICKED = 30

/**
 * Korzeń ścieżki: Start (galeria: jedno lub kilka zdjęć) → Edytor (Film / Ramka) → zapis jednym dotknięciem.
 * Photo Picker i zapis bez uprawnień do pamięci (MediaStore od API 29, SAF na API 26–28).
 */
@Composable
fun MainScreen(state: MainState, onSettings: () -> Unit) {
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
    LaunchedEffect(Unit) { state.library.observeDownloads() }
    LaunchedEffect(state.photo, state.film, state.frame, state.intensity, state.adjustments, state.previewDetail) {
        state.refreshPreview()
    }
    val editing = state.photo != null
    AutoOpenGallery(editing, onPick)
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    if (editing) BackHandler(onBack = state::closePhoto)
    if (editing && showLibrary) BackHandler { showLibrary = false }
    if (editing && showLibrary) {
        FilmLibraryScreen(state, onBack = { showLibrary = false })
    } else if (editing) {
        EditorScreen(
            state,
            onBack = state::closePhoto,
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

/** Galeria otwiera się sama przy pierwszym wejściu na Start; po anulowaniu zostaje ekran z przyciskami. */
@Composable
private fun AutoOpenGallery(editing: Boolean, onPick: () -> Unit) {
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoOpened && !editing) {
            autoOpened = true
            onPick()
        }
    }
}
