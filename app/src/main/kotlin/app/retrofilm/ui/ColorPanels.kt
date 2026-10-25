package app.retrofilm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.HslAdjust
import java.util.Locale
import kotlin.math.roundToInt

private val Pad = 16.dp
private val Gap = 8.dp
private val Dot = 12.dp
private const val HUE_DEGREES = 30
private const val FULL_CIRCLE = 360f
private const val PERCENT = 100

private val BandColors = listOf(
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF00ACC1),
    Color(0xFF1E88E5),
    Color(0xFF7E57C2),
    Color(0xFFD81B60)
)

private val BandNames = listOf(
    R.string.hsl_red,
    R.string.hsl_orange,
    R.string.hsl_yellow,
    R.string.hsl_green,
    R.string.hsl_aqua,
    R.string.hsl_blue,
    R.string.hsl_purple,
    R.string.hsl_magenta
)

/** Mikser HSL: wybór zakresu barw (kolorowe chipy) i trzy suwaki: odcień, nasycenie, jasność. */
@Composable
internal fun HslSliders(state: MainState) {
    var band by rememberSaveable { mutableIntStateOf(0) }
    val a = state.adjustments
    LazyRow(contentPadding = PaddingValues(horizontal = Pad), horizontalArrangement = Arrangement.spacedBy(Gap)) {
        itemsIndexed(BandNames, key = { i, _ -> i }) { i, name ->
            FilterChip(
                selected = band == i,
                onClick = { band = i },
                leadingIcon = {
                    Row(Modifier.size(Dot).clip(CircleShape).background(BandColors[i])) {}
                },
                label = { Text(stringResource(name)) }
            )
        }
    }
    AdjustSlider(
        label = stringResource(R.string.hsl_hue),
        value = a.hsl.get(band, HslAdjust.HUE),
        display = { "%+d°".format(Locale.forLanguageTag("pl"), (it * HUE_DEGREES).roundToInt()) }
    ) { state.adjustments = a.copy(hsl = a.hsl.with(band, HslAdjust.HUE, it)) }
    AdjustSlider(stringResource(R.string.hsl_saturation), a.hsl.get(band, HslAdjust.SATURATION), { signed(it) }) {
        state.adjustments = a.copy(hsl = a.hsl.with(band, HslAdjust.SATURATION, it))
    }
    AdjustSlider(stringResource(R.string.hsl_lightness), a.hsl.get(band, HslAdjust.LIGHTNESS), { signed(it) }) {
        state.adjustments = a.copy(hsl = a.hsl.with(band, HslAdjust.LIGHTNESS, it))
    }
}

/** Tonowanie klisza: barwa i siła dla cieni oraz świateł i balans granicy między nimi. */
@Composable
internal fun ToningSliders(state: MainState) {
    val a = state.adjustments
    val hueRange = 0f..FULL_CIRCLE
    AdjustSlider(stringResource(R.string.toning_shadow_hue), a.shadowHue, { degrees(it) }, hueRange) {
        state.adjustments = a.copy(shadowHue = it)
    }
    AdjustSlider(stringResource(R.string.toning_shadow_amount), a.shadowAmount, { plain(it) }, 0f..1f) {
        state.adjustments = a.copy(shadowAmount = it)
    }
    AdjustSlider(stringResource(R.string.toning_highlight_hue), a.highlightHue, { degrees(it) }, hueRange) {
        state.adjustments = a.copy(highlightHue = it)
    }
    AdjustSlider(stringResource(R.string.toning_highlight_amount), a.highlightAmount, { plain(it) }, 0f..1f) {
        state.adjustments = a.copy(highlightAmount = it)
    }
    AdjustSlider(stringResource(R.string.toning_balance), a.toneBalance, { signed(it) }) {
        state.adjustments = a.copy(toneBalance = it)
    }
}

private fun signed(v: Float) = "%+d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun plain(v: Float) = "%d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun degrees(v: Float) = "%d°".format(Locale.forLanguageTag("pl"), v.roundToInt())
