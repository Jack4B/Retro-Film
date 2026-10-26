package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import java.util.Locale
import kotlin.math.roundToInt

private val MinTouch = 48.dp
private val SidePad = 16.dp
private val Gap = 4.dp
private const val PERCENT = 100
private const val RANGE = 1f

private enum class FinishGroup(val label: Int) {
    Film(R.string.adjust_group_film),
    Sharpen(R.string.sharpen_title),
    Toning(R.string.adjust_group_toning),
    Curves(R.string.adjust_group_curves)
}

/**
 * Zakładka Wykończenie (po filmie, D15): klisza (blaknięcie, ziarno, winieta), wyostrzanie, tonowanie i krzywe.
 * Te ustawienia działają na obraz już z filmem, więc nie wpływają na korekty ani na wybór filmu.
 */
@Composable
fun FinishPanel(state: MainState) {
    var group by rememberSaveable { mutableStateOf(FinishGroup.Film) }
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePad),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            items(FinishGroup.entries, key = { it.name }) { g ->
                FilterChip(
                    selected = g == group,
                    onClick = { group = g },
                    label = { Text(stringResource(g.label)) }
                )
            }
        }
        when (group) {
            FinishGroup.Film -> KlisaSliders(state)
            FinishGroup.Sharpen -> SharpenControls(state)
            FinishGroup.Toning -> ToningSliders(state)
            FinishGroup.Curves -> CurveControls(state)
        }
        OutlinedButton(
            onClick = { state.adjustments = state.adjustments.corrections() },
            enabled = !state.adjustments.finishing().isNeutral,
            modifier = Modifier.padding(horizontal = SidePad).heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.finish_reset)) }
    }
}

@Composable
private fun KlisaSliders(state: MainState) {
    val a = state.adjustments
    AdjustSlider(stringResource(R.string.adjust_fade), a.fade, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(fade = it)
    }
    AdjustSlider(stringResource(R.string.adjust_grain), a.grain, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(grain = it)
    }
    AdjustSlider(stringResource(R.string.adjust_grain_size), a.grainSize, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(grainSize = it)
    }
    AdjustSlider(stringResource(R.string.adjust_vignette), a.vignette, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(vignette = it)
    }
}

private fun plainPercent(v: Float): String = "%d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())
