package app.retrofilm.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.feature.settings.lut.formatBytes
import app.retrofilm.ui.MainState
import app.retrofilm.ui.categoryLabel

private val MinTouch = 48.dp
private val SidePad = 16.dp
private val Gap = 12.dp
private val TileMin = 104.dp
internal val TileCorner = 12.dp

/**
 * Biblioteka filmów (D12): wyszukiwarka, kategorie z licznikami, ulubione i „Na telefonie”.
 * Kafelki pokazują gotowe JPG z APK (także dla filmów jeszcze niepobranych); podgląd na zdjęciu
 * użytkownika z `LutMemoryCache` to tylko dodatkowy widok („Na moim zdjęciu”) i nie zastępuje JPG.
 * Dotknięcie: film na telefonie → wybór; niepobrany → pobranie; w trakcie pobierania → anulowanie.
 * Przytrzymanie: ulubione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmLibraryScreen(state: MainState, onBack: () -> Unit) {
    val library = state.library
    val packs by library.packs.collectAsState()
    val favorites = library.favorites
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(LibraryFilter()) }
    var onMyPhoto by rememberSaveable { mutableStateOf(true) }
    val all = remember(library.films, packs) { LibraryModel.items(library.films, packs) }
    val shown = remember(all, filter, favorites) { LibraryModel.apply(all, filter, favorites) }
    val myPhoto = MyPhotoToggle(state.photo != null, onMyPhoto) { onMyPhoto = it }
    Scaffold(topBar = { LibraryTopBar(onBack) }) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileMin),
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(SidePad),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeader(all, filter, { filter = it }, myPhoto)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { DownloadAllCard(all, library::download) }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.library_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shown, key = { it.id }) { item ->
                LibraryTile(
                    item = item,
                    flags = TileFlags(selected = state.film == item.id, favorite = item.id in favorites),
                    state = state,
                    onMyPhoto = onMyPhoto,
                    onClick = { onTileClick(item, state, onBack) }
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    items: List<LibraryItem>,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    myPhoto: MyPhotoToggle
) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            label = { Text(stringResource(R.string.library_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap / 2)) {
            item(key = "all") {
                FilterChip(
                    selected = filter == LibraryFilter(query = filter.query),
                    onClick = { onFilter(LibraryFilter(query = filter.query)) },
                    label = { Text(stringResource(R.string.film_all)) }
                )
            }
            item(key = "favorites") {
                FilterChip(
                    selected = filter.onlyFavorites,
                    onClick = { onFilter(filter.copy(onlyFavorites = !filter.onlyFavorites)) },
                    label = { Text(stringResource(R.string.film_favorites)) }
                )
            }
            item(key = "on_device") {
                FilterChip(
                    selected = filter.onlyOnDevice,
                    onClick = { onFilter(filter.copy(onlyOnDevice = !filter.onlyOnDevice)) },
                    label = { Text(stringResource(R.string.library_on_device)) }
                )
            }
            items(LibraryModel.categoryCounts(items), key = { it.first }) { (category, count) ->
                FilterChip(
                    selected = filter.category == category,
                    onClick = { onFilter(filter.copy(category = if (filter.category == category) null else category)) },
                    label = { Text("${categoryLabel(category)} $count") }
                )
            }
        }
        if (myPhoto.available) {
            FilterChip(
                selected = myPhoto.on,
                onClick = { myPhoto.onChange(!myPhoto.on) },
                label = { Text(stringResource(R.string.library_on_my_photo)) }
            )
        }
        Text(
            stringResource(R.string.library_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadAllCard(items: List<LibraryItem>, download: (String) -> Unit) {
    val todo = LibraryModel.downloadable(items)
    if (todo.isEmpty()) return
    val total = formatBytes(todo.sumOf { it.bytes })
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(TileCorner)) {
        Row(Modifier.fillMaxWidth().padding(SidePad), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.library_download_title), fontWeight = FontWeight.Medium)
                Text(
                    pluralStringResource(R.plurals.library_download_summary, todo.size, todo.size, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { todo.forEach { download(it.id) } }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.library_download_all))
            }
        }
    }
}

/** Dodatkowy widok „Na moim zdjęciu”: dostępny, gdy jest zdjęcie; [on] = włączony. */
@Immutable
internal class MyPhotoToggle(val available: Boolean, val on: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun LibraryTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back))
            }
        },
        title = { Text(stringResource(R.string.library_title), modifier = Modifier.semantics { heading() }) }
    )
}

/** Zapis filtra przy obrocie ekranu: kategoria i zapytanie jako tekst, flagi jako 0/1. */
private val FilterSaver = Saver<LibraryFilter, Any>(
    save = { listOf(it.query, it.category, it.onlyFavorites, it.onlyOnDevice) },
    restore = {
        val v = it as List<*>
        LibraryFilter(v[0] as String, v[1] as String?, v[2] as Boolean, v[3] as Boolean)
    }
)
