package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.CropAspect
import app.retrofilm.pipeline.Geometry
import java.util.Locale

private val Pad = 16.dp
private val Gap = 8.dp
private val MinTouch = 48.dp
private const val PERCENT = 100

/** Zakładka Kadr: obrót o 90°, prostowanie (±15°) i proporcje kadru (środkowy kadr). */
@Composable
fun CropPanel(state: MainState) {
    val g = state.geometry
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(Modifier.padding(horizontal = Pad), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            OutlinedButton(
                onClick = {
                    state.updateGeometry(g.copy(quarterTurns = Math.floorMod(g.quarterTurns - 1, Geometry.FULL_TURN)))
                },
                modifier = Modifier.heightIn(min = MinTouch)
            ) { Text(stringResource(R.string.crop_rotate_left)) }
            OutlinedButton(
                onClick = { state.updateGeometry(g.copy(quarterTurns = (g.quarterTurns + 1) % Geometry.FULL_TURN)) },
                modifier = Modifier.heightIn(min = MinTouch)
            ) { Text(stringResource(R.string.crop_rotate_right)) }
            OutlinedButton(
                onClick = { state.updateGeometry(g.copy(flip = !g.flip)) },
                enabled = g.aspect != CropAspect.Original,
                modifier = Modifier.heightIn(min = MinTouch)
            ) { Text(stringResource(R.string.crop_flip)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Pad), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            items(CropAspect.entries, key = { it.name }) { a ->
                FilterChip(
                    selected = g.aspect == a,
                    onClick = { state.updateGeometry(g.copy(aspect = a)) },
                    label = { Text(a.label) }
                )
            }
        }
        AdjustSlider(
            label = stringResource(R.string.crop_straighten),
            value = g.straighten,
            display = { "%+.1f°".format(Locale.forLanguageTag("pl"), it) },
            range = -Geometry.MAX_STRAIGHTEN..Geometry.MAX_STRAIGHTEN
        ) { state.updateGeometry(g.copy(straighten = it)) }
        WindowSliders(state)
        OutlinedButton(
            onClick = { state.updateGeometry(Geometry()) },
            enabled = g != Geometry(),
            modifier = Modifier.padding(horizontal = Pad).heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.crop_reset)) }
    }
}

/** Docięcie: przybliżenie okna kadru i jego przesunięcie po zdjęciu. */
@Composable
private fun WindowSliders(state: MainState) {
    val g = state.geometry
    AdjustSlider(
        label = stringResource(R.string.crop_zoom),
        value = g.zoom,
        display = { "%.1f×".format(Locale.forLanguageTag("pl"), it) },
        range = 1f..Geometry.MAX_ZOOM
    ) { state.updateGeometry(g.copy(zoom = it)) }
    AdjustSlider(
        label = stringResource(R.string.crop_pan_x),
        value = g.panX,
        display = { "%+.0f%%".format(Locale.forLanguageTag("pl"), it * PERCENT) },
        range = -1f..1f
    ) { state.updateGeometry(g.copy(panX = it)) }
    AdjustSlider(
        label = stringResource(R.string.crop_pan_y),
        value = g.panY,
        display = { "%+.0f%%".format(Locale.forLanguageTag("pl"), it * PERCENT) },
        range = -1f..1f
    ) { state.updateGeometry(g.copy(panY = it)) }
}
