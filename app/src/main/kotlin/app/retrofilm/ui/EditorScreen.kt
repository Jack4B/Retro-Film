package app.retrofilm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.pipeline.GeometryMath
import java.util.Locale
import kotlin.math.roundToInt

private val MinTouch = 48.dp
private const val PANEL_SCREEN_FRACTION = 0.42f
private const val PERCENT = 100
private val CaptionPad = 6.dp
private const val BADGE_ALPHA = 0.72f
private val EdgePad = 8.dp
private val BadgePad = 12.dp
private val TabHeight = 56.dp
private val PanelCorner = 20.dp
private val PanelElevation = 8.dp

private enum class EditorTab(val label: Int) {
    Film(R.string.tab_film),
    Adjust(R.string.tab_adjust),
    Crop(R.string.tab_crop),
    Frame(R.string.tab_frame)
}

/**
 * Edytor: zdjęcie na pierwszym planie, pod nim panel z zakładkami Film / Ramka.
 * Przycisk „Przed” pokazuje oryginał, szczypanie i dwukrotne dotknięcie przybliżają podgląd; „Zapisz” zapisuje od razu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // zdarzenia ekranu przekazywane jawnie
@Composable
fun EditorScreen(
    state: MainState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSelectPhoto: (Int) -> Unit,
    snackbar: @Composable () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(EditorTab.Film) }
    var showBefore by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceDim),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(MinTouch)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                title = {},
                actions = { EditorActions(state, showBefore, { showBefore = !showBefore }, onSave) }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        snackbarHost = snackbar
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            PreviewPane(state, showBefore, Modifier.weight(1f).fillMaxWidth())
            if (state.preview != null) FilmCaption(state)
            if (state.uris.size > 1) PhotoStrip(state, onSelectPhoto)
            EditorPanel(state, tab, { tab = it }, onOpenLibrary)
        }
    }
}

/** Dolny panel: zawartość wybranej zakładki (przewijana, do ~42% wysokości ekranu) i pasek zakładek. */
@Composable
private fun EditorPanel(state: MainState, tab: EditorTab, onTab: (EditorTab) -> Unit, onOpenLibrary: () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * PANEL_SCREEN_FRACTION).dp
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner),
        shadowElevation = PanelElevation
    ) {
        Column {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                when (tab) {
                    EditorTab.Film -> FilmPanel(state, onOpenLibrary)
                    EditorTab.Adjust -> AdjustPanel(state)
                    EditorTab.Crop -> CropPanel(state)
                    EditorTab.Frame -> FramePanel(state)
                }
            }
            TabBar(tab, onTab)
        }
    }
}

/** Jedna linia pod zdjęciem: nazwa wybranego filmu i jego moc, żeby zawsze było widać, co jest nałożone. */
@Composable
private fun FilmCaption(state: MainState) {
    val text = if (state.film == IDENTITY_ID) {
        stringResource(R.string.film_none)
    } else {
        val name = state.library.films.firstOrNull { it.id == state.film }?.name.orEmpty()
        stringResource(R.string.film_caption, name, (state.intensity * PERCENT).roundToInt())
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = EdgePad, vertical = CaptionPad)
    )
}

@Composable
private fun RowScope.EditorActions(
    state: MainState,
    showBefore: Boolean,
    onToggleBefore: () -> Unit,
    onSave: () -> Unit
) {
    FilterChip(
        selected = showBefore,
        onClick = onToggleBefore,
        label = { Text(stringResource(R.string.editor_before_toggle)) },
        modifier = Modifier.padding(end = EdgePad)
    )
    Button(
        onClick = onSave,
        enabled = !state.busy && state.signatureStatus() != SignatureFitStatus.TooLong,
        modifier = Modifier.padding(end = EdgePad).heightIn(min = MinTouch)
    ) {
        val label = if (state.uris.size > 1) {
            stringResource(R.string.editor_save_all, state.uris.size)
        } else {
            stringResource(R.string.editor_save)
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PreviewPane(state: MainState, showBefore: Boolean, modifier: Modifier) {
    val zoom = remember { ZoomState() }
    LaunchedEffect(state.photo) { zoom.reset() }
    LaunchedEffect(zoom.isDetailed) { state.updateDetail(zoom.isDetailed) }
    val shown = if (showBefore) state.before else state.preview
    val cd = stringResource(if (showBefore) R.string.editor_preview_before_cd else R.string.editor_preview_cd)
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { zoom.size = it }
            .pointerInput(Unit) { detectTransformGestures { centroid, pan, z, _ -> zoom.transform(centroid, pan, z) } }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom.toggle(it) }) },
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = cd,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                }
            )
        }
        if (shown != null) PreviewReadout(state, shown.width, shown.height, zoom, Modifier.align(Alignment.TopCenter))
        if (shown == null || state.busy) CircularProgressIndicator()
        if (showBefore) Badge(stringResource(R.string.editor_badge_before), Modifier.align(Alignment.TopStart))
        if (state.signatureStatus() == SignatureFitStatus.TooLong) {
            Badge(
                stringResource(R.string.signature_too_long),
                Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Skala i rozmiar zdjęcia u góry podglądu, żeby było widać, ile pikseli ma zapisywany obraz. */
@Composable
private fun PreviewReadout(state: MainState, shownW: Int, shownH: Int, zoom: ZoomState, modifier: Modifier) {
    val photo = state.photo ?: return
    val (outW, outH) = GeometryMath.outputSize(photo.width, photo.height, state.geometry)
    val exportLong = maxOf(outW, outH)
    val (w, h) = ZoomMath.exportSize(shownW, shownH, exportLong)
    val percent = ZoomMath.percent(Dim(zoom.size.width, zoom.size.height), Dim(shownW, shownH), exportLong, zoom.scale)
    val mp = String.format(Locale.forLanguageTag("pl"), "%.1f", ZoomMath.megapixels(w, h))
    ZoomReadout(
        text = stringResource(R.string.zoom_readout, percent, w, h, mp),
        description = stringResource(R.string.zoom_readout_cd, percent, w, h, mp),
        modifier = modifier.padding(top = EdgePad)
    )
}

@Composable
private fun Badge(text: String, modifier: Modifier, color: Color = Color.White) {
    Surface(
        color = Color.Black.copy(alpha = BADGE_ALPHA),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.padding(BadgePad)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(BadgePad))
    }
}

@Composable
private fun TabBar(selected: EditorTab, onSelect: (EditorTab) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        EditorTab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.weight(1f).heightIn(min = TabHeight)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(t.label),
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
