package app.retrofilm.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FilterFrames
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.retrofilm.R

/** Kolejność zakładek jest kolejnością pracy (D15): kadr, korekty, film, efekty, ramka. */
internal enum class EditorTab(val label: Int, val icon: ImageVector) {
    Crop(R.string.tab_crop, Icons.Outlined.Crop),
    Adjust(R.string.tab_adjust, Icons.Outlined.Tune),
    Film(R.string.tab_film, Icons.Outlined.Movie),
    Finish(R.string.tab_finish, Icons.Outlined.AutoAwesome),
    Frame(R.string.tab_frame, Icons.Outlined.FilterFrames)
}

/** Dolny pasek zakładek edytora (telefon): ikona + etykieta, 5 równych pozycji, cel dotyku ≥ 48 dp. */
@Composable
internal fun TabBar(selected: EditorTab, onSelect: (EditorTab) -> Unit) {
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        EditorTab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** Szyna nawigacji (tablet / poziomo): te same zakładki pionowo przy krawędzi ekranu. */
@Composable
internal fun EditorRail(selected: EditorTab, onSelect: (EditorTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        EditorTab.entries.forEach { t ->
            NavigationRailItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(stringResource(t.label), maxLines = 1) }
            )
        }
    }
}
