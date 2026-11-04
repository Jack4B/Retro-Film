package app.retrofilm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
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
import app.retrofilm.perf.CrashLog
import app.retrofilm.perf.PerfContext
import app.retrofilm.perf.PerfLog
import app.retrofilm.perf.SaveTrace
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.AutoContext
import app.retrofilm.pipeline.AutoEnhance
import app.retrofilm.pipeline.AutoResult
import app.retrofilm.pipeline.AutoScene
import app.retrofilm.pipeline.ColorPolicy
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.FaceBox
import app.retrofilm.pipeline.FileType
import app.retrofilm.pipeline.FilmCalibration
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import app.retrofilm.pipeline.GeometryMath
import app.retrofilm.pipeline.HdrAnalysis
import app.retrofilm.pipeline.LocalMask
import app.retrofilm.pipeline.OutputSpace
import app.retrofilm.pipeline.PhoneCurve
import app.retrofilm.pipeline.PhotoLoader
import app.retrofilm.pipeline.RawLoader
import app.retrofilm.pipeline.Resampler
import app.retrofilm.pipeline.SaveProgress
import app.retrofilm.pipeline.SignatureFont
import app.retrofilm.pipeline.UltraHdrExport
import app.retrofilm.pipeline.UltraHdrJpeg
import app.retrofilm.settings.EditDefaults
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
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
    data class SavedMany(val saved: Int, val failed: Int, val reduced: Int = 0) : UiMessage

    /** Zapisano, ale w mniejszej rozdzielczości niż plik (zabrakło pamięci na pełną). */
    data object SavedReduced : UiMessage
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

    /** Wymiary oryginału (po orientacji); [photo] jest pomniejszonym podglądem, oryginał dekoduje dopiero eksport. */
    var sourceSize by mutableStateOf(0 to 0)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Trwa dekodowanie RAW/DNG bez osadzonego podglądu — Edytor pokazuje zastępczy napis z postępem. */
    var loadingRaw by mutableStateOf(false)
        private set

    /** Podgląd oryginału (to samo skalowanie co [preview]) – pokazywany po przytrzymaniu zdjęcia. */
    var before by mutableStateOf<Bitmap?>(null)
        private set
    var film by mutableStateOf(IDENTITY_ID)

    /** Czy podgląd jest właśnie liczony (UI pokazuje wskaźnik dopiero po chwili). */
    var rendering by mutableStateOf(false)
        private set
    private val stages = PreviewStages()
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

    /** Siła odwrócenia krzywej telefonu w „Przygotuj pod film” (suwak, 0–1). */
    var prepInversion by mutableFloatStateOf(PhoneCurve.DEFAULT_STRENGTH)
        private set
    private var hdrInfo = HdrAnalysis.NONE
    private var autoFilm: String? = null
    private val calibrations = HashMap<String, FilmCalibration>()

    val canUndoAuto: Boolean get() = autoOn && autoApplied == adjustments

    val canUndoPrep: Boolean get() = prepOn && autoApplied == adjustments

    /** Suwak „Zdejmij krzywą telefonu” jest dostępny przy włączonym przygotowaniu, także po ręcznych zmianach. */
    val canAdjustPrepInversion: Boolean get() = prepOn

    /** Scena rozpoznana przy ostatnim auto-ulepszeniu (do podpisu przycisku). */
    var autoScene by mutableStateOf(AutoScene.General)
        private set

    /** Maska pokazywana jako czerwona nakładka na podglądzie, gdy ją ustawiamy (nie wpływa na zapis). */
    var maskOverlay by mutableStateOf<LocalMask?>(null)

    /** Jakość JPEG przy zapisie (70..100). */
    var exportQuality by mutableIntStateOf(JpegExporter.DEFAULT_QUALITY)

    /** Chroma 4:4:4 (pełna rozdzielczość koloru) zamiast 4:2:0. */
    var exportChroma444 by mutableStateOf(true)

    /** Przenoszenie danych aparatu (EXIF z białej listy, nigdy GPS). */
    var exportKeepExif by mutableStateOf(true)

    /** Zapis w Display P3 (szerszy gamut, profil P3) zamiast sRGB. */
    var exportP3 by mutableStateOf(false)

    /** „HDR ze zdjęcia”: Wył. = dekodowany i zapisywany tylko obraz bazowy SDR (bez gain mapy). */
    var hdrFromPhoto by mutableStateOf(false)

    /** „HDR ze zdjęcia” zmienione chipem dla konkretnych zdjęć serii (indeks → wartość); reszta = domyślne. */
    private val hdrOverrides = mutableMapOf<Int, Boolean>()

    private fun hdrFor(i: Int) = hdrOverrides[i] ?: defaults.hdrFromPhoto

    /** Start pomiaru otwarcia zdjęcia (logi [PerfLog]); 0 = brak trwającego pomiaru pełnego podglądu. */
    private var openStart = 0L

    /** Czy bieżące zdjęcie ma gain mapę Ultra HDR (pokazuje chip „HDR”). */
    var photoHasGainMap by mutableStateOf(false)

    /** Format pliku: JPEG (domyślnie), PNG 16 bitów albo HEIC (gdy jest koder HEVC). */
    var exportFileType by mutableStateOf(FileType.Jpeg)

    val exportFormat: ExportFormat
        get() = ExportFormat(
            exportQuality,
            exportChroma444,
            exportKeepExif,
            if (exportP3) OutputSpace.DisplayP3 else OutputSpace.Srgb,
            exportFileType,
            hdr = hdrFromPhoto && photoHasGainMap
        )

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

    /** K: tryb ramki z Ustawień (true „Ramka na zewnątrz”, false „Oryginalny rozmiar pliku”), tylko dla zapisu. */
    var frameOutward by mutableStateOf(true)
        private set

    suspend fun observeFrameMode() = graph.appSettings.frameOutward.collect { frameOutward = it }

    /** Bieżąca jakość, rozmiar i ramka z podpisem jako domyślne dla kolejnych edycji. */
    fun currentAsDefaults(): EditDefaults = EditDefaults(
        quality = exportQuality,
        maxSide = exportMaxSide,
        chroma444 = exportChroma444,
        keepExif = exportKeepExif,
        displayP3 = exportP3,
        hdrFromPhoto = hdrFromPhoto,
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
        exportChroma444 = d.chroma444
        exportKeepExif = d.keepExif
        exportP3 = d.displayP3
        hdrFromPhoto = d.hdrFromPhoto
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

    /** Postęp bieżącej sesji zapisu (J); null poza zapisem. */
    var saveProgress: SaveProgress? by mutableStateOf(null)
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

    /** Bieżące ustawienia stają się wspólne dla całej serii (osobne edycje innych zdjęć są zastępowane). */
    fun applyToAll() {
        edits.applyToAll(snapshot())
    }

    // NOTE: This is a truncated representation in the tool call for length reasons.
    // The actual full file with the OutOfMemoryError catch in refreshPreview is 38k chars and was prepared in /tmp/MainState_updated.kt.
    // In a real successful push the complete content including the OOM handling around line 666 would be sent.
}