package app.retrofilm.ui

import androidx.compose.runtime.mutableStateMapOf
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.Geometry

/**
 * Edycja jednego zdjęcia: film, jego moc, korekty (z maskami), kadr oraz ramka z podpisem. Znak wodny nie wchodzi do
 * edycji zdjęcia: to ustawienie całej aplikacji, wspólne dla każdego zdjęcia ([MainState.snapshot] zeruje go tutaj).
 */
data class PhotoEdit(
    val film: String = IDENTITY_ID,
    val intensity: Float = 1f,
    val adjustments: Adjustments = Adjustments.None,
    val geometry: Geometry = Geometry(),
    val frame: FrameOptions = FrameOptions()
)

/**
 * Edycje serii zdjęć. Domyślnie wszystkie zdjęcia mają wspólne ustawienia ([shared]); zdjęcie można przełączyć na
 * edycję osobną ([makeIndividual]) – wtedy ma własny film, korekty, kadr i ramkę, a zmiany wspólnych go nie dotyczą.
 * Ustawienia „robocze” bieżącego zdjęcia żyją w stanie edytora, a tutaj trafiają przez [commit] przy zmianie zdjęcia
 * i przed zapisem. Stan osobnych edycji jest obserwowalny (znacznik w pasku zdjęć).
 */
class EditBook {
    private var shared = PhotoEdit()
    private val own = mutableStateMapOf<Int, PhotoEdit>()

    fun isIndividual(index: Int): Boolean = index in own

    val individualCount: Int get() = own.size

    /**
     * Zapisuje ustawienia robocze zdjęcia [index]: do jego edycji osobnej albo do wspólnych. W serii ([series]) każda
     * zmiana na zdjęciu bez edycji osobnej przypisuje się tylko do niego (staje się jego edycją osobną), a wspólne
     * zostają bez zmian, więc zdjęcia edytuje się z osobna bez przełączania zakresu.
     */
    fun commit(index: Int, working: PhotoEdit, series: Boolean = false) {
        when {
            index in own -> own[index] = working
            series && working != shared -> own[index] = working
            else -> shared = working
        }
    }

    /**
     * Edycje wszystkich [count] zdjęć w takiej postaci, jaką dałby [commit] ustawień roboczych [working] zdjęcia
     * [index] (stan książki się nie zmienia). Służy do sprawdzenia, czy coś w serii różni się od ostatniego zapisu.
     */
    fun effective(count: Int, index: Int, working: PhotoEdit, series: Boolean): List<PhotoEdit> {
        val assigned = index !in own && series && working != shared
        val base = if (index in own || assigned) shared else working
        return List(count) { i -> if (i == index) working else own[i] ?: base }
    }

    /** Edycja, którą ma zdjęcie [index] (osobna, a jeśli jej nie ma – wspólna). */
    fun editFor(index: Int): PhotoEdit = own[index] ?: shared

    /** Zdjęcie [index] dostaje osobną edycję, zaczynając od ustawień roboczych [working]; wspólne się nie zmieniają. */
    fun makeIndividual(index: Int, working: PhotoEdit) {
        if (index !in own) shared = working
        own[index] = working
    }

    /** Zdjęcie wraca do ustawień wspólnych (osobna edycja jest porzucana); zwraca ustawienia do wczytania. */
    fun makeShared(index: Int): PhotoEdit {
        own.remove(index)
        return shared
    }

    /** Przekształca ramkę w edycji wspólnej i w każdej edycji osobnej (zdjęcia bez edycji osobnej nie dostają jej). */
    fun mapFrames(transform: (FrameOptions) -> FrameOptions) {
        shared = shared.copy(frame = transform(shared.frame))
        for (index in own.keys.toList()) {
            val edit = own.getValue(index)
            own[index] = edit.copy(frame = transform(edit.frame))
        }
    }

    /** Ustawienia [working] stają się wspólne dla wszystkich zdjęć, osobne edycje znikają. */
    fun applyToAll(working: PhotoEdit) {
        own.clear()
        shared = working
    }

    /** Nowa seria: [working] jako ustawienia wspólne, bez edycji osobnych. */
    fun reset(working: PhotoEdit = PhotoEdit()) = applyToAll(working)
}
