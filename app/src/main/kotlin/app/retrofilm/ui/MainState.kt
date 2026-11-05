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

/** Widok pinch-zoom przekazywany z EditorScreen do podglądu ROI (O). */
data class ZoomViewport(val scale: Float, val offsetX: Float, val offsetY: Float, val viewW: Int, val viewH: Int)

/** Stan ścieżki testowej: zdjęcie → film → ramka/podpis → eksport. */
@Suppress("TooManyFunctions", "LargeClass") // stan jednej ścieżki: wczytanie, podgląd, zapis pojedynczy i seria
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

    /** O/P: wycinek ROI bez ramki; z ramką zawsze false – zoom Compose na zdjęciu+ramce. */
    var previewRoiActive by mutableStateOf(false)
        private set

    private var zoomViewport: ZoomViewport? = null

    /** Zmiana widocznego ROI – MainScreen odświeża podgląd. */
    var roiEpoch by mutableIntStateOf(0)
        private set

    fun updateDetail(detailed: Boolean) {
        if (detailed == previewDetail) return
        previewDetail = detailed
        if (!detailed) {
            previewRoiActive = false
            zoomViewport = null
        }
        // N/O: zwolnij etapy i bazę poprzedniej rozdzielczości
        previewCache = null
        stages.clear()
        before = null
    }

    /** Widoczny fragment przy pinch-zoom (scale/offset z ZoomState + rozmiar pola). */
    fun updateZoomViewport(scale: Float, offsetX: Float, offsetY: Float, viewW: Int, viewH: Int) {
        if (viewW <= 0 || viewH <= 0) return
        val next = ZoomViewport(scale, offsetX, offsetY, viewW, viewH)
        if (next != zoomViewport) {
            zoomViewport = next
            roiEpoch++
        }
    }

    /**
     * Hak testowy: wywoływany na początku renderu podglądu. W produkcji null.
     * Pozwala unit-testowi wstrzyknąć OutOfMemoryError przy trybie szczegółowym.
     */
    @androidx.annotation.VisibleForTesting
    internal var previewBomb: (() -> Unit)? = null

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
        val (sw, sh) = sourceOf(p)
        val (w, h) = GeometryMath.outputSize(sw, sh, geometry)
        return FrameRenderer.status(frame, w, h, signatureTypeface())
    }

    /** Zmierzony zapas HDR w EV: z treści gain mapy (I) albo z dekodu F16 DNG (J); null = prior z nagłówka. */
    private fun measureHeadroom(bytes: ByteArray): Float? = when {
        photoHasGainMap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            UltraHdrExport.measuredHeadroom({ java.io.ByteArrayInputStream(bytes) }, hdrInfo.headroomEv)

        HdrAnalysis.isTiff(bytes) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> RawLoader.analyseHeadroom(bytes)

        else -> null
    }

    /** Wymiary oryginału dla [p] (gdy nieznane: wymiary bitmapy). */
    fun sourceOf(p: Bitmap): Pair<Int, Int> = sourceSize.takeIf { it.first > 0 } ?: (p.width to p.height)

    suspend fun load(context: Context, uri: Uri): UiMessage? = withBusy {
        val start = PerfLog.now()
        openStart = start
        updatePerfContext()
        try {
            // Jeden odczyt pliku zamiast 4 (EXIF, miniatura, wymiary, dekodowanie przez ContentResolver); zdjęcia
            // „ruchome” (Motion/Live Photo) mają za JPEG-iem ~10 MB wideo i gain mapę, więc każdy odczyt był drogi.
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Brak pliku")
            }
            photoHasGainMap = UltraHdrJpeg.hasGainMap(bytes)
            hdrInfo = HdrAnalysis.of(bytes)
            // J: pomiar zapasu HDR (gain mapa / DNG F16) równolegle z dekodowaniem podglądu, nie przed nim – w I
            // dokładał się szeregowo do open_first_preview i open_full_preview (230 → 302 ms w CI).
            val headroom = CoroutineScope(currentCoroutineContext()).async(Dispatchers.Default) {
                runCatching { measureHeadroom(bytes) }.getOrNull()
            }
            // Podgląd zawsze z obrazu bazowego SDR (piksele identyczne, gain mapa nie jest wyświetlana): przełącznik
            // HDR wpływa tylko na zapis, więc nie wymaga ponownego dekodowania.
            val decodeBytes = UltraHdrJpeg.sdrOnly(bytes)
            val open = { java.io.ByteArrayInputStream(decodeBytes) }
            // Najpierw osadzona miniatura (ms), potem dekodowanie pomniejszone do rozmiaru podglądu (nie pełne MP).
            withContext(Dispatchers.IO) { PhotoLoader.thumbnail(open) }?.let {
                preview = it
                PerfLog.since("open_first_preview", start)
            }
            loadingRaw = preview == null && PhotoLoader.isTiffRaw(open)
            val loaded = try {
                withContext(Dispatchers.IO) {
                    PerfLog.time("photo_decode") { PhotoLoader.open(open, PhotoLoader.PREVIEW_MAX_SIDE) }
                }
            } finally {
                loadingRaw = false
            }
            sourceSize = loaded.sourceWidth to loaded.sourceHeight
            updatePerfContext()
            photo = loaded.bitmap
            before = null
            headroom.await()?.let { hdrInfo = hdrInfo.copy(headroomEv = it) }
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
        hdrOverrides.clear()
        hdrFromPhoto = defaults.hdrFromPhoto
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
        hdrFromPhoto = hdrFor(i)
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
        val start = PerfLog.now()
        commitCurrent()
        val watermark = frame.watermark
        var current = 0
        var reducedCount = 0
        val result = exportBatch(only, {
            current = only[it]
            exportProgress = it + 1
        }) { photoIndex ->
            val uri = uris[photoIndex]
            val edit = edits.editFor(current)
            val photoFrame = seriesFrame.working(edit.frame, watermark)
            val face = fonts(photoFrame.font)
            val job = ExportJob(
                ExportLook(edit.geometry, lutFor(edit.film), edit.intensity, edit.adjustments),
                graph.filmEngine.renderer,
                photoFrame.copy(outward = frameOutward),
                face,
                exportMaxSide,
                exportFormat.copy(hdr = hdrFor(photoIndex))
            ).apply { trace = SaveTrace().also { saveProgress = it.progress } }
            val out = withContext(Dispatchers.Default) {
                job.run(exportSource(context, uri, hdrFor(photoIndex)), null)
            }
            if (out.reduced) reducedCount++
            val jpeg = out.jpeg
            withContext(Dispatchers.IO) { job.trace?.stage("write") { save(jpeg) } }
            job.trace?.finish()
        }
        exportProgress = 0
        failedIndices = result.failedIndices.map { only[it] }
        markSaved()
        PerfLog.since("save_batch_total", start)
        UiMessage.SavedMany(result.saved, result.failed, reducedCount)
    }

    /** Źródło eksportu: przy wyłączonym „HDR ze zdjęcia” sam obraz bazowy SDR (gain mapa i wideo odcięte). */
    private fun exportSource(context: Context, uri: Uri, hdr: Boolean = hdrFromPhoto): () -> InputStream {
        val open = { context.contentResolver.openInputStream(uri) ?: throw IOException("Brak pliku") }
        if (hdr) return open
        val bytes by lazy { UltraHdrJpeg.sdrOnly(open().use { it.readBytes() }) }
        return { java.io.ByteArrayInputStream(bytes) }
    }

    private var reportedFallback: String? = null

    /** Jednorazowy wpis `backend_fallback` z powodem, gdy GPU podglądu zawiodło i liczy CPU. */
    private fun reportFallback() {
        val reason = graph.filmEngine.renderer.fallbackReason ?: return
        if (reason == reportedFallback) return
        reportedFallback = reason
        updatePerfContext()
        PerfLog.log("backend_fallback", 0, "reason=$reason")
    }

    /** Kontekst wpisów „Logów czasów” (tylko gdy włączone – inaczej bez kosztu). */
    private fun updatePerfContext() {
        if (PerfLog.recorder == null) return
        PerfLog.context = PerfContext(
            backend = graph.filmEngine.renderer.activeBackend,
            width = sourceSize.first,
            height = sourceSize.second,
            hdr = hdrFromPhoto
        )
    }

    /**
     * Przełącza „HDR ze zdjęcia” dla bieżącego zdjęcia (chip „HDR”). Bez ponownego dekodowania: podgląd i tak
     * pokazuje obraz bazowy SDR, a etapy podglądu są w pamięci – zmienia się tylko sposób zapisu.
     */
    suspend fun setHdrFromPhoto(on: Boolean) {
        if (on == hdrFromPhoto) return
        val start = PerfLog.now()
        hdrFromPhoto = on
        hdrOverrides[index] = on
        updatePerfContext()
        refreshPreview()
        PerfLog.since("hdr_toggle_reapply", start)
    }

    /** Podgląd: ta sama ścieżka co eksport, na pomniejszonej kopii. */
    suspend fun refreshPreview() {
        val p = photo ?: return
        if (signatureStatus() == SignatureFitStatus.TooLong) return
        refreshAutoForFilm()
        rendering = true
        try {
            try {
                withContext(Dispatchers.Default) { renderPreview(p) }
            } catch (e: OutOfMemoryError) {
                // N: zoom+wyostrzanie przy 2560 potrafi zabić proces na realme; eksport już łapał OOM, podgląd nie.
                CrashLog.record("preview", e)
                if (!previewDetail) throw e
                recoverFromDetailOom()
                try {
                    withContext(Dispatchers.Default) { renderPreview(p) }
                } catch (e2: OutOfMemoryError) {
                    // 1280 też nie wszedł – zostaw ostatni udany podgląd, nie zabijaj procesu
                    CrashLog.record("preview_fallback", e2)
                }
            }
            reportFallback()
            if (openStart != 0L) {
                PerfLog.since("open_full_preview", openStart)
                openStart = 0L
            }
        } finally {
            rendering = false
        }
    }

    /** Wyłącza tryb szczegółowy i czyści pamięć etapów, żeby następny render został na 1280. */
    private fun recoverFromDetailOom() {
        previewDetail = false
        previewRoiActive = false
        zoomViewport = null
        previewCache = null
        stages.clear()
        before = null
    }

    /**
     * Podgląd etapami z pamięcią każdego etapu ([PreviewStages]): kadr, korekty i wynik bez ramki są liczone tylko,
     * gdy zmieniło się to, od czego zależą. Zmiana filmu = sam LUT na gotowych korektach (bez odszumiania, analizy
     * i twarzy), zmiana ramki = samo złożenie ramki. Przy nowym filmie najpierw idzie szybki podgląd w małej
     * rozdzielczości, potem pełny (progresywnie).
     */
    private suspend fun CoroutineScope.renderPreview(p: Bitmap) {
        previewBomb?.invoke()
        val vp = zoomViewport
        // P: z ramką ROI wycina zdjęcie przed FrameRenderer – zoom w oknie ramki.
        // ROI tylko bez ramki; z ramką Compose skaluje całą kompozycję.
        if (shouldRenderRoi(vp)) {
            renderRoiPreview(p, checkNotNull(vp))
            return
        }
        previewRoiActive = false
        val base = previewBase(p)
        renderFullPreview(p, base, applyGeometry = true)
    }

    /** Ładuje w tle LUT-y sąsiednich filmów karuzeli, żeby kolejne dotknięcie nie czekało na dekodowanie. */
    private fun prefetchNeighbours() {
        val ids = graph.filmEngine.availableLuts().map { it.id }
        val i = ids.indexOf(film)
        if (i < 0) return
        listOf(i + 1, i - 1).mapNotNull { ids.getOrNull(it) }.filter { it != IDENTITY_ID }
            .forEach { id ->
                runCatching { graph.filmEngine.load(id)?.let { graph.filmEngine.renderer.warm(it) } }
            }
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
            val result = suggestFor(p, kind)
            if (kind == AutoKind.Auto) autoResult = result else prepResult = result
            autoScene = result.scene
        }
        if (kind == AutoKind.Auto) autoOn = turnOn else prepOn = turnOn
        reapplyAuto()
    }

    /**
     * Suwak siły odwrócenia krzywej telefonu (niezależny od pozostałych suwaków). Bez ręcznych zmian propozycja
     * liczy się od nowa po zdjęciu krzywej z nową siłą (ekspozycja pasuje do tego, co zobaczy film); po ręcznych
     * zmianach zmienia się tylko odwrócenie, a ręczna ekspozycja i reszta zostają.
     */
    suspend fun updatePrepInversion(value: Float) {
        prepInversion = value.coerceIn(0f, 1f)
        if (!prepOn) return
        val p = photo
        if (autoApplied == adjustments && p != null) {
            val asked = prepInversion
            val result = suggestFor(p, AutoKind.Prep)
            if (asked != prepInversion) return // przeciąganie: liczy się tylko najnowsza wartość
            prepResult = result
            reapplyAuto()
        } else {
            adjustments = adjustments.copy(toneInversion = prepInversion)
        }
    }

    /** Po zmianie filmu propozycja liczy się od nowa pod jego szarość (o ile od tej pory nie zmieniano korekt). */
    private suspend fun refreshAutoForFilm() {
        val p = photo?.takeIf { (autoOn || prepOn) && autoFilm != film } ?: return
        if (autoApplied != adjustments) return
        if (autoOn) autoResult = suggestFor(p, AutoKind.Auto)
        if (prepOn) prepResult = suggestFor(p, AutoKind.Prep)
        reapplyAuto()
    }

    private suspend fun suggestFor(p: Bitmap, kind: AutoKind): AutoResult = withContext(Dispatchers.Default) {
        val small = GeometryApplier.apply(previewBase(p), geometry)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val faces = FaceFinder.find(small)
        val cal = calibration()
        val inversion = if (kind == AutoKind.Prep) prepInversion else 0f
        val context = AutoContext(cal.targetGrey, hdrInfo.headroomEv, hdrInfo.baselineEv, inversion, cal.shoulder)
        autoFilm = film
        val start = PerfLog.now()
        val suggestion = AutoEnhance.suggest(px, small.width, small.height, faces, context)
        PerfLog.since("auto_suggest", start)
        if (kind == AutoKind.Prep) {
            suggestion.forFilm(prepInversion)
        } else {
            suggestion
        }
    }

    /** Kalibracja wybranego filmu (rampa szarości przez LUT), liczona raz na film. */
    private fun calibration(): FilmCalibration {
        val lut = lut() ?: return FilmCalibration.NEUTRAL
        return calibrations.getOrPut(film) { FilmCalibration.of(lut) }
    }

    private fun reapplyAuto() {
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

    /**
     * O: podgląd szczegółowy = tylko widoczny wycinek (+halo), max [ROI_MAX_SIDE] px.
     * Unika pełnego 2560 + SemanticSharpen/Lowres.Accumulator (~75 MB) – właściwa naprawa crashu z N.
     */

    /** P: ROI tylko przy zoomie szczegółowym bez ramki (ramka = zoom Compose na kompozycji). */
    private fun shouldRenderRoi(vp: ZoomViewport?): Boolean =
        previewDetail && !frame.enabled && vp != null && vp.scale >= ZoomState.DETAIL_SCALE

    private suspend fun CoroutineScope.renderRoiPreview(p: Bitmap, vp: ZoomViewport) {
        // Najpierw kadr (geometry) na bazie podglądu, potem wycinek widoczny – UI zoomuje już wykadrowany obraz.
        val base = previewBase(p)
        val framed = stages.cropped(base, geometry) { GeometryApplier.apply(base, geometry) }
        ensureActive()
        val rect = ZoomMath.visiblePhotoRect(
            framed.width,
            framed.height,
            vp.viewW,
            vp.viewH,
            vp.scale,
            vp.offsetX,
            vp.offsetY
        )
        val expanded = ZoomMath.expand(rect, ROI_HALO, framed.width, framed.height)
        if (expanded.width < 2 || expanded.height < 2) {
            previewRoiActive = false
            renderFullPreview(p, base, applyGeometry = true)
            return
        }
        val crop = Bitmap.createBitmap(framed, expanded.left, expanded.top, expanded.width, expanded.height)
        val region = if (maxOf(crop.width, crop.height) > ROI_MAX_SIDE) scaleDown(crop, ROI_MAX_SIDE) else crop
        // region jest już po geometry – nie kadrować drugi raz
        renderFullPreview(p, region, applyGeometry = false)
        previewRoiActive = true
    }

    private suspend fun CoroutineScope.renderFullPreview(p: Bitmap, base: Bitmap, applyGeometry: Boolean = true) {
        val small = if (applyGeometry) {
            stages.cropped(base, geometry) { GeometryApplier.apply(base, geometry) }
        } else {
            base
        }
        ensureActive()
        val srgb = stages.srgb(small) { ColorPolicy.toSrgb(small) }
        if (!previewRoiActive) {
            before = before ?: FrameRenderer.render(srgb, FrameOptions(), typeface, colorSpaceSource = srgb)
        }
        val lut = lut()
        val corrected = stages.corrected(srgb, adjustments, lut != null) {
            ExportPipeline.correct(srgb, adjustments, lut != null)
        }
        ensureActive()
        val key = PreviewStages.LookKey(film, intensity, adjustments.finishing())
        val engine = graph.filmEngine.renderer
        if (!stages.hasLook(corrected, key) && lut != null && !previewRoiActive) {
            val quick = ExportPipeline.look(
                scaleDown(corrected, PREVIEW_SIDE_QUICK),
                lut,
                engine,
                intensity,
                adjustments
            )
            ensureActive()
            preview = FrameRenderer.render(quick, frame, signatureTypeface(), colorSpaceSource = srgb)
        }
        val looked = stages.look(corrected, key) {
            ExportPipeline.look(corrected, lut, engine, intensity, adjustments, facesFor(p, small))
        }
        ensureActive()
        preview = FrameRenderer.render(looked, frame, signatureTypeface(), colorSpaceSource = srgb)
        if (!previewRoiActive) prefetchNeighbours()
    }

    /** Zmniejszona kopia zdjęcia do podglądu (filtr Lanczosa jest drogi, więc liczona raz na zdjęcie i rozmiar). */
    private fun previewBase(p: Bitmap): Bitmap {
        val side = if (previewDetail) PREVIEW_SIDE_DETAIL else PREVIEW_SIDE
        val cached = previewCache
        if (cached != null && cached.first === p && cached.second == side) return cached.third
        return scaleDown(p, side).also { previewCache = Triple(p, side, it) }
    }

    /** Eksport; nigdy nie rzuca – błędy wracają jako [UiMessage]. */
    suspend fun export(context: Context? = null, save: suspend (ByteArray) -> Unit): UiMessage = withBusy {
        val start = PerfLog.now()
        val p = photo ?: return@withBusy UiMessage.LoadFailed
        if (signatureStatus() == SignatureFitStatus.TooLong) return@withBusy UiMessage.SignatureTooLong
        try {
            val job = ExportJob(
                ExportLook(geometry, lut(), intensity, adjustments),
                graph.filmEngine.renderer,
                frame.copy(outward = frameOutward),
                signatureTypeface(),
                exportMaxSide,
                exportFormat
            ).apply { trace = SaveTrace().also { saveProgress = it.progress } }
            val uri = uris.getOrNull(index)
            val open: (() -> java.io.InputStream)? = if (context != null &&
                uri != null
            ) {
                exportSource(context, uri)
            } else {
                null
            }
            val out = withContext(Dispatchers.Default) { job.run(open, p) }
            withContext(Dispatchers.IO) { job.trace?.stage("write") { save(out.jpeg) } }
            job.trace?.finish()
            markSaved()
            PerfLog.since("save_total", start)
            if (out.reduced) UiMessage.SavedReduced else UiMessage.Saved
        } catch (_: SignatureTooLongException) {
            UiMessage.SignatureTooLong
        } catch (e: IOException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        } catch (e: IllegalStateException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        } catch (e: SecurityException) {
            UiMessage.ExportFailed(e.message.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // H: brak pamięci (OOM) i każdy inny błąd zapisu → komunikat zamiast zamknięcia aplikacji
            CrashLog.record("export", e)
            UiMessage.ExportFailed(e.javaClass.simpleName)
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
            saveProgress = null
        }
    }

    companion object {
        const val PREVIEW_SIDE = 1280
        const val PREVIEW_SIDE_QUICK = 512
        const val PREVIEW_SIDE_DETAIL = 2560

        /** O: dłuższy bok wycinka ROI; wystarczy do inspekcji detalu bez 75 MB akumulatora. */
        const val ROI_MAX_SIDE = 1280
        const val ROI_HALO = 48

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
