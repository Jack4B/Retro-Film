package app.retrofilm.core.ui

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** Akcent Retro-Film (bursztyn). Na tle #0E0E10 kontrast ≈ 9.9:1 (AA). */
val AccentAmber = Color(0xFFF5A33B)
val OnAccent = Color(0xFF1A1206)
val RfBackground = Color(0xFF0E0E10)
val RfSurface = Color(0xFF1A1A1E)
val RfSurfaceHigh = Color(0xFF26262C)
val RfOnSurface = Color(0xFFF2F2F2)

/** #A0A0A8 na #1A1A1E ≈ 6.6:1 (AA). */
val RfOnSurfaceVariant = Color(0xFFA0A0A8)
val RfError = Color(0xFFFF8A80)
val RfSelectedContainer = Color(0xFF3A2A12)

private val DarkColors = darkColorScheme(
    primary = AccentAmber,
    onPrimary = OnAccent,
    primaryContainer = RfSelectedContainer,
    onPrimaryContainer = AccentAmber,
    secondary = AccentAmber,
    onSecondary = OnAccent,
    secondaryContainer = RfSelectedContainer,
    onSecondaryContainer = AccentAmber,
    background = RfBackground,
    onBackground = RfOnSurface,
    surface = RfBackground,
    onSurface = RfOnSurface,
    surfaceVariant = RfSurfaceHigh,
    onSurfaceVariant = RfOnSurfaceVariant,
    surfaceContainerLowest = RfBackground,
    surfaceContainerLow = RfSurface,
    surfaceContainer = RfSurface,
    surfaceContainerHigh = RfSurfaceHigh,
    surfaceContainerHighest = Color(0xFF303038),
    outline = Color(0xFF6A6A72),
    outlineVariant = Color(0xFF3A3A42),
    error = RfError,
    onError = Color(0xFF2A0A08)
)

/** Font podpisu: Caveat (SIL OFL 1.1). */
val CaveatFamily = FontFamily(Font(R.font.caveat))

/** Czy urządzenie obsługuje dynamiczne kolory (Material You, API 31+). */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Ciemny motyw Material 3 Expressive.
 *
 * @param dynamicColor gdy `true` i API 31+, kolory pochodzą z tapety (Material You);
 * w przeciwnym razie bursztynowy motyw Retro-Film.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RetroFilmTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val colors = if (dynamicColor && supportsDynamicColor()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DarkColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

/** Ciemny motyw Retro-Film bez dynamicznych kolorów (domyślnie wyłączone). */
@Composable
fun RetroFilmTheme(content: @Composable () -> Unit) = RetroFilmTheme(dynamicColor = false, content = content)
