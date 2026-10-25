package app.retrofilm.core.ui.preview

// TYLKO PODGLĄD / TESTY: plik leży w src/debug, nie trafia do wariantu release.
// Zawiera fałszywy silnik i zastępcze „zdjęcie” dla @Preview i testów zrzutów (Roborazzi).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.core.ui.engine.FilmEngine
import app.retrofilm.core.ui.engine.FilmLut
import app.retrofilm.core.ui.photo.LocalPhotoRenderer
import app.retrofilm.core.ui.photo.PhotoRenderer

/** Zastępcze zdjęcie (pejzaż górski) rysowane w Canvas – deterministyczne, bez plików. */
object PlaceholderPhotoRenderer : PhotoRenderer {
    override fun DrawScope.drawPhoto(key: Int, rect: Rect, matrix: ColorMatrix?) {
        drawLandscape(rect, key, matrix)
    }
}

@Suppress("MagicNumber", "LongMethod")
private fun DrawScope.drawLandscape(rect: Rect, seed: Int, matrix: ColorMatrix?) {
    val palettes = listOf(
        listOf(Color(0xFFF2B56B), Color(0xFFB86A5A), Color(0xFF4A3550), Color(0xFF2A2236)),
        listOf(Color(0xFF9CC7E6), Color(0xFF6E8FB0), Color(0xFF3C5470), Color(0xFF1F2E3E)),
        listOf(Color(0xFFE8C9A0), Color(0xFF8FA07A), Color(0xFF4E6447), Color(0xFF26342A))
    )
    val p = palettes[((seed % palettes.size) + palettes.size) % palettes.size]
    val filter = matrix?.let { ColorFilter.colorMatrix(it) }
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        drawRect(
            Brush.verticalGradient(listOf(p[0], p[1]), rect.top, rect.bottom),
            rect.topLeft,
            rect.size,
            colorFilter = filter
        )
        drawCircle(
            Color(0xFFFFE3A3),
            rect.width * 0.08f,
            Offset(
                rect.left + rect.width * 0.68f,
                rect.top + rect.height * 0.42f
            ),
            colorFilter = filter
        )
        fun ridge(h: Float, color: Color, shift: Float) {
            val path = Path().apply {
                moveTo(rect.left, rect.bottom)
                lineTo(rect.left, rect.top + rect.height * (h + 0.1f))
                lineTo(rect.left + rect.width * (0.25f + shift), rect.top + rect.height * h)
                lineTo(rect.left + rect.width * (0.5f + shift), rect.top + rect.height * (h + 0.12f))
                lineTo(rect.left + rect.width * (0.75f + shift), rect.top + rect.height * (h - 0.04f))
                lineTo(rect.right, rect.top + rect.height * (h + 0.08f))
                lineTo(rect.right, rect.bottom)
                close()
            }
            drawPath(path, color, colorFilter = filter)
        }
        ridge(0.48f, p[2], 0f)
        ridge(0.62f, p[3], -0.08f)
    }
}

/** Deterministyczny fałszywy silnik do podglądów i testów zrzutów. */
@Suppress("MagicNumber")
object PreviewFilmEngine : FilmEngine {
    private val luts = listOf(
        FilmLut("portra400", "Portra 400"),
        FilmLut("ektar100", "Ektar 100"),
        FilmLut("hp5", "HP5 Plus"),
        FilmLut("superia200", "Superia 200"),
        FilmLut("velvia50", "Velvia 50"),
        FilmLut("trix400", "Tri-X 400")
    )

    override fun availableLuts(): List<FilmLut> = luts

    override fun previewMatrix(lut: FilmLut?, intensity: Float): ColorMatrix? {
        if (lut == null || intensity <= 0f) return null
        val k = intensity.coerceIn(0f, 2f)
        return when (lut.id) {
            "hp5", "trix400" -> ColorMatrix().apply { setToSaturation(1f - k.coerceAtMost(1f)) }

            else -> {
                val (r, g, b) = when (lut.id) {
                    "portra400" -> Triple(1.06f, 1.0f, 0.90f)
                    "ektar100" -> Triple(1.10f, 0.98f, 0.92f)
                    "superia200" -> Triple(0.96f, 1.05f, 0.98f)
                    else -> Triple(1.04f, 1.0f, 1.08f)
                }
                fun mix(c: Float) = 1f + (c - 1f) * k
                ColorMatrix(
                    floatArrayOf(
                        mix(r), 0f, 0f, 0f, 0f,
                        0f, mix(g), 0f, 0f, 0f,
                        0f, 0f, mix(b), 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }
    }
}

/** Motyw + zastępczy renderer zdjęć – opakowanie dla @Preview i testów zrzutów. */
@Composable
fun RetroFilmPreview(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPhotoRenderer provides PlaceholderPhotoRenderer) {
        RetroFilmTheme(content)
    }
}
