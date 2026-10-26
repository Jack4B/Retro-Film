package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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

private val StripGap = 8.dp
private val StripPad = 16.dp

/** Pasek serii zdjęć: numer bieżącego, postęp zapisu i przełączanie między zdjęciami. */
@Composable
fun PhotoStrip(state: MainState, onSelect: (Int) -> Unit) {
    val total = state.uris.size
    val label = if (state.exportProgress > 0) {
        stringResource(R.string.photos_saving, state.exportProgress, total)
    } else {
        stringResource(R.string.photos_position, state.index + 1, total)
    }
    Column(
        Modifier.fillMaxWidth().padding(top = StripGap),
        verticalArrangement = Arrangement.spacedBy(StripGap)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = StripPad)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = StripPad),
            horizontalArrangement = Arrangement.spacedBy(StripGap)
        ) {
            item(key = "scope") { EditScopeChip(state) }
            itemsIndexed(state.uris, key = { i, uri -> "$i-$uri" }) { i, _ ->
                val own = state.edits.isIndividual(i)
                val cd = stringResource(
                    if (own) R.string.photos_pick_own_cd else R.string.photos_pick_cd,
                    i + 1,
                    total
                )
                FilterChip(
                    selected = i == state.index,
                    enabled = !state.busy,
                    onClick = { onSelect(i) },
                    label = { Text(if (own) "${i + 1} ✎" else (i + 1).toString()) },
                    modifier = Modifier.semantics { contentDescription = cd }
                )
            }
        }
    }
}

/**
 * Zakres edycji bez zajmowania miejsca: pierwszy chip w wierszu zdjęć otwiera menu („Edytuj tylko to zdjęcie”,
 * „Wróć do wspólnych”, „Zastosuj do wszystkich”). Wygląd edytora zostaje taki sam jak przy edycji wspólnej.
 */
@Composable
private fun EditScopeChip(state: MainState) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val own = state.individual
    val stateCd = stringResource(if (own) R.string.scope_state_one_cd else R.string.scope_state_all_cd)
    Box {
        FilterChip(
            selected = own,
            enabled = !state.busy,
            onClick = { open = true },
            label = { Text(stringResource(if (own) R.string.scope_chip_own else R.string.scope_chip_shared)) },
            modifier = Modifier.semantics { stateDescription = stateCd }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (own) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scope_menu_shared)) },
                    onClick = {
                        open = false
                        state.setIndividual(false)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scope_apply_all)) },
                    onClick = {
                        open = false
                        confirm = true
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scope_menu_own)) },
                    onClick = {
                        open = false
                        state.setIndividual(true)
                    }
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.scope_apply_all_title)) },
            text = { Text(stringResource(R.string.scope_apply_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    state.applyCurrentToAll()
                    confirm = false
                }) { Text(stringResource(R.string.scope_apply_all_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.preset_cancel)) }
            }
        )
    }
}
