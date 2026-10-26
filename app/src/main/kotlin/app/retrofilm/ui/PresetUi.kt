package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import kotlinx.coroutines.launch

private val MinTouch = 48.dp
private val SidePad = 16.dp
private val TileGap = 8.dp

/** Plus przy filtrach: zapisuje bieżący film, jego moc i wszystkie korekty jako preset o podanej nazwie. */
@Composable
internal fun PresetAddButton(state: MainState) {
    var open by rememberSaveable { mutableStateOf(false) }
    val cd = stringResource(R.string.preset_add_cd)
    IconButton(onClick = { open = true }, modifier = Modifier.size(MinTouch).semantics { contentDescription = cd }) {
        Icon(Icons.Filled.Add, contentDescription = null)
    }
    if (open) SavePresetDialog(state, onDismiss = { open = false })
}

@Composable
private fun SavePresetDialog(state: MainState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    val canSave = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                Text(stringResource(R.string.preset_save_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Preset.MAX_NAME) },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val clean = state.adjustments.copy(sharpMaskPreview = false)
                    val snapshot = Preset(name, state.film, state.intensity, clean)
                    scope.launch { state.presetBook.save(context, snapshot) }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.preset_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preset_cancel)) } }
    )
}

/** Katalog „Presety”: kafelki zapisanych presetów (miniatura filmu, nazwa); wybrany można usunąć. */
@Composable
internal fun PresetTiles(state: MainState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = state.presetBook.items
    if (items.isEmpty()) {
        Hint(stringResource(R.string.preset_empty))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = SidePad),
        horizontalArrangement = Arrangement.spacedBy(TileGap)
    ) {
        items(items, key = { it.name }) { p ->
            FilmTile(
                name = p.name,
                thumbnail = state.thumbnails.bitmaps[p.film],
                selected = state.matches(p),
                favorite = false,
                onVisible = { state.thumbnails.ensure(p.film, state.photo) }
            ) { state.applyPreset(p) }
        }
    }
    items.firstOrNull { state.matches(it) }?.let { active ->
        Row(Modifier.fillMaxWidth().padding(horizontal = SidePad), verticalAlignment = Alignment.CenterVertically) {
            Text(active.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val cd = stringResource(R.string.preset_delete_cd, active.name)
            IconButton(
                onClick = { scope.launch { state.presetBook.delete(context, active.name) } },
                modifier = Modifier.size(MinTouch).semantics { contentDescription = cd }
            ) { Icon(Icons.Filled.Delete, contentDescription = null) }
        }
    }
}
