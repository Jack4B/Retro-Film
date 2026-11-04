package app.retrofilm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val ThumbSize = 48.dp
private val ThumbGap = 8.dp
private val StripPad = 12.dp
private val SelectedBorder = 2.dp

/**
 * Pasek serii (gdy zdjęć jest kilka): przełącznik zakresu „Cała seria | To zdjęcie” i miniatury-numery zdjęć
 * („✎” = zdjęcie z osobną edycją). Włączenie „Cała seria” przy osobnych edycjach innych zdjęć pyta o potwierdzenie.
 */
@Composable
internal fun SeriesStrip(state: MainState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val total = state.uris.size
    if (total < 2) return
    var confirm by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = StripPad, vertical = 4.dp)) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth()
        ) {
            val options = listOf(true to R.string.series_scope_all, false to R.string.series_scope_one)
            options.forEachIndexed { i, (all, label) ->
                SegmentedButton(
                    selected = state.scopeAll == all,
                    enabled = !state.busy,
                    onClick = {
                        when {
                            all == state.scopeAll -> Unit
                            all && otherOwnEdits(state) -> confirm = true
                            else -> state.changeScopeAll(all)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size)
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        val list = rememberLazyListState()
        LaunchedEffect(state.index) { list.animateScrollToItem(state.index) }
        LazyRow(
            state = list,
            horizontalArrangement = Arrangement.spacedBy(ThumbGap),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(state.uris) { i, _ -> SeriesThumb(state, i, total, onSelect) }
        }
    }
    if (confirm) {
        ScopeAllDialog(onConfirm = {
            confirm = false
            state.changeScopeAll(true)
        }, onDismiss = { confirm = false })
    }
}

private fun otherOwnEdits(state: MainState): Boolean = state.uris.indices.any {
    it != state.index &&
        state.isIndividual(it)
}

@Composable
private fun SeriesThumb(state: MainState, i: Int, total: Int, onSelect: (Int) -> Unit) {
    val current = i == state.index
    val own = state.isIndividual(i)
    val cd = stringResource(if (own) R.string.series_thumb_own_cd else R.string.series_thumb_cd, i + 1, total)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { if (!current) onSelect(i) },
        enabled = !state.busy,
        shape = MaterialTheme.shapes.small,
        color = if (current) colors.primaryContainer else colors.surfaceContainerHighest,
        border = if (current) BorderStroke(SelectedBorder, colors.primary) else null,
        modifier = Modifier.size(ThumbSize).semantics {
            contentDescription = cd
            selected = current
            role = Role.Tab
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (own) "${i + 1}✎" else "${i + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScopeAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.series_scope_all_confirm_title)) },
        text = { Text(stringResource(R.string.series_scope_all_confirm_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.scope_apply_all_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preset_cancel)) } }
    )
}
