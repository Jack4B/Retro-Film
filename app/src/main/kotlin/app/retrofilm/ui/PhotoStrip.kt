package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val StripGap = 8.dp
private val StripPad = 16.dp
private val StripCorner = 24.dp
private const val OVERLAY_ALPHA = 0.62f

/**
 * Pasek serii zdjęć jako półprzezroczysta nakładka na zdjęciu (zdjęcie jest w edytorze najważniejsze, więc pasek nie
 * zabiera mu miejsca): numery zdjęć do przełączania (przewijane) z chipem zakresu edycji na początku. Podczas zapisu
 * pokazuje postęp.
 */
@Composable
fun PhotoStrip(state: MainState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val total = state.uris.size
    val position = stringResource(R.string.photos_position, state.index + 1, total)
    Surface(
        modifier = modifier.semantics { contentDescription = position },
        color = MaterialTheme.colorScheme.surfaceDim.copy(alpha = OVERLAY_ALPHA),
        shape = RoundedCornerShape(StripCorner)
    ) {
        if (state.exportProgress > 0) {
            Text(
                stringResource(R.string.photos_saving, state.exportProgress, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = StripPad, vertical = StripGap)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StripGap),
                horizontalArrangement = Arrangement.spacedBy(StripGap),
                verticalAlignment = Alignment.CenterVertically
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
