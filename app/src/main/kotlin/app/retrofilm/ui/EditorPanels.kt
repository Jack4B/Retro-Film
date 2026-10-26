package app.retrofilm.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val PERCENT = 100
private val MinTouch = 48.dp
private val Gap = 12.dp
private val SidePad = 16.dp
private val ChipGap = 8.dp

/** Złota gwiazdka widoczna w obu motywach (kontrast z miniaturą i z tłem panelu). */
private val FavoriteGold = Color(0xFFFFB300)
private val FavoriteBadge = 18.dp
private val FavoriteIcon = 30.dp
private val TileWidth = 76.dp
private val TileThumb = 72.dp
private val TileCorner = 10.dp
private val RowPadding = PaddingValues(horizontal = SidePad)

@Composable
internal fun categoryLabel(category: String): String = stringResource(
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

/** Zakładka Film: ulubione, kategorie, miniatury filmów i moc. Zmiana mocy zatwierdzana po puszczeniu suwaka. */
@Composable
fun FilmPanel(state: MainState, onOpenLibrary: () -> Unit) {
    var brand by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val real = state.library.films.filter { it.id != IDENTITY_ID }
    val current = brand
        ?: real.firstOrNull { it.id == state.film }?.let { FilmGroups.brandOf(it.id) }
        ?: FilmGroups.brandsPresent(real).firstOrNull().orEmpty()
    val shown = FilmGroups.filter(real, current, type, onlyFavorites, state.library.favorites)
    Column(Modifier.padding(vertical = Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FilmFilterBar(
                    films = real,
                    brand = current,
                    type = type,
                    onlyFavorites = onlyFavorites,
                    onBrand = {
                        brand = it
                        type = null
                        onlyFavorites = false
                    },
                    onType = { type = it },
                    onFavorites = { onlyFavorites = true }
                )
            }
            LibraryButton(onOpenLibrary)
        }
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
            item(key = IDENTITY_ID) {
                FilmTile(
                    name = stringResource(R.string.film_none),
                    thumbnail = state.thumbnails.bitmaps[IDENTITY_ID],
                    selected = state.film == IDENTITY_ID,
                    favorite = false,
                    onVisible = { state.thumbnails.ensure(IDENTITY_ID, state.photo) }
                ) { state.film = IDENTITY_ID }
            }
            items(shown, key = { it.id }) { f ->
                FilmTile(
                    name = f.name.orEmpty(),
                    thumbnail = state.thumbnails.bitmaps[f.id],
                    selected = state.film == f.id,
                    favorite = f.id in state.library.favorites,
                    onVisible = { state.thumbnails.ensure(f.id, state.photo) }
                ) { state.film = f.id }
            }
        }
        if (real.isEmpty()) {
            Hint(stringResource(R.string.films_missing))
        } else if (onlyFavorites && shown.isEmpty()) {
            Hint(stringResource(R.string.film_favorites_empty))
        }
        FavoriteToggle(state, real)
        StrengthSlider(state)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = SidePad))
}

@Composable
private fun LibraryButton(onClick: () -> Unit) {
    val cd = stringResource(R.string.library_open_cd)
    IconButton(onClick = onClick, modifier = Modifier.size(MinTouch).semantics { contentDescription = cd }) {
        Icon(Icons.Filled.Search, contentDescription = null)
    }
}

/** Gwiazdka przy wybranym filmie: dodaje go do ulubionych albo usuwa. */
@Composable
private fun FavoriteToggle(state: MainState, films: List<FilmOption>) {
    val selected = films.firstOrNull { it.id == state.film } ?: return
    val scope = rememberCoroutineScope()
    val favorite = selected.id in state.library.favorites
    val cd = stringResource(
        if (favorite) R.string.film_favorite_remove_cd else R.string.film_favorite_add_cd,
        selected.name.orEmpty()
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SidePad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(selected.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (favorite) R.string.film_favorite_on else R.string.film_favorite_off),
                style = MaterialTheme.typography.labelMedium,
                color = if (favorite) FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { scope.launch { state.library.toggleFavorite(selected.id) } },
            modifier = Modifier.size(MinTouch).semantics { contentDescription = cd }
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (favorite) FavoriteGold else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(FavoriteIcon)
            )
        }
    }
}

@Suppress("LongParameterList") // kafelek: nazwa, miniatura i dwa znaczniki stanu + dwa zdarzenia
@Composable
private fun FilmTile(
    name: String,
    thumbnail: Bitmap?,
    selected: Boolean,
    favorite: Boolean,
    onVisible: suspend () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(thumbnail == null) { if (thumbnail == null) onVisible() }
    val cd = stringResource(R.string.film_tile_cd, name)
    val st = stringResource(if (selected) R.string.film_selected else R.string.film_not_selected)
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(TileCorner)
    Column(
        Modifier
            .width(TileWidth)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = cd
                stateDescription = st
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(TileThumb).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (selected) Modifier.border(BorderStroke(2.dp, primary), shape) else Modifier)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = FavoriteGold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(FavoriteBadge)
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
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
        LabeledSwitch(stringResource(R.string.watermark_enabled), f.watermark) { state.frame = f.copy(watermark = it) }
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
            SignatureStyleControls(state)
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
