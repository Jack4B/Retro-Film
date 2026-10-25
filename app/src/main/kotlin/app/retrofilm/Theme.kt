package app.retrofilm

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Placeholder accent color. */
val AccentAmber = Color(0xFFF5A33B)

private val DarkColors = darkColorScheme(
    primary = AccentAmber,
    secondary = AccentAmber
)

@Composable
fun RetroFilmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
