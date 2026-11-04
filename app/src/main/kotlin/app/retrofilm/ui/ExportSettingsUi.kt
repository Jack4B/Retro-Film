package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.FileType
import app.retrofilm.pipeline.HeicExport
import kotlinx.coroutines.launch

private val Gap = 8.dp
private val MinTouch = 48.dp
private const val MIN_QUALITY = 70f
private const val MAX_QUALITY = 100f
private val Sizes = listOf(0, 4096, 2048, 1080)

/**
 * Ustawienia zapisu: format i kolor pliku, jakość JPEG, maksymalny rozmiar (zmniejszanie bez aliasingu
 * + wyostrzanie wyjściowe) i dane aparatu. Domyślnie JPEG 4:4:4, jakość 95, sRGB, EXIF bez GPS.
 */
@Composable
internal fun ExportSettings(state: MainState) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            stringResource(R.string.export_title),
            style = MaterialTheme.typography.titleSmall
        )
        ExportFileOptions(state)
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

/** Format, podpróbkowanie chromy, przestrzeń barw i EXIF. */
@Composable
private fun ExportFileOptions(state: MainState) {
    FileTypeOption(state)
    OptionRow(R.string.export_chroma_label) {
        FilterChip(
            selected = state.exportChroma444,
            onClick = { state.exportChroma444 = true },
            label = { Text(stringResource(R.string.export_chroma_444)) },
            modifier = Modifier.testTag("export-chroma-444")
        )
        FilterChip(
            selected = !state.exportChroma444,
            onClick = { state.exportChroma444 = false },
            label = { Text(stringResource(R.string.export_chroma_420)) },
            modifier = Modifier.testTag("export-chroma-420")
        )
    }
    OptionRow(R.string.export_space_label) {
        FilterChip(
            selected = !state.exportP3,
            onClick = { state.exportP3 = false },
            label = { Text(stringResource(R.string.export_space_srgb)) },
            modifier = Modifier.testTag("export-space-srgb")
        )
        FilterChip(
            selected = state.exportP3,
            onClick = { state.exportP3 = true },
            label = { Text(stringResource(R.string.export_space_p3)) },
            modifier = Modifier.testTag("export-space-p3")
        )
    }
    if (state.photoHasGainMap) HdrOption(state)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .toggleable(
                value = state.exportKeepExif,
                role = Role.Switch,
                onValueChange = { state.exportKeepExif = it }
            )
            .testTag("export-exif")
    ) {
        Text(stringResource(R.string.export_exif), modifier = Modifier.weight(1f))
        Switch(checked = state.exportKeepExif, onCheckedChange = null)
    }
}

/** „HDR ze zdjęcia: Wł./Wył.” – tylko dla zdjęć z gain mapą; Wł. wymusza 4:2:0 i zapis bez ramki (Gainmap API). */
@Composable
private fun HdrOption(state: MainState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OptionRow(R.string.hdr_from_photo_label) {
        FilterChip(
            selected = !state.hdrFromPhoto,
            onClick = { scope.launch { state.setHdrFromPhoto(context, false) } },
            label = { Text(stringResource(R.string.hdr_off)) },
            modifier = Modifier.testTag("hdr-off")
        )
        FilterChip(
            selected = state.hdrFromPhoto,
            onClick = { scope.launch { state.setHdrFromPhoto(context, true) } },
            label = { Text(stringResource(R.string.hdr_on)) },
            modifier = Modifier.testTag("hdr-on")
        )
    }
    if (state.hdrFromPhoto) Text(stringResource(R.string.hdr_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun OptionRow(label: Int, content: @Composable () -> Unit) {
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) { content() }
}

/** JPEG / PNG 16 / HEIC (HEIC tylko z koderem HEVC). PNG 16 i HEIC nie niosą EXIF; HEIC jest 8-bitowy. */
@Composable
private fun FileTypeOption(state: MainState) {
    val types = FileType.entries.filter { it != FileType.Heic || HeicExport.isAvailable }
    OptionRow(R.string.export_format_label) {
        types.forEach { t ->
            FilterChip(
                selected = state.exportFileType == t,
                onClick = { state.exportFileType = t },
                label = { Text(stringResource(t.label)) },
                modifier = Modifier.testTag("export-type-${t.extension}")
            )
        }
    }
    if (state.exportFileType != FileType.Jpeg) {
        Text(
            stringResource(
                if (state.exportFileType ==
                    FileType.Png16
                ) {
                    R.string.export_png16_note
                } else {
                    R.string.export_heic_note
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private val FileType.label: Int
    get() = when (this) {
        FileType.Jpeg -> R.string.export_format_jpeg
        FileType.Png16 -> R.string.export_format_png16
        FileType.Heic -> R.string.export_format_heic
    }
