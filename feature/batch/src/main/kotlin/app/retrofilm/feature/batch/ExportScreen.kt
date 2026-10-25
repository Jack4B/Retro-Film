package app.retrofilm.feature.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class ExportSize(val label: String) { Original("Oryginał"), Large("2048 px"), Social("1080 px") }

@Immutable
data class ExportUiState(
    val photoCount: Int = 1,
    /** Ramka domyślnie wyłączona. */
    val frameEnabled: Boolean = false,
    val frameSummary: String = "",
    val quality: Int = 95,
    val size: ExportSize = ExportSize.Original,
    /** null = brak eksportu w toku; inaczej liczba gotowych zdjęć. */
    val progress: Int? = null
)

/** Ekran Eksport: JPEG, bez ramki domyślnie, bez metadanych i lokalizacji. Bezstanowy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onBack: () -> Unit,
    onOpenFrame: () -> Unit,
    onQualityChange: (Int) -> Unit,
    onSizeChange: (ExportSize) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val series = state.photoCount > 1
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
                title = { Text(if (series) "Eksport serii (${state.photoCount})" else "Eksport", maxLines = 1) }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p = state.progress
                    if (p != null) {
                        Text("Eksport $p/${state.photoCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { p / state.photoCount.toFloat() },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription =
                                    "Postęp eksportu $p z ${state.photoCount}"
                            }
                        )
                    }
                    Button(onClick = onSave, enabled = p == null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(
                            if (series) "Zapisz serię do galerii" else "Zapisz do galerii",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GroupCard {
                ListItem(
                    headlineContent = { Text("Ramka i podpis") },
                    supportingContent = {
                        Text(
                            if (state.frameEnabled) state.frameSummary else "Bez ramki (domyślnie) · dotknij, aby dodać"
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.CropFree, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.heightIn(
                        min = 56.dp
                    ).clickable(onClickLabel = "Otwórz ustawienia ramki", onClick = onOpenFrame)
                        .semantics {
                            contentDescription =
                                "Ramka i podpis: " +
                                if (state.frameEnabled) state.frameSummary else "bez ramki, dotknij, aby dodać"
                        }
                )
            }
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Jakość JPEG", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${state.quality}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = state.quality.toFloat(),
                        onValueChange = { onQualityChange(it.roundToInt()) },
                        valueRange = 60f..100f,
                        modifier = Modifier.height(48.dp).semantics {
                            contentDescription = "Jakość JPEG"
                            stateDescription =
                                "${state.quality} procent"
                        }
                    )
                    Text("Rozmiar", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ExportSize.entries.forEachIndexed { i, sz ->
                            SegmentedButton(
                                selected = state.size == sz,
                                onClick = { onSizeChange(sz) },
                                shape = SegmentedButtonDefaults.itemShape(i, ExportSize.entries.size),
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription =
                                        "Rozmiar ${sz.label}"
                                }
                            ) { Text(sz.label, maxLines = 1) }
                        }
                    }
                }
            }
            GroupCard {
                ListItem(
                    headlineContent = { Text("Prywatność") },
                    supportingContent = {
                        Text(
                            "Zapisywany jest tylko obraz: metadane aparatu i lokalizacja są usuwane. " +
                                "Zdjęcia przetwarzane lokalnie. Internet służy tylko do pobierania filtrów. " +
                                "Zdjęcia nie opuszczają telefonu."
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
        }
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
