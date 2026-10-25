package app.retrofilm.core.ui.photo

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Rysuje zdjęcie o kluczu [key] w prostokącie [rect], opcjonalnie z macierzą koloru (podgląd LUT).
 * Prawdziwy renderer (bitmapy z galerii) dostarczy `app`; ekrany go nie znają.
 */
fun interface PhotoRenderer {
    fun DrawScope.drawPhoto(key: Int, rect: Rect, matrix: ColorMatrix?)
}

/** Domyślny renderer: neutralny prostokąt, gdy nikt nie podał właściwego. */
object NeutralPhotoRenderer : PhotoRenderer {
    private val Neutral = Color(0xFF3A3A40)

    override fun DrawScope.drawPhoto(key: Int, rect: Rect, matrix: ColorMatrix?) {
        drawRect(Neutral, rect.topLeft, rect.size, colorFilter = matrix?.let { ColorFilter.colorMatrix(it) })
    }
}

val LocalPhotoRenderer = staticCompositionLocalOf<PhotoRenderer> { NeutralPhotoRenderer }

/** Rysuje zdjęcie bieżącym [LocalPhotoRenderer]. */
fun DrawScope.drawPhoto(renderer: PhotoRenderer, key: Int, rect: Rect, matrix: ColorMatrix? = null) {
    with(renderer) { drawPhoto(key, rect, matrix) }
}

/** Zdjęcie; [contentDescription] = null dla miniatur dekoracyjnych (opis ma element nadrzędny). */
@Composable
fun Photo(contentDescription: String?, key: Int, modifier: Modifier = Modifier, matrix: ColorMatrix? = null) {
    val renderer = LocalPhotoRenderer.current
    val described = if (contentDescription ==
        null
    ) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described) {
        drawPhoto(renderer, key, Rect(Offset.Zero, size), matrix)
    }
}
