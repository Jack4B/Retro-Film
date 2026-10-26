package app.retrofilm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.SignatureTooLongException
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.AutoEnhance
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import app.retrofilm.pipeline.GeometryMath
import app.retrofilm.pipeline.LocalMask
import app.retrofilm.pipeline.PhotoLoader
import app.retrofilm.pipeline.Resampler
import app.retrofilm.pipeline.SignatureFont
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
    data class SavedMany(val saved: Int, val failed: Int) : UiMessage
    data class ExportFailed(val reason: String) : UiMessage
}

const val IDENTITY_ID = "identity"

/** Stan ścieżki testowej: zdjęcie → film → ramka/podpis → eksport. */
@Suppress("TooManyFunctions") // stan jednej ścieżki: wczytanie, podgląd, zapis pojedynczy i seria
class MainState(
    private val graph: AppGraph,
    private val typeface: Typeface,
    private val fonts: (SignatureFont) -> Typeface = { typeface }
) {
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
    private var previewCache: Triple<Bitmap, Int, Bitmap>? = null

    /** Ustawienia sprzed i po ostatnim auto-ulepszeniu; cofnięcie możliwe, dopóki nic go nie zmieniło. */
    private var autoPair by mutableStateOf<Pair<Adjustments, Adjustments>?>(null)

    val canUndoAuto: Boolean get() = autoPair?.second == adjustments

    /** Maska pokazywana jako czerwona nakładka na podglądzie, gdy ją ustawiamy (nie wpływa na zapis). */
    var maskOverlay by mutableStateOf<LocalMask?>(null)

    /** Jakość JPEG przy zapisie (70..100). */
    var exportQuality by mutableIntStateOf(JpegExporter.DEFAULT_QUALITY)

    /** Dłuższy bok zapisanego zdjęcia w px; 0 = oryginalny rozmiar. */
    var exportMaxSide by mutableIntStateOf(0)

    /** Presety użytkownika (film, moc i korekty) zapisywane w pamięci aplikacji. */
    val presetBook = PresetBook()

    /** Kadr i obrót; stosowany przed filmem, więc podgląd, ramka i zapis widzą ten sam kadr. */
    var geometry by mutableStateOf(Geometry())
        private set

    fun updateGeometry(g: Geometry) {
        geometry = g
        before = null
    }

    /** Zdjęcia wybrane w galerii (jedno lub kilka); te same ustawienia dotyczą wszystkich. */
    var uris by mutableStateOf<List<Uri>>(emptyList())
        private set

    /** Które z [uris] jest teraz w Edytorze (od 0). */
    var index by mutableIntStateOf(0)
        private set

    /** Numer zdjęcia zapisywanego w serii (od 1); 0 = nic się nie zapisuje. */
    var exportProgress by mutableIntStateOf(0)
        private set

    /** Moc filmu 0..1 (miks z oryginałem); 1 = pełny film. */
    var intensity by mutableFloatStateOf(1f)

    /** Korekty (ekspozycja, kontrast, nasycenie, temperatura) po nałożeniu filmu; neutralne = bez zmian. */
    var adjustments by mutableStateOf(Adjustments.None)
    var frame by mutableStateOf(FrameOptions())

    /** Podgląd w wyższej rozdzielczości, gdy zdjęcie jest przybliżone (zmiana odświeża podgląd i oryginał). */
    var previewDetail by mutableStateOf(false)
        private set

    fun updateDetail(detailed: Boolean) {
        if (detailed == previewDetail) return
        previewDetail = detailed
        before = null
    }

    /** Miniatury filmów na bieżącym zdjęciu (zob. [FilmThumbnails]). */
    val thumbnails = FilmThumbnails(graph, typeface)

    /** Filmy startowe i pobrane oraz ulubione (zob. [FilmLibrary]). */
    val library = FilmLibrary(graph)

    @androidx.annotation.VisibleForTesting
    internal fun setPhotoForTest(bitmap: Bitmap) {
        photo = bitmap
    }

    /** Wraca do ekranu startowego (nic nie jest zapisywane). */
    fun closePhoto() {
        geometry = Geometry()
        uris = emptyList()
        index = 0
        photo = null
        preview = null
        before = null
        autoPair = null
        previewCache = null
        thumbnails.reset()
    }

    /** Status podpisu dla bieżącego zdjęcia i ramki (blokada eksportu przy TooLong). */
    fun signatureStatus(): SignatureFitStatus {
        val p = photo ?: return SignatureFitStatus.None
        val (w, h) = GeometryMath.outputSize(p.width, p.height, geometry)
        return FrameRenderer.status(frame, w, h, signatureTypeface())
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

    /** Start z galerii: pierwsze zdjęcie otwiera Edytor, reszta czeka w pasku zdjęć. */
    suspend fun loadAll(context: Context, picked: List<Uri>): UiMessage? {
        if (picked.isEmpty()) return null
        uris = picked
        index = 0
        return load(context, picked.first()).also { if (it != null) uris = emptyList() }
    }

    /** Przełącza Edytor na inne zdjęcie z serii, zachowując film, korekty i ramkę. */
    suspend fun select(context: Context, i: Int): UiMessage? {
        if (i == index || i !in uris.indices) return null
        index = i
        return load(context, uris[i])
    }

    /** Zapisuje wszystkie zdjęcia z serii z bieżącymi ustawieniami; nigdy nie rzuca. */
    suspend fun exportAll(context: Context, save: suspend (ByteArray) -> Unit): UiMessage = withBusy {
        val lutNow = lut()
        val face = signatureTypeface()
        val result = exportBatch(uris, { exportProgress = it + 1 }) { uri ->
            val bmp = withContext(Dispatchers.IO) {
                PhotoLoader.load({ context.contentResolver.openInputStream(uri) ?: throw IOException("Brak pliku") })
            }
            val jpeg = withContext(Dispatchers.Default) {
                val cropped = GeometryApplier.apply(bmp, geometry)
                val rendered = ExportPipeline.render(
                    cropped,
                    lutNow,
                    graph.filmEngine.renderer,
                    frame,
                    face,
                    intensity,
                    adjustments
                )
                ExportPipeline.encode(
                    cropped,
                    ExportPipeline.resize(rendered, exportMaxSide),
                    frame,
                    face,
                    exportQuality
                )
            }
            withContext(Dispatchers.IO) { save(jpeg) }
        }
        exportProgress = 0
        UiMessage.SavedMany(result.saved, result.failed)
    }

    /** Podgląd: ta sama ścieżka co eksport, na pomniejszonej kopii. */
    suspend fun refreshPreview() {
        val p = photo ?: return
        if (signatureStatus() == SignatureFitStatus.TooLong) return
        val (orig, shown) = withContext(Dispatchers.Default) {
            val small = GeometryApplier.apply(previewBase(p), geometry)
            val orig = before
                ?: ExportPipeline.render(small, null, graph.filmEngine.renderer, FrameOptions(), typeface)
            orig to
                ExportPipeline.render(
                    small,
                    lut(),
                    graph.filmEngine.renderer,
                    frame,
                    signatureTypeface(),
                    intensity,
                    adjustments
                )
        }
        before = orig
        preview = shown
    }

    /**
     * Auto-ulepszenie: analizuje podgląd po filmie i kadrze (bez korekt), ustawia suwaki Światło i Kolor i zapamiętuje
     * poprzednie ustawienia do cofnięcia ([undoAuto]).
     */
    suspend fun autoEnhance() {
        val p = photo ?: return
        val result = withContext(Dispatchers.Default) {
            val small = GeometryApplier.apply(previewBase(p), geometry)
            val graded = ExportPipeline.render(
                small,
                lut(),
                graph.filmEngine.renderer,
                FrameOptions(),
                typeface,
                intensity
            )
            val px = IntArray(graded.width * graded.height)
            graded.getPixels(px, 0, graded.width, 0, 0, graded.width, graded.height)
            AutoEnhance.suggest(px, graded.width, graded.height)
        }
        val before = adjustments
        adjustments = result.applyTo(before)
        autoPair = before to adjustments
    }

    fun undoAuto() {
        autoPair?.let { adjustments = it.first }
        autoPair = null
    }

    /** Zmniejszona kopia zdjęcia do podglądu (filtr Lanczosa jest drogi, więc liczona raz na zdjęcie i rozmiar). */
    private fun previewBase(p: Bitmap): Bitmap {
        val side = if (previewDetail) PREVIEW_SIDE_DETAIL else PREVIEW_SIDE
        val cached = previewCache
        if (cached != null && cached.first === p && cached.second == side) return cached.third
        return scaleDown(p, side).also { previewCache = Triple(p, side, it) }
    }

    /** Eksport; nigdy nie rzuca – błędy wracają jako [UiMessage]. */
    suspend fun export(save: suspend (ByteArray) -> Unit): UiMessage = withBusy {
        val p = photo ?: return@withBusy UiMessage.LoadFailed
        if (signatureStatus() == SignatureFitStatus.TooLong) return@withBusy UiMessage.SignatureTooLong
        try {
            val jpeg = withContext(Dispatchers.Default) {
                val cropped = GeometryApplier.apply(p, geometry)
                val rendered = ExportPipeline.render(
                    cropped,
                    lut(),
                    graph.filmEngine.renderer,
                    frame,
                    signatureTypeface(),
                    intensity,
                    adjustments
                )
                ExportPipeline.encode(
                    cropped,
                    ExportPipeline.resize(rendered, exportMaxSide),
                    frame,
                    signatureTypeface(),
                    exportQuality
                )
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

    private fun signatureTypeface(): Typeface = fonts(frame.font)

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
        const val PREVIEW_SIDE_DETAIL = 2560

        fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
            val long = maxOf(src.width, src.height)
            if (long <= maxSide) return src
            val s = maxSide.toFloat() / long
            val w = (src.width * s).toInt().coerceAtLeast(1)
            val h = (src.height * s).toInt().coerceAtLeast(1)
            val px = IntArray(src.width * src.height)
            src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
            val cs = src.colorSpace
            val out = if (cs != null) {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
            } else {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
            out.setPixels(Resampler.resize(px, src.width, src.height, w, h), 0, w, 0, 0, w, h)
            return out
        }
    }
}
