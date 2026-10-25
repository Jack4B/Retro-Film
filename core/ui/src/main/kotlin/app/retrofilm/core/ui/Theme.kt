package app.retrofilm.core.ui

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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

/**
 * Jasny motyw marki: ciepły papier + ciemny bursztyn. Jasny bursztyn #F5A33B na bieli ma kontrast ≈ 2:1,
 * więc akcent tekstowy i przyciski to #8A5200 (≈ 6,4:1 na #FBF8F3, AA). #51473B na #EAE2D6 ≈ 7:1.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5200),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF8A5200),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2D1600),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF211B13),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF211B13),
    surfaceVariant = Color(0xFFEAE2D6),
    onSurfaceVariant = Color(0xFF51473B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1E9),
    surfaceContainer = Color(0xFFF3EDE4),
    surfaceContainerHigh = Color(0xFFEAE2D6),
    surfaceContainerHighest = Color(0xFFE2D9CB),
    outline = Color(0xFF7F7568),
    outlineVariant = Color(0xFFD5CBBB),
    error = Color(0xFFB3261E),
    onError = Color.White
)

/** Wybór motywu przez użytkownika. Domyślnie [Dark] (D3); [System] podąża za ustawieniem telefonu. */
enum class ThemeMode {
    System,
    Dark,
    Light;

    /** Czy użyć ciemnych kolorów, gdy system jest w trybie ciemnym ([systemDark]). */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Dark -> true
        Light -> false
    }
}

/** Font podpisu: Caveat (SIL OFL 1.1). */
val CaveatFamily = FontFamily(Font(R.font.caveat))

/** Czy urządzenie obsługuje dynamiczne kolory (Material You, API 31+). */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Motyw Material 3 Expressive: ciemny (domyślny) albo jasny.
 *
 * @param dynamicColor gdy `true` i API 31+, kolory pochodzą z tapety (Material You);
 * w przeciwnym razie bursztynowy motyw Retro-Film.
 * @param darkTheme `true` (domyślnie) = ciemny, `false` = jasny.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RetroFilmTheme(dynamicColor: Boolean, darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = when {
        dynamicColor && supportsDynamicColor() && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && supportsDynamicColor() -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
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
