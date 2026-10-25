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
        OutlinedButton(
            onClick = { state.updateGeometry(Geometry()) },
            enabled = !g.isNeutral,
            modifier = Modifier.padding(horizontal = Pad).heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.crop_reset)) }
    }
}
