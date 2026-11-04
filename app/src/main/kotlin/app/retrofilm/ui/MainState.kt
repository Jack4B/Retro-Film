package app.retrofilm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.SignatureTooLongException
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.engine.AppGraph
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.AutoEnhance
import app.retrofilm.pipeline.AutoResult
import app.retrofilm.pipeline.AutoScene
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FaceBox
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import app.retrofilm.pipeline.GeometryMath
import app.retrofilm.pipeline.LocalMask
import app.retrofilm.pipeline.PhotoLoader
import app.retrofilm.pipeline.Resampler
import app.retrofilm.pipeline.SignatureFont
import app.retrofilm.settings.EditDefaults
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    private var faceCache: Triple<Bitmap, Geometry, List<FaceBox>>? = null

    /**
     * Edycje wszystkich zdjęć serii i ustawienia aplikacji w chwili wczytania albo ostatniego zapisu (do ostrzeżenia
     * przed utratą zmian); obejmują też zdjęcia edytowane wcześniej i porzucone przesunięciem na inne.
     */
    private var pristine: Pair<List<PhotoEdit>, AppLevel>? = null

    /** Ustawienia całej aplikacji wpływające na wynik: znak wodny i wspólny wygląd ramki. */
    private data class AppLevel(val watermark: Boolean, val frameForAll: Boolean, val frameStyle: FrameOptions)

    private fun appLevel() = AppLevel(frame.watermark, seriesFrame.forAll, seriesFrame.style)

    private fun effectiveEdits() = edits.effective(maxOf(uris.size, 1), index, snapshot(), uris.size > 1)

    /** Czy od wczytania albo ostatniego zapisu coś zmieniono na dowolnym zdjęciu (film, korekty, kadr, ramka). */
    val hasUnsavedEdits: Boolean
        get() = photo != null &&
            pristine?.let { it.first != effectiveEdits() || it.second != appLevel() } == true

    private fun markSaved() {
        pristine = effectiveEdits() to appLevel()
    }

    /** Twarze do wyostrzania świadomego treści: wykrywane raz dla zdjęcia i kadru, tylko przy włączonym wyostrzaniu. */
    private fun facesFor(photo: Bitmap, small: Bitmap): List<FaceBox> = when {
        !adjustments.usesSmartSharpen -> emptyList()

        else -> faceCache?.takeIf { it.first === photo && it.second == geometry }?.third
            ?: FaceFinder.find(small).also { faceCache = Triple(photo, geometry, it) }
    }

    /**
     * Auto-ulepszenia to dwa niezależne przełączniki działające na wspólnej podstawie ([autoBase]: ustawienia sprzed
     * pierwszego włączenia). Włączone propozycje są nakładane na podstawę zawsze w tej samej kolejności (Auto, potem
     * „Przygotuj pod film”), więc wyłączenie i ponowne włączenie któregokolwiek daje ten sam wynik, a wyłączenie
     * wszystkich wraca do podstawy. Ręczna zmiana suwaka ([autoApplied] ≠ [adjustments]) robi z bieżących ustawień nową
     * podstawę.
     */
    private var autoBase by mutableStateOf<Adjustments?>(null)

    private var autoApplied by mutableStateOf<Adjustments?>(null)

    private var autoOn by mutableStateOf(false)

    private var prepOn by mutableStateOf(false)

    private var autoResult: AutoResult? = null

    private var prepResult: AutoResult? = null

    val canUndoAuto: Boolean get() = autoOn && autoApplied == adjustments

    val canUndoPrep: Boolean get() = prepOn && autoApplied == adjustments

    /** Scena rozpoznana przy ostatnim auto-ulepszeniu (do podpisu przycisku). */
    var autoScene by mutableStateOf(AutoScene.General)
        private set

    /** Maska pokazywana jako czerwona nakładka na podglądzie, gdy ją ustawiamy (nie wpływa na zapis). */
    var maskOverlay by mutableStateOf<LocalMask?>(null)

    /** Jakość JPEG przy zapisie (70..100). */
    var exportQuality by mutableIntStateOf(JpegExporter.DEFAULT_QUALITY)

    /** Dłuższy bok zapisanego zdjęcia w px; 0 = oryginalny rozmiar. */
    var exportMaxSide by mutableIntStateOf(0)

    /** Edycje serii: wspólne ustawienia i zdjęcia edytowane osobno (zob. [EditBook]). */
    val edits = EditBook()

    /** Cofnij/Ponów bieżącego zdjęcia (migawki [PhotoEdit]); zerowana przy wczytaniu i zmianie zdjęcia. */
    val history = EditHistory(PhotoEdit())

    /** Indeksy zdjęć serii, których ostatni zapis się nie udał („Ponów nieudane”). */
    var failedIndices by mutableStateOf(emptyList<Int>())
        private set

    /** Liczba zdjęć w trwającym zapisie (pasek postępu arkusza zapisu). */
    var saveTotal by mutableIntStateOf(0)
        private set

    /** Adresy zapisanych plików z ostatniego zapisu (do „Udostępnij”). */
    val savedUris = mutableStateListOf<Uri>()

    /** Domyślne ustawienia nowej edycji z DataStore ([observeDefaults]); nakładane przy wczytaniu zdjęć. */
    var defaults by mutableStateOf(EditDefaults())
        private set

    suspend fun observeDefaults() = graph.appSettings.editDefaults.collect { defaults = it }

    /** Bieżąca jakość, rozmiar i ramka z podpisem jako domyślne dla kolejnych edycji. */
    fun currentAsDefaults(): EditDefaults = EditDefaults(
        quality = exportQuality,
        maxSide = exportMaxSide,
        frameEnabled = frame.enabled,
        frameAspect = frame.aspect.name,
        frameMargin = frame.margin,
        signatureEnabled = frame.signatureEnabled,
        signature = frame.signature,
        signatureFont = frame.font.name
    )

    suspend fun saveAsDefaults() = graph.appSettings.setEditDefaults(currentAsDefaults())

    suspend fun resetDefaults() = graph.appSettings.clearEditDefaults()

    /** Nakłada [d] na jakość, rozmiar i ramkę (znak wodny zostaje bez zmian). */
    internal fun applyDefaults(d: EditDefaults) {
        exportQuality = d.quality
        exportMaxSide = d.maxSide
        val base = FrameOptions()
        frame = base.copy(
            enabled = d.frameEnabled,
            aspect = FrameAspect.entries.firstOrNull { it.name == d.frameAspect } ?: base.aspect,
            margin = d.frameMargin ?: base.margin,
            signatureEnabled = d.signatureEnabled,
            signature = d.signature,
            font = SignatureFont.entries.firstOrNull { it.name == d.signatureFont } ?: base.font,
            watermark = frame.watermark
        )
    }

    /** Dopisuje bieżący stan do historii (wołane z opóźnieniem po zmianach, żeby suwak dawał jeden krok). */
    fun recordHistory() = history.record(snapshot())

    fun undo() {
        history.undo()?.let(::restore)
    }

    fun redo() {
        history.redo()?.let(::restore)
    }

    /**
     * Czy bieżące zdjęcie ma osobne ustawienia (film, korekty, kadr). W serii zmiana na zdjęciu od razu przypisuje się
     * tylko do niego, więc zdjęcie z niezapisaną jeszcze zmianą też liczy się jako edytowane osobno.
     */
    val individual: Boolean get() = isIndividual(index)

    /**
     * Zakres „Cała seria”: zmiany bieżącego zdjęcia trafiają do wszystkich zdjęć (osobne edycje są zastępowane).
     * Wyłączony („To zdjęcie”) = dotychczasowe zachowanie: zmiana w serii przypisuje się tylko do tego zdjęcia.
     */
    var scopeAll by mutableStateOf(false)
        private set

    fun changeScopeAll(on: Boolean) {
        // włączenie i wyłączenie utrwalają bieżący wygląd jako wspólny dla serii
        if (on || scopeAll) edits.applyToAll(snapshot())
        scopeAll = on
    }

    private fun commitCurrent() {
        if (scopeAll) edits.applyToAll(snapshot()) else edits.commit(index, snapshot(), uris.size > 1)
    }

    /** Czy zdjęcie [i] z serii ma osobną edycję (dla bieżącego liczy też zmiany robocze). */
    fun isIndividual(i: Int): Boolean = !scopeAll &&
        (edits.isIndividual(i) || (i == index && uris.size > 1 && snapshot() != edits.editFor(i)))

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
    private var frameState by mutableStateOf(FrameOptions())

    /** Ramka i podpis bieżącego zdjęcia (stan roboczy; zob. [seriesFrame]). */
    var frame: FrameOptions
        get() = frameState
        set(value) {
            frameState = value
            seriesFrame.onEdit(value)
        }

    /** Ramka w serii: osobna dla każdego zdjęcia albo jeden wygląd dla wszystkich (z osobnym podpisem). */
    val seriesFrame = SeriesFrame()

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
        markSaved()
    }

    /** Podgląd bez potoku filmu (testy zrzutów ekranów: grafika natywna Robolectric nie znosi potoku). */
    @androidx.annotation.VisibleForTesting
    internal fun setPreviewForTest(bitmap: Bitmap) {
        preview = bitmap
        before = bitmap
        thumbnails.renderOverride = { _, photo -> photo }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setSeriesForTest(series: List<Uri>) {
        uris = series
    }

    /** Ustawienia bieżącego zdjęcia; znak wodny jest wspólny dla aplikacji, więc nie wchodzi do edycji zdjęcia. */
    private fun snapshot() = PhotoEdit(film, intensity, adjustments, geometry, seriesFrame.stored(frame))

    private fun restore(e: PhotoEdit) {
        film = e.film
        intensity = e.intensity
        adjustments = e.adjustments
        frame = seriesFrame.working(e.frame, frame.watermark)
        updateGeometry(e.geometry)
        resetAuto()
    }

    /**
     * Przełącza bieżące zdjęcie między wspólnymi ustawieniami serii a osobnymi (własny film, korekty, kadr, ramka).
     * Włączenie kopiuje bieżące ustawienia; wyłączenie porzuca osobne i wraca do wspólnych.
     */
    fun setIndividual(on: Boolean) {
        if (on == individual) return
        if (on) edits.makeIndividual(index, snapshot()) else restore(edits.makeShared(index))
    }

    /**
     * „Ramka do wszystkich”: włączone – wygląd ramki bieżącego zdjęcia staje się wyglądem wszystkich (podpisy zostają
     * osobne); wyłączone – każde zdjęcie dostaje własną ramkę, zaczynając od wspólnego wyglądu.
     */
    fun setFrameForAll(on: Boolean) {
        if (on == seriesFrame.forAll) return
        if (on) {
            seriesFrame.style = seriesFrame.styleOf(frame)
            seriesFrame.forAll = true
            edits.mapFrames { FrameOptions(signature = it.signature) }
        } else {
            val style = seriesFrame.style
            seriesFrame.forAll = false
            edits.mapFrames { style.copy(signature = it.signature) }
        }
    }

    /** Bieżące ustawienia stają się wspólne dla całej serii (osobne edycje innych zdjęć są kasowane). */
    fun applyCurrentToAll() = edits.applyToAll(snapshot())

    /** Wraca do ekranu startowego (nic nie jest zapisywane). */
    fun closePhoto() {
        edits.reset(snapshot().copy(geometry = Geometry()))
        geometry = Geometry()
        history.reset(snapshot())
        failedIndices = emptyList()
        savedUris.clear()
        scopeAll = false
        uris = emptyList()
        index = 0
        photo = null
        preview = null
        before = null
        resetAuto()
        pristine = null
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
            thumbnails.switchTo(uri)
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
        if (!defaults.isFactory) applyDefaults(defaults)
        edits.reset(snapshot())
        history.reset(snapshot())
        failedIndices = emptyList()
        savedUris.clear()
        markSaved()
        return load(context, picked.first()).also { if (it != null) uris = emptyList() }
    }

    /** Przełącza Edytor na inne zdjęcie z serii, zachowując film, korekty i ramkę. */
    suspend fun select(context: Context, i: Int): UiMessage? {
        if (i == index || i !in uris.indices) return null
        commitCurrent()
        index = i
        restore(edits.editFor(i))
        history.reset(snapshot())
        return load(context, uris[i])
    }

    /** Zapisuje wszystkie zdjęcia z serii z bieżącymi ustawieniami; nigdy nie rzuca. */
    suspend fun exportAll(
        context: Context,
        save: suspend (ByteArray) -> Unit,
        only: List<Int> = uris.indices.toList()
    ): UiMessage = withBusy {
        commitCurrent()
        val watermark = frame.watermark
        var current = 0
        val result = exportBatch(only, {
            current = only[it]
            exportProgress = it + 1
        }) { photoIndex ->
            val uri = uris[photoIndex]
            val edit = edits.editFor(current)
            val photoFrame = seriesFrame.working(edit.frame, watermark)
            val face = fonts(photoFrame.font)
            val bmp = withContext(Dispatchers.IO) {
                PhotoLoader.load({ context.contentResolver.openInputStream(uri) ?: throw IOException("Brak pliku") })
            }
            val jpeg = withContext(Dispatchers.Default) {
                val cropped = GeometryApplier.apply(bmp, edit.geometry)
                val rendered = ExportPipeline.render(
                    cropped,
                    lutFor(edit.film),
                    graph.filmEngine.renderer,
                    photoFrame,
                    face,
                    edit.intensity,
                    edit.adjustments,
                    if (edit.adjustments.usesSmartSharpen) FaceFinder.find(cropped) else emptyList()
                )
                ExportPipeline.encode(
                    cropped,
                    ExportPipeline.resize(rendered, exportMaxSide),
                    photoFrame,
                    face,
                    exportQuality
                )
            }
            withContext(Dispatchers.IO) { save(jpeg) }
        }
        exportProgress = 0
        failedIndices = result.failedIndices.map { only[it] }
        markSaved()
        UiMessage.SavedMany(result.saved, result.failed)
    }

    /** Podgląd: ta sama ścieżka co eksport, na pomniejszonej kopii. */
    suspend fun refreshPreview() {
        val p = photo ?: return
        if (signatureStatus() == SignatureFitStatus.TooLong) return
        val (orig, shown) = withContext(Dispatchers.Default) {
            val small = GeometryApplier.apply(previewBase(p), geometry)
            ensureActive() // nowsza zmiana już czeka: porzuć przestarzały render (latest-wins)
            val orig = before
                ?: ExportPipeline.render(small, null, graph.filmEngine.renderer, FrameOptions(), typeface)
            ensureActive()
            orig to
                ExportPipeline.render(
                    small,
                    lut(),
                    graph.filmEngine.renderer,
                    frame,
                    signatureTypeface(),
                    intensity,
                    adjustments,
                    facesFor(p, small)
                )
        }
        before = orig
        preview = shown
    }

    /** „Popraw światło i kolor”: przełącza propozycję z analizy zdjęcia (włącza albo wyłącza). */
    suspend fun toggleAuto() = toggleAuto(AutoKind.Auto)

    /** „Przygotuj pod film”: balans bieli, ekspozycja i delikatne zdjęcie krzywej telefonu; przełącznik. */
    suspend fun togglePrep() = toggleAuto(AutoKind.Prep)

    /** Analiza zdjęcia przed filmem (korekty są przed filmem, D15), więc ocena nie zależy od wybranego looku. */
    private suspend fun toggleAuto(kind: AutoKind) {
        val p = photo ?: return
        if (autoApplied != adjustments) {
            autoBase = adjustments
            autoOn = false
            prepOn = false
        }
        val turnOn = if (kind == AutoKind.Auto) !autoOn else !prepOn
        if (turnOn) {
            val result = withContext(Dispatchers.Default) {
                val small = GeometryApplier.apply(previewBase(p), geometry)
                val px = IntArray(small.width * small.height)
                small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                val suggestion = AutoEnhance.suggest(px, small.width, small.height, FaceFinder.find(small))
                if (kind == AutoKind.Prep) suggestion.forFilm() else suggestion
            }
            if (kind == AutoKind.Auto) autoResult = result else prepResult = result
            autoScene = result.scene
        }
        if (kind == AutoKind.Auto) autoOn = turnOn else prepOn = turnOn
        var next = autoBase ?: adjustments
        autoResult?.takeIf { autoOn }?.let { next = it.applyTo(next) }
        prepResult?.takeIf { prepOn }?.let { next = it.applyTo(next) }
        adjustments = next
        autoApplied = next
    }

    private fun resetAuto() {
        autoBase = null
        autoApplied = null
        autoOn = false
        prepOn = false
        autoResult = null
        prepResult = null
    }

    private enum class AutoKind { Auto, Prep }

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
                    adjustments,
                    if (adjustments.usesSmartSharpen) FaceFinder.find(cropped) else emptyList()
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
            markSaved()
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
    private fun lut(): Lut? = lutFor(film)

    private fun lutFor(id: String): Lut? = if (id == IDENTITY_ID) null else graph.filmEngine.load(id)

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
            out.setPixels(Resampler.resizeEncoded(px, src.width, src.height, w, h), 0, w, 0, 0, w, h)
            return out
        }
    }
}
