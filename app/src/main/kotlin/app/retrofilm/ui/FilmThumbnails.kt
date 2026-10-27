package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.runtime.mutableStateMapOf
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FrameOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Miniatury filmów na bieżącym zdjęciu (id → bitmapa). Tworzone na żądanie dla widocznych kafelków
 * z LUT-ów z `EngineFilmEngine.load`, czyli z `LutMemoryCache`. Bez osobnej pamięci podręcznej:
 * mapa żyje tyle co zdjęcie i jest czyszczona przez [reset].
 */
class FilmThumbnails(private val graph: AppGraph, private val typeface: Typeface) {
    val bitmaps = mutableStateMapOf<String, Bitmap>()
    private val lock = Mutex()
    private var source: Bitmap? = null
    private var generation = 0

    fun reset() {
        generation++
        bitmaps.clear()
        source = null
    }

    /**
     * Miniatura filmu [id] na zdjęciu [photo] (dla [IDENTITY_ID] – oryginał). Wywołania są szeregowane,
     * żeby LRU z `LutMemoryCache` (3 filmy) nie był szarpany równoległym dekodowaniem.
     */
    suspend fun ensure(id: String, photo: Bitmap?) {
        if (photo == null || id in bitmaps) return
        lock.withLock {
            val started = generation
            val bmp = if (id in bitmaps) null else render(id, photo)
            if (bmp != null && started == generation) bitmaps[id] = bmp
        }
    }

    /** Wątek renderowania miniatur; testy zrzutów podmieniają go na wątek wywołującego. */
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default

    private suspend fun render(id: String, photo: Bitmap): Bitmap? = withContext(dispatcher) {
        val src = source ?: MainState.scaleDown(photo, THUMB_SIDE).also { source = it }
        val lut = if (id == IDENTITY_ID) null else graph.filmEngine.load(id)
        if (lut == null && id != IDENTITY_ID) {
            null
        } else {
            ExportPipeline.render(src, lut, graph.filmEngine.renderer, FrameOptions(), typeface)
        }
    }

    private companion object {
        const val THUMB_SIDE = 160
    }
}
