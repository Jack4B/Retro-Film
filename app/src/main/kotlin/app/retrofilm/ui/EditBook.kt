package app.retrofilm.ui

import androidx.compose.runtime.mutableStateMapOf
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.Geometry

/** Edycja jednego zdjęcia: film, jego moc, korekty (z maskami) i kadr. Ramka i podpis są wspólne dla serii. */
data class PhotoEdit(
    val film: String = IDENTITY_ID,
    val intensity: Float = 1f,
    val adjustments: Adjustments = Adjustments.None,
    val geometry: Geometry = Geometry()
)

/**
 * Edycje serii zdjęć. Domyślnie wszystkie zdjęcia mają wspólne ustawienia ([shared]); zdjęcie można przełączyć na
 * edycję osobną ([makeIndividual]) – wtedy ma własny film, korekty i kadr, a zmiany wspólnych go nie dotyczą.
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

    /** Ustawienia [working] stają się wspólne dla wszystkich zdjęć, osobne edycje znikają. */
    fun applyToAll(working: PhotoEdit) {
        own.clear()
        shared = working
    }

    /** Nowa seria: [working] jako ustawienia wspólne, bez edycji osobnych. */
    fun reset(working: PhotoEdit = PhotoEdit()) = applyToAll(working)
}
