package app.retrofilm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.export.SignatureTooLongException
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.PhotoLoader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Film do wyboru; [id] = [IDENTITY_ID] to wbudowany LUT tożsamościowy. [category] = pierwszy człon id. */
data class FilmOption(val id: String, val name: String?) {
    val category: String get() = if (id == IDENTITY_ID) "" else id.substringBefore('/', missingDelimiterValue = "")
}

/** Wynik operacji pokazywany w UI (snackbar). */
sealed interface UiMessage {
    data object SignatureTooLong : UiMessage
    data object Saved : UiMessage
    data object LoadFailed : UiMessage
    data class ExportFailed(val reason: String) : UiMessage
}

const val IDENTITY_ID = "identity"

/** Stan ścieżki testowej: zdjęcie → film → ramka/podpis → eksport. */
class MainState(private val graph: AppGraph, private val typeface: Typeface) {
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Podgląd oryginału (to samo skalowanie co [preview]) – pokazywany po przytrzymaniu zdjęcia. */
    var before by mutableStateOf<Bitmap?>(null)
        private set
    var film by mutableStateOf(IDENTITY_ID)

    /** Moc filmu 0..1 (miks z oryginałem); 1 = pełny film. */
    var intensity by mutableFloatStateOf(1f)
    var frame by mutableStateOf(FrameOptions())

    /** Ulubione filmy (id z katalogu); zapisywane w DataStore, odczyt przez [observeFavorites]. */
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Miniatury filmów na bieżącym zdjęciu (zob. [FilmThumbnails]). */
    val thumbnails = FilmThumbnails(graph, typeface)

    val films: List<FilmOption> by lazy {
        listOf(FilmOption(IDENTITY_ID, null)) +
            graph.filmEngine.availableLuts().filter { it.id != IDENTITY_ID }.map { FilmOption(it.id, it.name) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPhotoForTest(bitmap: Bitmap) {
        photo = bitmap
    }

    /** Wraca do ekranu startowego (nic nie jest zapisywane). */
    fun closePhoto() {
        photo = null
        preview = null
        before = null
        thumbnails.reset()
    }

    /** Zbiera ulubione z DataStore; wołane z `LaunchedEffect` ekranu. */
    suspend fun observeFavorites() {
        graph.appSettings.favoriteFilms.collect { favorites = it }
    }

    suspend fun toggleFavorite(id: String) {
        graph.appSettings.setFavorite(id, id !in favorites)
    }

    /** Status podpisu dla bieżącego zdjęcia i ramki (blokada eksportu przy TooLong). */
    fun signatureStatus(): SignatureFitStatus {
        val p = photo ?: return SignatureFitStatus.None
        return FrameRenderer.status(frame, p.width, p.height, typeface)
    }

    suspend fun load(context: Context, uri: Uri): UiMessage? = withBusy {
        try {
            val bmp = withContext(Dispatchers.IO) {
                PhotoLoader.load({ context.contentResolver.openInputStream(uri) ?: throw IOException("Brak pliku") })
            }
            photo = bmp
            preview = null
            before = null
            thumbnails.reset()
            null
        } catch (_: IOException) {
            UiMessage.LoadFailed
        }
    }

    /** Podgląd: ta sama ścieżka co eksport, na pomniejszonej kopii. */
    suspend fun refreshPreview() {
        val p = photo ?: return
        if (signatureStatus() == SignatureFitStatus.TooLong) return
        val (orig, shown) = withContext(Dispatchers.Default) {
            val small = scaleDown(p, PREVIEW_SIDE)
            val orig = before
                ?: ExportPipeline.render(small, null, graph.filmEngine.renderer, FrameOptions(), typeface)
            orig to ExportPipeline.render(small, lut(), graph.filmEngine.renderer, frame, typeface, intensity)
        }
        before = orig
        preview = shown
    }

    /** Eksport; nigdy nie rzuca – błędy wracają jako [UiMessage]. */
    suspend fun export(save: suspend (ByteArray) -> Unit): UiMessage = withBusy {
        val p = photo ?: return@withBusy UiMessage.LoadFailed
        if (signatureStatus() == SignatureFitStatus.TooLong) return@withBusy UiMessage.SignatureTooLong
        try {
            val jpeg = withContext(Dispatchers.Default) {
                val rendered = ExportPipeline.render(p, lut(), graph.filmEngine.renderer, frame, typeface, intensity)
                ExportPipeline.encode(p, rendered, frame, typeface)
            }
            withContext(Dispatchers.IO) { save(jpeg) }
            UiMessage.Saved
        } catch (_: SignatureTooLongException) {
            UiMessage.SignatureTooLong
        } catch (e: IOException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        } catch (e: IllegalStateException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        } catch (e: SecurityException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        }
    }

    /** Identity = brak zmian pikseli, więc pomijamy render (zachowuje dokładnie kolory i przestrzeń barw). */
    private fun lut(): Lut? = if (film == IDENTITY_ID) null else graph.filmEngine.load(film)

    private suspend fun <T> withBusy(block: suspend () -> T): T {
        busy = true
        return try {
            block()
        } finally {
            busy = false
        }
    }

    companion object {
        const val PREVIEW_SIDE = 1280

        fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
            val long = maxOf(src.width, src.height)
            if (long <= maxSide) return src
            val s = maxSide.toFloat() / long
            return Bitmap.createScaledBitmap(
                src,
                (src.width * s).toInt().coerceAtLeast(1),
                (src.height * s).toInt().coerceAtLeast(1),
                true
            )
        }
    }
}
