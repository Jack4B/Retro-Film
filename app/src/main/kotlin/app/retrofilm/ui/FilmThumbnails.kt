package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.runtime.mutableStateMapOf
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FrameOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Miniatury filmów na bieżącym zdjęciu (id → bitmapa). Tworzone na żądanie dla widocznych kafelków
 * z LUT-ów z `EngineFilmEngine.load`, czyli z `LutMemoryCache`. Gotowe miniatury ostatnich [PHOTOS_KEPT] zdjęć
 * serii są trzymane w LRU ([switchTo]), więc powrót do zdjęcia nie generuje siatki od nowa (160 px ≈ 100 kB/szt.).
 */
class FilmThumbnails(private val graph: AppGraph, private val typeface: Typeface) {
    val bitmaps = mutableStateMapOf<String, Bitmap>()
    private val lock = Mutex()
    private var source: Bitmap? = null
    private var generation = 0

    private class Entry(val thumbs: Map<String, Bitmap>, val source: Bitmap?)

    private val recent = object : LinkedHashMap<Any, Entry>(PHOTOS_KEPT, LOAD, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Entry>) = size > PHOTOS_KEPT
    }
    private var key: Any? = null

    fun reset() {
        generation++
        bitmaps.clear()
        source = null
        key = null
        recent.clear()
    }

    /** Przełącza siatkę na zdjęcie [photoKey]: bieżące miniatury trafiają do LRU, zapamiętane wracają od razu. */
    fun switchTo(photoKey: Any) {
        if (photoKey == key) return
        generation++
        key?.let { recent[it] = Entry(HashMap(bitmaps), source) }
        val back = recent.remove(photoKey)
        bitmaps.clear()
        back?.let { bitmaps.putAll(it.thumbs) }
        source = back?.source
        key = photoKey
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

    /** Testy zrzutów ekranów podstawiają prostą miniaturę zamiast potoku filmu (grafika natywna Robolectric). */
    internal var renderOverride: ((String, Bitmap) -> Bitmap?)? = null

    private suspend fun render(id: String, photo: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        renderOverride?.let { return@withContext it(id, photo) }
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
        const val PHOTOS_KEPT = 3
        const val LOAD = 0.75f
    }
}
