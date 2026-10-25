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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import kotlin.math.roundToInt

private const val PERCENT = 100
private val MinTouch = 48.dp
private val Gap = 12.dp
private val SidePad = 16.dp
private val ChipGap = 8.dp
private val RowPadding = PaddingValues(horizontal = SidePad)

@Composable
private fun categoryLabel(category: String): String = stringResource(
    when (category) {
        "bw" -> R.string.film_category_bw
        "negative_color" -> R.string.film_category_negative_color
        "negative_new" -> R.string.film_category_negative_new
        "negative_old" -> R.string.film_category_negative_old
        "colorslide" -> R.string.film_category_colorslide
        "instant_consumer" -> R.string.film_category_instant_consumer
        "instant_pro" -> R.string.film_category_instant_pro
        else -> R.string.film_category_other
    }
)

/** Zakładka Film: kategorie, lista filmów i moc. Zmiana mocy zatwierdzana po puszczeniu suwaka. */
@Composable
fun FilmPanel(state: MainState) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val real = state.films.filter { it.id != IDENTITY_ID }
    val categories = real.map { it.category }.distinct()
    val shown = real.filter { category == null || it.category == category }
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        if (categories.size > 1) {
            LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                item(key = "all") {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = { Text(stringResource(R.string.film_all)) }
                    )
                }
                items(categories, key = { it }) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(categoryLabel(c)) }
                    )
                }
            }
        }
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
            item(key = IDENTITY_ID) {
                FilmTile(stringResource(R.string.film_none), state.film == IDENTITY_ID) { state.film = IDENTITY_ID }
            }
            items(shown, key = { it.id }) { f ->
                FilmTile(f.name.orEmpty(), state.film == f.id) { state.film = f.id }
            }
        }
        if (real.isEmpty()) {
            Text(
                stringResource(R.string.films_missing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SidePad)
            )
        }
        StrengthSlider(state)
    }
}

@Composable
private fun FilmTile(name: String, selected: Boolean, onClick: () -> Unit) {
    val cd = stringResource(R.string.film_tile_cd, name)
    val st = stringResource(if (selected) R.string.film_selected else R.string.film_not_selected)
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Text(
        name,
        color = color,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        modifier = Modifier
            .heightIn(min = MinTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = cd
                stateDescription = st
            }
            .padding(horizontal = Gap, vertical = Gap)
    )
}

@Composable
private fun StrengthSlider(state: MainState) {
    var drag by remember(state.intensity) { mutableFloatStateOf(state.intensity) }
    val pct = (drag * PERCENT).roundToInt()
    val cd = stringResource(R.string.film_strength_cd)
    val value = stringResource(R.string.film_strength_percent, pct)
    Column(Modifier.padding(horizontal = SidePad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.film_strength), modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = drag,
            onValueChange = { drag = it },
            onValueChangeFinished = { state.intensity = drag },
            enabled = state.film != IDENTITY_ID,
            modifier = Modifier.heightIn(min = MinTouch).semantics {
                contentDescription = cd
                stateDescription = value
            }
        )
    }
}

/** Zakładka Ramka: przełącznik, proporcje, grubość i podpis; podgląd na żywo nad panelem. */
@Composable
fun FramePanel(state: MainState) {
    val f = state.frame
    Column(Modifier.padding(vertical = Gap, horizontal = SidePad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        LabeledSwitch(stringResource(R.string.frame_enabled), f.enabled) { state.frame = f.copy(enabled = it) }
        if (!f.enabled) return@Column
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
            items(FrameAspect.entries, key = { it.name }) { a ->
                FilterChip(
                    selected = f.aspect == a,
                    onClick = { state.frame = f.copy(aspect = a) },
                    label = { Text(a.label) }
                )
            }
        }
        Text(stringResource(R.string.margin_label, (f.margin * PERCENT).roundToInt()))
        Slider(
            value = f.margin,
            onValueChange = { state.frame = f.copy(margin = it) },
            valueRange = 0f..FrameGeometry.MAX_MARGIN,
            modifier = Modifier.heightIn(min = MinTouch)
        )
        LabeledSwitch(stringResource(R.string.signature_enabled), f.signatureEnabled) {
            state.frame = f.copy(signatureEnabled = it)
        }
        if (f.signatureEnabled) {
            OutlinedTextField(
                value = f.signature,
                onValueChange = { state.frame = f.copy(signature = it.take(FrameGeometry.SIGNATURE_MAX_CHARS)) },
                label = { Text(stringResource(R.string.signature_label)) },
                supportingText = {
                    val counter = stringResource(
                        R.string.signature_counter,
                        f.signature.length,
                        FrameGeometry.SIGNATURE_MAX_CHARS
                    )
                    Text(counter)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
