package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val Gap = 8.dp
private val MinTouch = 48.dp
private const val MIN_QUALITY = 70f
private const val MAX_QUALITY = 100f
private val Sizes = listOf(0, 4096, 2048, 1080)

/** Ustawienia zapisu: jakość JPEG i maksymalny rozmiar (zmniejszanie bez aliasingu + wyostrzanie wyjściowe). */
@Composable
internal fun ExportSettings(state: MainState) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            stringResource(R.string.export_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(stringResource(R.string.export_quality_label, state.exportQuality))
        val cd = stringResource(R.string.export_quality_cd)
        Slider(
            value = state.exportQuality.toFloat(),
            onValueChange = { state.exportQuality = it.toInt() },
            valueRange = MIN_QUALITY..MAX_QUALITY,
            steps = (MAX_QUALITY - MIN_QUALITY).toInt() / QUALITY_STEP - 1,
            modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = cd }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            items(Sizes, key = { it }) { size ->
                FilterChip(
                    selected = state.exportMaxSide == size,
                    onClick = { state.exportMaxSide = size },
                    label = {
                        Text(if (size == 0) stringResource(R.string.export_size_original) else "$size px")
                    }
                )
            }
        }
    }
}

private const val QUALITY_STEP = 5
