package app.retrofilm.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.retrofilm.R

/** Wysokość szuflady z narzędziami: zwinięta (same zakładki), kompaktowa (domyślna) albo pełna. */
enum class SheetMode { Collapsed, Compact, Full }

/** Stan szuflady przekazywany z ekranu: bieżący tryb, zmiana trybu i zgłoszenie jej wysokości (px) dla zdjęcia. */
class SheetControl(val mode: SheetMode, val onMode: (SheetMode) -> Unit, val onHeight: (Int) -> Unit)

private val HandleHeight = 24.dp
private val HandleWidth = 40.dp
private val HandleThickness = 4.dp
private val SheetCorner = 20.dp
private val SheetElevation = 8.dp
private const val COMPACT_FRACTION = 0.26f
private const val FULL_FRACTION = 0.5f
private const val HANDLE_ALPHA = 0.5f
private const val DRAG_THRESHOLD = 24f

/** Zawartość wybranej zakładki (do przewijania w szufladzie albo w panelu bocznym). */
@Composable
internal fun TabContent(state: MainState, tab: EditorTab) {
    when (tab) {
        EditorTab.Crop -> CropPanel(state)
        EditorTab.Adjust -> AdjustPanel(state)
        EditorTab.Film -> FilmPanel(state)
        EditorTab.Finish -> FinishPanel(state)
        EditorTab.Frame -> FramePanel(state)
    }
}

/**
 * Szuflada z narzędziami na dole ekranu (układ pionowy). Zdjęcie dostaje całą resztę miejsca nad nią, dlatego szuflada
 * jest niska domyślnie (kompaktowa ≈ 26% wysokości ekranu) i można ją zwinąć do samych zakładek albo rozwinąć
 * (≈ 50%): uchwytem (przeciągnięcie) albo dotknięciem aktywnej zakładki (zwija i rozwija), co działa też z TalkBack.
 */
@Suppress("LongParameterList") // stan, zakładka, zdarzenia i szuflada przekazywane jawnie
@Composable
internal fun EditorSheet(
    state: MainState,
    tab: EditorTab,
    onTab: (EditorTab) -> Unit,
    control: SheetControl,
    modifier: Modifier = Modifier
) {
    val screen = LocalConfiguration.current.screenHeightDp
    val contentMax = when (control.mode) {
        SheetMode.Collapsed -> 0.dp
        SheetMode.Compact -> (screen * COMPACT_FRACTION).dp
        SheetMode.Full -> (screen * FULL_FRACTION).dp
    }
    val pick = { t: EditorTab ->
        when {
            t != tab -> {
                onTab(t)
                if (control.mode == SheetMode.Collapsed) control.onMode(SheetMode.Compact)
            }

            control.mode == SheetMode.Collapsed -> control.onMode(SheetMode.Compact)

            else -> control.onMode(SheetMode.Collapsed)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().onSizeChanged { control.onHeight(it.height) },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        shadowElevation = SheetElevation
    ) {
        Column(Modifier.animateContentSize()) {
            SheetHandle(control.mode, control.onMode)
            if (control.mode != SheetMode.Collapsed) {
                Column(Modifier.fillMaxWidth().heightIn(max = contentMax).verticalScroll(rememberScrollState())) {
                    TabContent(state, tab)
                }
            }
            TabBar(tab, pick)
        }
    }
}

/** Uchwyt szuflady: przeciągnięcie w górę rozwija, w dół zwija; dla TalkBack akcje „Rozwiń” i „Zwiń”. */
@Composable
private fun SheetHandle(mode: SheetMode, onMode: (SheetMode) -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    val label = stringResource(R.string.sheet_handle_cd)
    val stateText = stringResource(
        when (mode) {
            SheetMode.Collapsed -> R.string.sheet_state_collapsed
            SheetMode.Compact -> R.string.sheet_state_compact
            SheetMode.Full -> R.string.sheet_state_full
        }
    )
    val expand = stringResource(R.string.sheet_expand)
    val collapse = stringResource(R.string.sheet_collapse)
    Box(
        Modifier.fillMaxWidth().height(HandleHeight)
            .pointerInput(mode) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = { onMode(moved(mode, drag)) }
                ) { _, delta -> drag += delta }
            }
            .semantics {
                contentDescription = label
                stateDescription = stateText
                customActions = listOf(
                    CustomAccessibilityAction(expand) {
                        onMode(moved(mode, -DRAG_THRESHOLD * 2))
                        true
                    },
                    CustomAccessibilityAction(collapse) {
                        onMode(moved(mode, DRAG_THRESHOLD * 2))
                        true
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(HandleWidth, HandleThickness)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA), CircleShape)
        )
    }
}

/** Kolejny tryb po przeciągnięciu o [drag] px (ujemne = w górę = rozwija). */
internal fun moved(mode: SheetMode, drag: Float): SheetMode = when {
    drag <= -DRAG_THRESHOLD -> when (mode) {
        SheetMode.Collapsed -> SheetMode.Compact
        else -> SheetMode.Full
    }

    drag >= DRAG_THRESHOLD -> when (mode) {
        SheetMode.Full -> SheetMode.Compact
        else -> SheetMode.Collapsed
    }

    else -> mode
}
