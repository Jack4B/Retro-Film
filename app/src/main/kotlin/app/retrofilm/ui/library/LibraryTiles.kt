package app.retrofilm.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.feature.settings.lut.formatBytes
import app.retrofilm.ui.MainState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BadgePad = 6.dp
private val StarSize = 20.dp
private val SpinnerSize = 12.dp
private const val PERCENT = 100
private const val OVERLAY_ALPHA = 0.72f

/** Wybór/ulubiony kafelka. */
@Immutable
internal data class TileFlags(val selected: Boolean, val favorite: Boolean)

internal fun onTileClick(item: LibraryItem, state: MainState, onBack: () -> Unit) {
    when (item.status) {
        FilmStatus.OnDevice -> {
            state.film = item.id
            onBack()
        }

        FilmStatus.NotDownloaded, FilmStatus.Failed -> state.library.download(item.id)

        FilmStatus.Downloading -> state.library.cancel(item.id)
    }
}

/** Kafelek: obraz to gotowy JPG z APK; z „Na moim zdjęciu” (tylko film na telefonie) podgląd z LutMemoryCache. */
@Composable
internal fun LibraryTile(
    item: LibraryItem,
    flags: TileFlags,
    state: MainState,
    onMyPhoto: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val jpg by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { FilmThumbAssets.load(context.assets, item.id) }
    }
    val useMyPhoto = onMyPhoto && state.photo != null && item.status == FilmStatus.OnDevice
    if (useMyPhoto) LaunchedEffect(item.id, state.photo) { state.thumbnails.ensure(item.id, state.photo) }
    // Na zdjęciu użytkownika nie pokazujemy najpierw miniatury z APK, żeby obraz się nie zmieniał w trakcie ładowania.
    val image = if (useMyPhoto) state.thumbnails.bitmaps[item.id] else jpg
    val scope = rememberCoroutineScope()
    TileContent(item, image, flags, onClick) { scope.launch { state.library.toggleFavorite(item.id) } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileContent(
    item: LibraryItem,
    image: Bitmap?,
    flags: TileFlags,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(TileCorner)
    val selectedText = stringResource(if (flags.selected) R.string.film_selected else R.string.film_not_selected)
    val favoriteLabel = stringResource(
        if (flags.favorite) R.string.film_favorite_remove else R.string.film_favorite_add
    )
    val cd = stringResource(R.string.library_tile_cd, item.name, statusText(item))
    Column(
        Modifier
            .combinedClickable(
                onClickLabel = clickLabel(item),
                onClick = onClick,
                onLongClickLabel = favoriteLabel,
                onLongClick = onToggleFavorite
            )
            .semantics(mergeDescendants = true) {
                contentDescription = cd
                stateDescription = selectedText
                customActions = listOf(CustomAccessibilityAction(favoriteLabel) { onToggleFavorite().let { true } })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (flags.selected) Modifier.border(BorderStroke(2.dp, primary), shape) else Modifier)
        ) {
            image?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (flags.favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(BadgePad).size(StarSize)
                )
            }
            StatusBadge(item, Modifier.align(Alignment.BottomStart).padding(BadgePad))
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (flags.selected) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (flags.selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(item: LibraryItem, modifier: Modifier) {
    if (item.status == FilmStatus.OnDevice) return
    val text = badgeText(item)
    Surface(
        color = Color.Black.copy(alpha = OVERLAY_ALPHA),
        shape = RoundedCornerShape(TileCorner / 2),
        modifier = modifier
    ) {
        Row(Modifier.padding(horizontal = BadgePad, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.status == FilmStatus.Downloading) {
                CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(SpinnerSize),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun statusText(item: LibraryItem): String = when (item.status) {
    FilmStatus.OnDevice -> stringResource(R.string.library_status_on_device)

    FilmStatus.NotDownloaded -> stringResource(R.string.library_status_download, formatBytes(item.bytes))

    FilmStatus.Downloading ->
        stringResource(R.string.library_status_downloading, (item.progress * PERCENT).roundToInt())

    FilmStatus.Failed -> stringResource(R.string.library_status_failed)
}

@Composable
private fun badgeText(item: LibraryItem): String = when (item.status) {
    FilmStatus.OnDevice -> ""

    FilmStatus.NotDownloaded -> stringResource(R.string.library_badge_download, formatBytes(item.bytes))

    FilmStatus.Downloading ->
        stringResource(R.string.library_badge_downloading, (item.progress * PERCENT).roundToInt())

    FilmStatus.Failed -> stringResource(R.string.library_badge_failed)
}

@Composable
private fun clickLabel(item: LibraryItem): String = stringResource(
    when (item.status) {
        FilmStatus.OnDevice -> R.string.library_action_select
        FilmStatus.NotDownloaded, FilmStatus.Failed -> R.string.library_action_download
        FilmStatus.Downloading -> R.string.library_action_cancel
    }
)
