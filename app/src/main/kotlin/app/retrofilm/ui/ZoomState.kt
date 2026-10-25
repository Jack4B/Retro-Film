package app.retrofilm.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Powiększanie i przesuwanie podglądu: szczypanie, przeciąganie, dwukrotne dotknięcie. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)

    /** Przybliżenie na tyle duże, że warto policzyć podgląd w wyższej rozdzielczości. */
    val isDetailed: Boolean get() = scale >= DETAIL_SCALE

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Gest: [zoom] względem punktu [centroid] (współrzędne ekranu), potem przesunięcie o [pan]. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val applied = newScale / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        val rel = centroid - center
        offset = clamp(rel - (rel - offset) * applied + pan, newScale)
        scale = newScale
    }

    /** Dwukrotne dotknięcie: z powiększenia wraca do całości, z całości zbliża do [DOUBLE_TAP_SCALE] w punkcie [at]. */
    fun toggle(at: Offset) {
        if (scale > 1f) reset() else transform(at, Offset.Zero, DOUBLE_TAP_SCALE)
    }

    private fun clamp(o: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1f) / 2f
        val maxY = size.height * (forScale - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MAX_SCALE = 6f
        const val DOUBLE_TAP_SCALE = 2.5f
        const val DETAIL_SCALE = 1.5f
    }
}
