package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            itemsIndexed(state.uris, key = { i, uri -> "$i-$uri" }) { i, _ ->
                val own = state.edits.isIndividual(i)
                val cd = stringResource(if (own) R.string.photos_pick_own_cd else R.string.photos_pick_cd, i + 1)
                FilterChip(
                    selected = i == state.index,
                    enabled = !state.busy,
                    onClick = { onSelect(i) },
                    label = { Text(if (own) "${i + 1} ✎" else (i + 1).toString()) },
                    modifier = Modifier.semantics { contentDescription = cd }
                )
            }
        }
        EditScopeBar(state)
    }
}

/** Zakres edycji: wspólnie dla całej serii albo tylko to zdjęcie (własny film, korekty i kadr). */
@Composable
private fun EditScopeBar(state: MainState) {
    var confirm by remember { mutableStateOf(false) }
    val own = state.individual
    Column(Modifier.padding(horizontal = StripPad), verticalArrangement = Arrangement.spacedBy(StripGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StripGap), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !own,
                enabled = !state.busy,
                onClick = { state.setIndividual(false) },
                label = { Text(stringResource(R.string.scope_all)) }
            )
            FilterChip(
                selected = own,
                enabled = !state.busy,
                onClick = { state.setIndividual(true) },
                label = { Text(stringResource(R.string.scope_one)) }
            )
            if (own) {
                TextButton(onClick = { confirm = true }, enabled = !state.busy) {
                    Text(stringResource(R.string.scope_apply_all))
                }
            }
        }
        Text(
            stringResource(if (own) R.string.scope_hint_one else R.string.scope_hint_all, state.index + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
