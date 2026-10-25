package app.retrofilm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.SignatureFitStatus

private val MinTouch = 48.dp
private val PanelMaxHeight = 280.dp
private val BadgeAlpha = 0.72f
private val EdgePad = 8.dp
private val BadgePad = 12.dp
private val TabHeight = 56.dp

private enum class EditorTab(val label: Int) {
    Film(R.string.tab_film),
    Frame(R.string.tab_frame)
}

/**
 * Edytor: zdjęcie na pierwszym planie, pod nim panel z zakładkami Film / Ramka.
 * Przytrzymanie zdjęcia pokazuje oryginał; „Zapisz” zapisuje od razu z bieżącymi ustawieniami.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: MainState, onBack: () -> Unit, onSave: () -> Unit, snackbar: @Composable () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(EditorTab.Film) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(MinTouch)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                title = {},
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = !state.busy && state.signatureStatus() != SignatureFitStatus.TooLong,
                        modifier = Modifier.padding(end = EdgePad).heightIn(min = MinTouch)
                    ) { Text(stringResource(R.string.editor_save), fontWeight = FontWeight.SemiBold) }
                }
            )
        },
        snackbarHost = snackbar
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            PreviewPane(state, Modifier.weight(1f).fillMaxWidth())
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = PanelMaxHeight).verticalScroll(rememberScrollState())
                    ) {
                        when (tab) {
                            EditorTab.Film -> FilmPanel(state)
                            EditorTab.Frame -> FramePanel(state)
                        }
                    }
                    TabBar(tab) { tab = it }
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(state: MainState, modifier: Modifier) {
    var showBefore by remember { mutableStateOf(false) }
    val shown = if (showBefore) state.before else state.preview
    val cd = stringResource(if (showBefore) R.string.editor_preview_before_cd else R.string.editor_preview_cd)
    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                showBefore = true
                try {
                    tryAwaitRelease()
                } finally {
                    showBefore = false
                }
            })
        },
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = cd,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (shown == null || state.busy) CircularProgressIndicator()
        if (showBefore) Badge(stringResource(R.string.editor_badge_before), Modifier.align(Alignment.TopStart))
        if (state.signatureStatus() == SignatureFitStatus.TooLong) {
            Badge(
                stringResource(R.string.signature_too_long),
                Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.error
            )
        } else if (!showBefore && state.preview != null) {
            Badge(stringResource(R.string.editor_hint_hold), Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier, color: Color = Color.White) {
    Surface(
        color = Color.Black.copy(alpha = BadgeAlpha),
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
