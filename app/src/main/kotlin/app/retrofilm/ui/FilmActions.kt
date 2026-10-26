package app.retrofilm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val Touch = 48.dp
private val Dot = 36.dp
private val DotPad = 8.dp
private const val SCRIM_ALPHA = 0.5f
private const val ICON_ALPHA = 0.95f

/**
 * Przyciski zakładki Film jako półprzezroczyste ikony na zdjęciu (pod ikoną „i”): biblioteka filmów (lupa) i zapis
 * presetu („+”). Dzięki temu rzędy kategorii w szufladzie mają całą szerokość i nic nie zasłania przewijania.
 */
@Composable
internal fun FilmActions(state: MainState, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        GlassButton(Icons.Filled.Search, stringResource(R.string.library_open_cd), onOpenLibrary)
        PresetAddAction(state) { open ->
            GlassButton(Icons.Filled.Add, stringResource(R.string.preset_add_cd), open)
        }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Touch).semantics { contentDescription = description }) {
        Surface(color = Color.Black.copy(alpha = SCRIM_ALPHA), shape = CircleShape, modifier = Modifier.size(Dot)) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = ICON_ALPHA),
                modifier = Modifier.padding(DotPad)
            )
        }
    }
}
