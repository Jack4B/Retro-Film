@file:Suppress("MatchingDeclarationName") // stan + ekran w jednym pliku

package app.retrofilm.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.retrofilm.core.ui.engine.FilmEngine
import app.retrofilm.core.ui.engine.FilmLut
import app.retrofilm.core.ui.photo.Photo
import kotlin.math.roundToInt

@Immutable
data class EditorUiState(
    val photoSeed: Int = 0,
    val seriesIndex: Int = 0,
    val seriesSize: Int = 1,
    val luts: List<FilmLut>,
    val selectedLutId: String? = null,
    /** 0..2 (0–200%), domyślnie 1 = 100%. */
    val intensity: Float = 1f,
    val frameEnabled: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Klisze z pakietu `luts-v1`, których nie ma jeszcze na urządzeniu (D8). */
    val notDownloadedLutIds: Set<String> = emptySet()
) {
    val selectedLut get() = luts.firstOrNull { it.id == selectedLutId }
}

/** Ekran Edytor: wybór kliszy (LUT), intensywność, chip ramki. Bezstanowy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    engine: FilmEngine,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    onSelectLut: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onOpenFrame: () -> Unit,
    onDownloadLut: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lut = state.selectedLut
    val lutMissing = lut != null && lut.id in state.notDownloadedLutIds
    val pct = (state.intensity * 100).roundToInt()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                title = {
                    Text(
                        if (state.seriesSize > 1) "${state.seriesIndex + 1} / ${state.seriesSize}" else "Edytor",
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Cofnij")
                    }
                    IconButton(onClick = onRedo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ponów")
                    }
                    Button(onClick = onExport, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Eksportuj", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Photo(
                    contentDescription = "Podgląd zdjęcia" + (lut?.let { " z kliszą ${it.name}" } ?: " bez kliszy"),
                    key = state.photoSeed,
                    matrix = engine.previewMatrix(lut, state.intensity),
                    modifier = Modifier.fillMaxSize()
                )
                AssistChip(
                    onClick = onOpenFrame,
                    label = { Text(if (state.frameEnabled) "Ramka: wł." else "Ramka: wył.") },
                    leadingIcon = {
                        Icon(Icons.Outlined.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.Black.copy(alpha = 0.72f),
                        labelColor = if (state.frameEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        leadingIconContentColor = Color.White
                    ),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .semantics {
                            val frameState = if (state.frameEnabled) "włączona" else "wyłączona"
                            contentDescription = "Ramka $frameState, otwórz ustawienia ramki"
                        }
                )
                if (lutMissing && lut != null) {
                    AssistChip(
                        onClick = { onDownloadLut(lut.id) },
                        label = { Text("Nie pobrano · Pobierz") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Black.copy(alpha = 0.72f),
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .semantics { contentDescription = "Klisza ${lut.name} nie jest pobrana. Pobierz" }
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Intensywność",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$pct%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = state.intensity,
                        onValueChange = onIntensityChange,
                        valueRange = 0f..2f,
                        enabled = lut != null,
                        modifier = Modifier.padding(horizontal = 16.dp).height(48.dp)
                            .semantics {
                                contentDescription = "Intensywność kliszy"
                                stateDescription = "$pct procent"
                            }
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "none") {
                            LutTile(name = "Oryginał", selected = lut == null, onClick = { onSelectLut(null) }) {
                                Photo("", key = state.photoSeed, modifier = Modifier.fillMaxSize())
                            }
                        }
                        items(state.luts, key = { it.id }) { item ->
                            LutTile(name = item.name, selected = item.id == state.selectedLutId, onClick = {
                                onSelectLut(item.id)
                            }) {
                                Photo(
                                    "",
                                    key = state.photoSeed,
                                    matrix = engine.previewMatrix(item, 1f),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LutTile(name: String, selected: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Klisza $name" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
                .then(
                    if (selected) Modifier.border(BorderStroke(2.dp, primary), RoundedCornerShape(10.dp)) else Modifier
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) { thumb() }
        Text(
            name,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
