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

/**
 * Korzeń ścieżki: Start (wybór zdjęcia) → Edytor (Film / Ramka) → zapis jednym dotknięciem.
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { show(state.load(context, uri)) }
    }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportPipeline.MIME)
    ) { uri ->
        if (uri != null) {
            scope.launch { show(state.export { ExportPipeline.saveToUri(context.contentResolver, it, uri) }) }
        }
    }
    val onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val onSave: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                show(
                    state.export {
                        val name = ExportPipeline.fileName(System.currentTimeMillis())
                        ExportPipeline.saveToMediaStore(context.contentResolver, it, name)
                    }
                )
            }
        } else {
            createDoc.launch(ExportPipeline.fileName(System.currentTimeMillis()))
        }
    }
    LaunchedEffect(Unit) { state.library.observeFavorites() }
    LaunchedEffect(Unit) { state.library.observeDownloads() }
    LaunchedEffect(state.photo, state.film, state.frame, state.intensity, state.adjustments) {
        state.refreshPreview()
    }
    val editing = state.photo != null
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
            snackbar = { SnackbarHost(snackbar) }
        )
    } else {
        StartScreen(state, onPick = onPick, onSettings = onSettings, snackbar = { SnackbarHost(snackbar) })
    }
}
