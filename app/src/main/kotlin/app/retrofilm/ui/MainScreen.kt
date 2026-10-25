package app.retrofilm.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.pipeline.ExportPipeline
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Gap = 12.dp
private val Pad = 16.dp
private val PreviewMax = 420.dp
private val MinTouch = 48.dp
private const val PERCENT = 100

/** Ekran testowej ścieżki: Photo Picker → film → ramka/podpis → eksport JPEG. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val onExport: () -> Unit = {
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
    LaunchedEffect(state.photo, state.film, state.frame) { state.refreshPreview() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_open_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(Pad),
            verticalArrangement = Arrangement.spacedBy(Gap)
        ) {
            Actions(
                busy = state.busy,
                canExport = state.photo != null,
                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onExport = onExport
            )
            PreviewArea(state)
            FilmPicker(state)
            FramePanel(state)
        }
    }
}

@Composable
private fun Actions(busy: Boolean, canExport: Boolean, onPick: () -> Unit, onExport: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onPick, enabled = !busy) { Text(stringResource(R.string.action_pick)) }
        Button(onClick = onExport, enabled = !busy && canExport) { Text(stringResource(R.string.action_export)) }
    }
    if (busy) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(stringResource(R.string.working))
        }
    }
}

@Composable
private fun PreviewArea(state: MainState) {
    val shown = state.preview
    when {
        state.photo == null -> Text(stringResource(R.string.no_photo))

        shown != null -> Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = stringResource(R.string.preview_cd),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = PreviewMax)
        )
    }
    if (state.signatureStatus() == SignatureFitStatus.TooLong) {
        Text(
            stringResource(R.string.signature_too_long),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FilmPicker(state: MainState) {
    Text(
        stringResource(R.string.title_film),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    if (state.films.size <= 1) Text(stringResource(R.string.films_missing))
    Column(verticalArrangement = Arrangement.spacedBy(Gap / 2)) {
        state.films.forEach { f ->
            FilterChip(
                selected = state.film == f.id,
                onClick = { state.film = f.id },
                label = { Text(f.name ?: stringResource(R.string.film_identity)) }
            )
        }
    }
}

@Composable
private fun FramePanel(state: MainState) {
    val f = state.frame
    Text(
        stringResource(R.string.title_frame),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    LabeledSwitch(stringResource(R.string.frame_enabled), f.enabled) { state.frame = f.copy(enabled = it) }
    if (!f.enabled) return
    Row(horizontalArrangement = Arrangement.spacedBy(Gap / 2)) {
        FrameAspect.entries.forEach { a ->
            FilterChip(selected = f.aspect == a, onClick = {
                state.frame = f.copy(aspect = a)
            }, label = { Text(a.label) })
        }
    }
    Text(stringResource(R.string.margin_label, (f.margin * PERCENT).roundToInt()))
    Slider(
        value = f.margin,
        onValueChange = { state.frame = f.copy(margin = it) },
        valueRange = 0f..FrameGeometry.MAX_MARGIN
    )
    LabeledSwitch(stringResource(R.string.signature_enabled), f.signatureEnabled) {
        state.frame = f.copy(signatureEnabled = it)
    }
    if (f.signatureEnabled) {
        OutlinedTextField(
            value = f.signature,
            onValueChange = { state.frame = f.copy(signature = it.take(FrameGeometry.SIGNATURE_MAX_CHARS)) },
            label = { Text(stringResource(R.string.signature_label)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
