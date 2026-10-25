package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                val cd = stringResource(R.string.photos_pick_cd, i + 1)
                FilterChip(
                    selected = i == state.index,
                    enabled = !state.busy,
                    onClick = { onSelect(i) },
                    label = { Text((i + 1).toString()) },
                    modifier = Modifier.semantics { contentDescription = cd }
                )
            }
        }
    }
}
