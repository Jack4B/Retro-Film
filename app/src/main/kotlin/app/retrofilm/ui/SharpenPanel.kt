package app.retrofilm.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.SharpenMode
import app.retrofilm.pipeline.SharpenParams
import app.retrofilm.pipeline.SharpenPlanner
import app.retrofilm.pipeline.SharpenPreset
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SidePad = 16.dp
private val Gap = 8.dp
private const val PERCENT = 100
private const val ANALYSIS_SIDE = 512
private const val NOISE_CROP = 640
private const val MIN_RADIUS = 0.5f
private const val MAX_RADIUS = 3f

/**
 * Wyostrzanie jak w Lightroomie: zestawy (Auto dobiera do zdjęcia), sposób, ilość, promień, detal i maskowanie
 * krawędzi z podglądem maski (biel = wyostrzane).
 */
@Composable
internal fun SharpenControls(state: MainState) {
    val a = state.adjustments
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            stringResource(R.string.sharpen_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = SidePad)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePad),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            items(SharpenPreset.entries, key = { it.name }) { preset ->
                FilterChip(
                    selected =
                        preset.params != null && preset.params == a.sharpenParams.copy(mode = SharpenMode.Standard),
                    onClick = { scope.launch { state.applySharpenPreset(preset) } },
                    label = { Text(stringResource(presetLabel(preset))) }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePad),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            items(SharpenMode.entries, key = { it.name }) { mode ->
                FilterChip(
                    selected = a.sharpMode == mode,
                    onClick = { state.adjustments = a.copy(sharpMode = mode) },
                    label = { Text(stringResource(modeLabel(mode))) }
                )
            }
        }
        AdjustSlider(stringResource(R.string.sharpen_amount), a.sharpness, { plain(it) }, 0f..1f) {
            state.adjustments = a.copy(sharpness = it)
        }
        AdjustSlider(stringResource(R.string.sharpen_radius), a.sharpRadius, { radius(it) }, MIN_RADIUS..MAX_RADIUS) {
            state.adjustments = a.copy(sharpRadius = it)
        }
        AdjustSlider(stringResource(R.string.sharpen_detail), a.sharpDetail, { plain(it) }, 0f..1f) {
            state.adjustments = a.copy(sharpDetail = it)
        }
        AdjustSlider(stringResource(R.string.sharpen_masking), a.sharpMasking, { plain(it) }, 0f..1f) {
            state.adjustments = a.copy(sharpMasking = it)
        }
        LabeledSwitch(R.string.sharpen_mask_preview, a.sharpMaskPreview) {
            state.adjustments = a.copy(sharpMaskPreview = it)
        }
        if (a.sharpMaskPreview && a.sharpMasking <= 0f) MaskHint()
        LabeledSwitch(R.string.sharpen_smart, a.sharpSmart) { state.adjustments = a.copy(sharpSmart = it) }
    }
}

/** Podpowiedź przy pustej (całej białej) masce: podgląd działa, ale Maskowanie krawędzi wynosi 0. */
@Composable
private fun MaskHint() {
    Text(
        stringResource(R.string.sharpen_mask_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SidePad)
    )
}

@Composable
private fun LabeledSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SidePad)
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(stringResource(label))
    }
}

private fun presetLabel(p: SharpenPreset): Int = when (p) {
    SharpenPreset.Auto -> R.string.sharpen_auto
    SharpenPreset.Universal -> R.string.sharpen_universal
    SharpenPreset.Landscape -> R.string.sharpen_landscape
    SharpenPreset.Portrait -> R.string.sharpen_portrait
}

private fun modeLabel(m: SharpenMode): Int = when (m) {
    SharpenMode.Standard -> R.string.sharpen_mode_standard
    SharpenMode.Unsharp -> R.string.sharpen_mode_unsharp
    SharpenMode.Deconvolution -> R.string.sharpen_mode_deconv
}

private fun plain(v: Float) = "%d%%".format(Locale.forLanguageTag("pl"), (v * PERCENT).roundToInt())

private fun radius(v: Float) = "%.1f px".format(Locale.forLanguageTag("pl"), v)

/** Zastosowanie zestawu wyostrzania; Auto analizuje zdjęcie (skóra, krawędzie, szum) i dobiera parametry. */
internal suspend fun MainState.applySharpenPreset(preset: SharpenPreset) {
    val params = preset.params ?: suggestSharpen()
    adjustments = adjustments.copy(
        sharpness = params.amount,
        sharpRadius = params.radius,
        sharpDetail = params.detail,
        sharpMasking = params.masking,
        sharpMode = params.mode
    )
}

private suspend fun MainState.suggestSharpen(): SharpenParams {
    val p = photo ?: return SharpenPreset.Universal.params!!
    return withContext(Dispatchers.Default) {
        val scale = min(1f, ANALYSIS_SIDE.toFloat() / max(p.width, p.height))
        val w = max(1, (p.width * scale).roundToInt())
        val h = max(1, (p.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(p, w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        val stats = SharpenPlanner.analyze(px, w, h).copy(noiseSigma = noiseOfCenter(p))
        SharpenPlanner.suggest(stats)
    }
}

/** Szum liczony na wycinku oryginału w pełnej rozdzielczości (po zmniejszeniu szum by znikł). */
private fun noiseOfCenter(p: Bitmap): Float {
    val cw = min(NOISE_CROP, p.width)
    val ch = min(NOISE_CROP, p.height)
    val px = IntArray(cw * ch)
    p.getPixels(px, 0, cw, (p.width - cw) / 2, (p.height - ch) / 2, cw, ch)
    return SharpenPlanner.noiseSigma(SharpenPlanner.lumaOf(px), cw, ch)
}
