package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.AutoScene
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val MinTouch = 48.dp
internal val SidePad = 16.dp
internal val Gap = 4.dp
private const val PERCENT = 100
internal const val RANGE = 1f

private enum class AdjustGroup(val label: Int) {
    Light(R.string.adjust_group_light),
    Color(R.string.adjust_group_color),
    Hsl(R.string.adjust_group_hsl),
    Detail(R.string.adjust_group_detail),
    Masks(R.string.adjust_group_masks)
}

/**
 * Zakładka Korekty (przed filmem, D15): Światło, Kolor, Kolory (HSL), Detal i Maski; zmiana zatwierdzana po puszczeniu
 * suwaka. Wykończenie (klisza, wyostrzanie, tonowanie, krzywe) jest w osobnej zakładce ([FinishPanel]).
 */
@Composable
fun AdjustPanel(state: MainState) {
    var group by rememberSaveable { mutableStateOf(AdjustGroup.Light) }
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePad),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            items(AdjustGroup.entries, key = { it.name }) { g ->
                FilterChip(
                    selected = g == group,
                    onClick = { group = g },
                    label = { Text(stringResource(g.label)) }
                )
            }
        }
        when (group) {
            AdjustGroup.Light -> LightSliders(state)
            AdjustGroup.Color -> ColorSliders(state)
            AdjustGroup.Hsl -> HslSliders(state)
            AdjustGroup.Detail -> DetailSliders(state)
            AdjustGroup.Masks -> MaskControls(state)
        }
        OutlinedButton(
            onClick = { state.adjustments = state.adjustments.finishing() },
            enabled = !state.adjustments.corrections().isNeutral,
            modifier = Modifier.padding(horizontal = SidePad).heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.adjust_reset)) }
    }
}

@Composable
private fun LightSliders(state: MainState) {
    val a = state.adjustments
    AutoButton(state)
    PrepButton(state)
    AdjustSlider(
        label = stringResource(R.string.adjust_exposure),
        value = a.exposure / Adjustments.EXPOSURE_MAX_EV,
        display = { evText(it * Adjustments.EXPOSURE_MAX_EV) }
    ) { state.adjustments = a.copy(exposure = it * Adjustments.EXPOSURE_MAX_EV) }
    AdjustSlider(stringResource(R.string.adjust_contrast), a.contrast, { percentText(it) }) {
        state.adjustments = a.copy(contrast = it)
    }
    AdjustSlider(stringResource(R.string.adjust_highlights), a.highlights, { percentText(it) }) {
        state.adjustments = a.copy(highlights = it)
    }
    AdjustSlider(stringResource(R.string.adjust_shadows), a.shadows, { percentText(it) }) {
        state.adjustments = a.copy(shadows = it)
    }
    AdjustSlider(stringResource(R.string.adjust_whites), a.whites, { percentText(it) }) {
        state.adjustments = a.copy(whites = it)
    }
    AdjustSlider(stringResource(R.string.adjust_blacks), a.blacks, { percentText(it) }) {
        state.adjustments = a.copy(blacks = it)
    }
    AdjustSlider(stringResource(R.string.adjust_texture), a.texture, { percentText(it) }) {
        state.adjustments = a.copy(texture = it)
    }
}

/** „Auto” ustawia światło i kolor z analizy zdjęcia; po użyciu ten sam przycisk cofa zmianę. */
@Composable
private fun ColorSliders(state: MainState) {
    val a = state.adjustments
    AdjustSlider(stringResource(R.string.adjust_saturation), a.saturation, { percentText(it) }) {
        state.adjustments = a.copy(saturation = it)
    }
    AdjustSlider(stringResource(R.string.adjust_temperature), a.temperature, { percentText(it) }) {
        state.adjustments = a.copy(temperature = it)
    }
    AdjustSlider(stringResource(R.string.adjust_tint), a.tint, { percentText(it) }) {
        state.adjustments = a.copy(tint = it)
    }
}

@Composable
private fun DetailSliders(state: MainState) {
    val a = state.adjustments
    AdjustSlider(stringResource(R.string.adjust_clarity), a.clarity, { percentText(it) }) {
        state.adjustments = a.copy(clarity = it)
    }
    AdjustSlider(stringResource(R.string.adjust_dehaze), a.dehaze, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(dehaze = it)
    }
    AdjustSlider(stringResource(R.string.adjust_denoise), a.denoise, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(denoise = it)
    }
    AdjustSlider(stringResource(R.string.adjust_luma_denoise), a.lumaDenoise, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(lumaDenoise = it)
    }
    AdjustSlider(stringResource(R.string.adjust_chroma_edges), a.chromaEdges, { plainPercent(it) }, range = 0f..RANGE) {
        state.adjustments = a.copy(chromaEdges = it)
    }
}

/** Suwak −1..1 (wartość znormalizowana); [display] pokazuje wartość po polsku, [onCommit] woła się po puszczeniu. */
@Composable
internal fun AdjustSlider(
    label: String,
    value: Float,
    display: (Float) -> String,
    range: ClosedFloatingPointRange<Float> = -RANGE..RANGE,
    onCommit: (Float) -> Unit
) {
    var drag by remember(value) { mutableFloatStateOf(value) }
    val shown = display(drag)
    Column(Modifier.padding(horizontal = SidePad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(shown, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = drag,
            onValueChange = { drag = it },
            onValueChangeFinished = { onCommit(drag) },
            valueRange = range,
            modifier = Modifier.heightIn(min = MinTouch).semantics {
                contentDescription = label
                stateDescription = shown
            }
        )
    }
}

internal fun percentText(v: Float): String = "%+d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

internal fun plainPercent(v: Float): String = "%d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun evText(ev: Float): String = "%+.1f EV".format(Locale.forLanguageTag("pl"), ev)
