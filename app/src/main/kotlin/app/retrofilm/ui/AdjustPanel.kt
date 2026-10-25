package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import java.util.Locale
import kotlin.math.roundToInt

private val MinTouch = 48.dp
private val SidePad = 16.dp
private val Gap = 4.dp
private const val PERCENT = 100
private const val RANGE = 1f

/** Zakładka Korekty: cztery suwaki; zmiana zatwierdzana po puszczeniu suwaka (jak moc filmu). */
@Composable
fun AdjustPanel(state: MainState) {
    val a = state.adjustments
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        AdjustSlider(
            label = stringResource(R.string.adjust_exposure),
            value = a.exposure / Adjustments.EXPOSURE_MAX_EV,
            display = { evText(it * Adjustments.EXPOSURE_MAX_EV) }
        ) { state.adjustments = a.copy(exposure = it * Adjustments.EXPOSURE_MAX_EV) }
        AdjustSlider(stringResource(R.string.adjust_contrast), a.contrast, { percentText(it) }) {
            state.adjustments = a.copy(contrast = it)
        }
        AdjustSlider(stringResource(R.string.adjust_saturation), a.saturation, { percentText(it) }) {
            state.adjustments = a.copy(saturation = it)
        }
        AdjustSlider(stringResource(R.string.adjust_temperature), a.temperature, { percentText(it) }) {
            state.adjustments = a.copy(temperature = it)
        }
        OutlinedButton(
            onClick = { state.adjustments = Adjustments.None },
            enabled = !a.isNeutral,
            modifier = Modifier.padding(horizontal = SidePad).heightIn(min = MinTouch)
        ) { Text(stringResource(R.string.adjust_reset)) }
    }
}

/** Suwak −1..1 (wartość znormalizowana); [display] pokazuje wartość po polsku, [onCommit] woła się po puszczeniu. */
@Composable
private fun AdjustSlider(label: String, value: Float, display: (Float) -> String, onCommit: (Float) -> Unit) {
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
            valueRange = -RANGE..RANGE,
            modifier = Modifier.heightIn(min = MinTouch).semantics {
                contentDescription = label
                stateDescription = shown
            }
        )
    }
}

private fun percentText(v: Float): String = "%+d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun evText(ev: Float): String = "%+.1f EV".format(Locale.forLanguageTag("pl"), ev)
