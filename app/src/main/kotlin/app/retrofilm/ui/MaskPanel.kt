package app.retrofilm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.LocalMask
import app.retrofilm.pipeline.MaskPreview
import app.retrofilm.pipeline.MaskShape
import java.util.Locale
import kotlin.math.roundToInt

private val Pad = 16.dp
private val Gap = 8.dp
private val MinTouch = 48.dp
private const val PERCENT = 100
private const val MAX_ANGLE = 90f

/** Maski lokalne: lista (do trzech), kształt, położenie, miękkość i własne korekty wybranej maski. */
@Composable
internal fun MaskControls(state: MainState) {
    val masks = state.adjustments.masks
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var show by rememberSaveable { mutableStateOf(true) }
    val index = selected.coerceIn(0, (masks.size - 1).coerceAtLeast(0))
    val current = masks.getOrNull(index)
    val update = { m: LocalMask ->
        state.adjustments = state.adjustments.copy(masks = masks.toMutableList().also { it[index] = m })
    }
    DisposableEffect(current, show) {
        state.maskOverlay = if (show && !state.frame.enabled) current else null
        onDispose { state.maskOverlay = null }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        MaskList(state, masks, index) { selected = it }
        if (current != null) {
            Row(
                Modifier.padding(horizontal = Pad),
                horizontalArrangement = Arrangement.spacedBy(Gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = show, onCheckedChange = { show = it })
                Text(stringResource(R.string.mask_show))
            }
            ShapeSliders(current, update)
            EffectSliders(current, update)
            OutlinedButton(
                onClick = {
                    state.adjustments = state.adjustments.copy(masks = masks.filterIndexed { i, _ -> i != index })
                    selected = 0
                },
                modifier = Modifier.padding(horizontal = Pad).heightIn(min = MinTouch)
            ) { Text(stringResource(R.string.mask_delete)) }
        }
    }
}

@Composable
private fun MaskList(state: MainState, masks: List<LocalMask>, index: Int, onSelect: (Int) -> Unit) {
    val add = { shape: MaskShape ->
        val fresh = if (shape == MaskShape.Radial) LocalMask(shape, centerY = 0.5f) else LocalMask(shape)
        state.adjustments = state.adjustments.copy(masks = masks + fresh)
        onSelect(masks.size)
    }
    Row(Modifier.padding(horizontal = Pad), horizontalArrangement = Arrangement.spacedBy(Gap)) {
        masks.forEachIndexed { i, _ ->
            FilterChip(
                selected = i == index,
                onClick = { onSelect(i) },
                label = { Text(stringResource(R.string.mask_n, i + 1)) }
            )
        }
    }
    if (masks.size < LocalMask.MAX_MASKS) {
        Row(Modifier.padding(horizontal = Pad), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            OutlinedButton(onClick = { add(MaskShape.Linear) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.mask_add_linear))
            }
            OutlinedButton(onClick = { add(MaskShape.Radial) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.mask_add_radial))
            }
        }
    }
}

@Composable
private fun ShapeSliders(m: LocalMask, update: (LocalMask) -> Unit) {
    AdjustSlider(stringResource(R.string.mask_center_x), m.centerX, ::percentOf, range = 0f..1f) {
        update(m.copy(centerX = it))
    }
    AdjustSlider(stringResource(R.string.mask_center_y), m.centerY, ::percentOf, range = 0f..1f) {
        update(m.copy(centerY = it))
    }
    AdjustSlider(stringResource(R.string.mask_angle), m.angle / MAX_ANGLE, { degrees(it * MAX_ANGLE) }) {
        update(m.copy(angle = it * MAX_ANGLE))
    }
    if (m.shape == MaskShape.Radial) {
        AdjustSlider(stringResource(R.string.mask_size), m.size, ::percentOf, range = 0.05f..1f) {
            update(m.copy(size = it))
        }
        AdjustSlider(stringResource(R.string.mask_ratio), m.ratio, ::percentOf, range = 0.2f..1f) {
            update(m.copy(ratio = it))
        }
    }
    AdjustSlider(stringResource(R.string.mask_feather), m.feather, ::percentOf, range = 0f..1f) {
        update(m.copy(feather = it))
    }
    Row(
        Modifier.padding(horizontal = Pad),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = m.invert, onCheckedChange = { update(m.copy(invert = it)) })
        Text(stringResource(R.string.mask_invert))
    }
}

@Composable
private fun EffectSliders(m: LocalMask, update: (LocalMask) -> Unit) {
    AdjustSlider(stringResource(R.string.adjust_exposure), m.exposure, ::evText) { update(m.copy(exposure = it)) }
    AdjustSlider(stringResource(R.string.adjust_contrast), m.contrast, ::signedPercent) {
        update(m.copy(contrast = it))
    }
    AdjustSlider(stringResource(R.string.adjust_highlights), m.highlights, ::signedPercent) {
        update(m.copy(highlights = it))
    }
    AdjustSlider(stringResource(R.string.adjust_shadows), m.shadows, ::signedPercent) { update(m.copy(shadows = it)) }
    AdjustSlider(stringResource(R.string.adjust_saturation), m.saturation, ::signedPercent) {
        update(m.copy(saturation = it))
    }
    AdjustSlider(stringResource(R.string.adjust_temperature), m.temperature, ::signedPercent) {
        update(m.copy(temperature = it))
    }
}

/** Czerwona nakładka z siłą maski, rysowana w tym samym układzie co zdjęcie (ta sama bitmapa Fit i warstwa). */
@Composable
internal fun MaskOverlayImage(mask: LocalMask, photoW: Int, photoH: Int, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, mask, photoW, photoH) {
        value = MaskPreview.render(mask, photoW, photoH)
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit) }
}

private fun percentOf(v: Float): String = "%d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun signedPercent(v: Float): String = "%+d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun evText(v: Float): String = "%+.1f EV".format(Locale.forLanguageTag("pl"), v * Adjustments.EXPOSURE_MAX_EV)

private fun degrees(v: Float): String = "%+d°".format(Locale.forLanguageTag("pl"), v.roundToInt())
