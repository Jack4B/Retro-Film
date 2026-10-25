package app.retrofilm.engine

import androidx.compose.ui.graphics.ColorMatrix
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutAffineFit
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngine
import app.retrofilm.core.engine.LutEntry
import app.retrofilm.core.engine.LutMemoryCache
import app.retrofilm.core.ui.engine.FilmEngine
import app.retrofilm.core.ui.engine.FilmLut
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapter `core:ui` [FilmEngine] nad `core:engine`: lista klisz z [LutCatalog], podgląd jako
 * macierz koloru dopasowana MNK do LUT-u ([LutAffineFit]); pełny render przez [renderer] ([LutEngine]).
 */
class EngineFilmEngine(
    private val catalog: LutCatalog,
    val renderer: LutEngine,
    private val open: (LutCatalog, LutEntry) -> Lut = { c, e -> c.open(e) },
    /** Ograniczony LRU zdekodowanych LUT-ów (domyślnie 3 × 144³); wcześniej mapa bez limitu. */
    internal val luts: LutMemoryCache = LutMemoryCache()
) : FilmEngine {
    private val fits = ConcurrentHashMap<String, Array<FloatArray>>()

    override fun availableLuts(): List<FilmLut> = catalog.entries.map { FilmLut(it.id, it.name) }

    @Suppress("ReturnCount")
    override fun previewMatrix(lut: FilmLut?, intensity: Float): ColorMatrix? {
        if (lut == null || intensity <= 0f) return null
        val m = fits.getOrPut(lut.id) { LutAffineFit.fit(load(lut.id) ?: return null) }
        return toColorMatrix(m, intensity.coerceIn(0f, 1f))
    }

    /** Pełny LUT do renderu (eksport / pełna jakość); null gdy brak w katalogu. */
    fun load(id: String): Lut? = luts.getOrPut(id) {
        catalog.entries.firstOrNull { it.id == id }?.let { entry -> open(catalog, entry) }
    }

    companion object {
        private const val MAX_BYTE = 255f
        private const val COLS = 5
        private const val OFFSET_COL = 3

        /** Mieszanie z tożsamością wg [t]; przesunięcia w skali 0..255 (konwencja ColorMatrix). */
        @Suppress("MagicNumber")
        fun toColorMatrix(m: Array<FloatArray>, t: Float): ColorMatrix {
            val v = FloatArray(COLS * 4)
            for (c in 0 until 3) {
                for (k in 0 until 3) {
                    val id = if (c == k) 1f else 0f
                    v[c * COLS + k] = id + (m[c][k] - id) * t
                }
                v[c * COLS + 4] = m[c][OFFSET_COL] * t * MAX_BYTE
            }
            v[3 * COLS + 3] = 1f
            return ColorMatrix(v)
        }
    }
}
