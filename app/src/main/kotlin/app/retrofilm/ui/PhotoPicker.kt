package app.retrofilm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.retrofilm.R

/**
 * Wybór zdjęcia serii jako rozwijane menu w górnym pasku (nic nie zasłania fotografii): przycisk „2/3 ▾” otwiera listę
 * zdjęć, a pod nią zakres edycji („Edytuj tylko to zdjęcie”, „Wróć do wspólnych”, „Zastosuj do wszystkich”). Podczas
 * zapisu pokazuje postęp.
 */
@Composable
fun PhotoPicker(state: MainState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val total = state.uris.size
    if (total < 2) return
    if (state.exportProgress > 0) {
        Text(
            stringResource(R.string.photos_saving, state.exportProgress, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val own = state.individual
    val position = stringResource(R.string.photos_position, state.index + 1, total)
    val scopeCd = stringResource(if (own) R.string.scope_state_one_cd else R.string.scope_state_all_cd)
    val label = stringResource(if (own) R.string.photos_button_own else R.string.photos_button, state.index + 1, total)
    Box(modifier) {
        FilterChip(
            selected = own,
            enabled = !state.busy,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, softWrap = false) },
            modifier = Modifier.semantics {
                contentDescription = position
                stateDescription = scopeCd
            }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PhotoItems(state, onSelect) { open = false }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ScopeItems(state, { open = false }) { confirm = true }
        }
    }
    if (confirm) ApplyAllDialog(state) { confirm = false }
}

/** Lista zdjęć serii: bieżące z haczykiem, edytowane osobno z „✎”. */
@Composable
private fun PhotoItems(state: MainState, onSelect: (Int) -> Unit, close: () -> Unit) {
    val total = state.uris.size
    for (i in 0 until total) {
        val own = state.edits.isIndividual(i)
        val cd = stringResource(if (own) R.string.photos_pick_own_cd else R.string.photos_pick_cd, i + 1, total)
        val name = stringResource(if (own) R.string.photos_menu_item_own else R.string.photos_menu_item, i + 1)
        DropdownMenuItem(
            text = { Text(name) },
            trailingIcon = { if (i == state.index) Icon(Icons.Filled.Check, contentDescription = null) },
            onClick = {
                close()
                onSelect(i)
            },
            modifier = Modifier.semantics { contentDescription = cd }
        )
    }
}

/** Zakres edycji: pozycje zależą od tego, czy bieżące zdjęcie ma własne ustawienia. */
@Composable
private fun ScopeItems(state: MainState, close: () -> Unit, askApplyAll: () -> Unit) {
    if (state.individual) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scope_menu_shared)) },
            onClick = {
                close()
                state.setIndividual(false)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scope_apply_all)) },
            onClick = {
                close()
                askApplyAll()
            }
        )
    } else {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scope_menu_own)) },
            onClick = {
                close()
                state.setIndividual(true)
            }
        )
    }
}

@Composable
private fun ApplyAllDialog(state: MainState, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.scope_apply_all_title)) },
        text = { Text(stringResource(R.string.scope_apply_all_text)) },
        confirmButton = {
            TextButton(onClick = {
                state.applyCurrentToAll()
                dismiss()
            }) { Text(stringResource(R.string.scope_apply_all_ok)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.preset_cancel)) } }
    )
}
