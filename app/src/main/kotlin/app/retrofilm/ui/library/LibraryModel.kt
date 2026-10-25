package app.retrofilm.ui.library

import androidx.compose.runtime.Immutable
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.PackState
import app.retrofilm.ui.FilmOption
import app.retrofilm.ui.IDENTITY_ID

/** Gdzie jest film: na telefonie (APK albo pobrany), w trakcie pobierania, do pobrania albo błąd. */
enum class FilmStatus { OnDevice, Downloading, NotDownloaded, Failed }

@Immutable
data class LibraryItem(
    val id: String,
    val name: String,
    val category: String,
    val bytes: Long,
    val status: FilmStatus,
    val progress: Float = 0f
)

@Immutable
data class LibraryFilter(
    val query: String = "",
    val category: String? = null,
    val onlyFavorites: Boolean = false,
    val onlyOnDevice: Boolean = false
)

/** Czysta logika Biblioteki filmów (bez Androida): łączenie źródeł, filtrowanie, liczniki. */
object LibraryModel {
    fun categoryOf(id: String): String = id.substringBefore('/', missingDelimiterValue = "")

    /**
     * Wszystkie filmy: pakiety do pobrania ([packs], także gotowe) + filmy już na telefonie z [films],
     * których nie ma wśród pakietów (startowe z APK). Najpierw filmy na telefonie, potem alfabetycznie.
     */
    fun items(films: List<FilmOption>, packs: List<LutPack>): List<LibraryItem> {
        val fromPacks = packs.map { p ->
            val (status, progress) = when (val s = p.state) {
                PackState.Ready -> FilmStatus.OnDevice to 0f
                is PackState.Downloading -> FilmStatus.Downloading to s.progress
                is PackState.Failed -> FilmStatus.Failed to 0f
                PackState.NotDownloaded -> FilmStatus.NotDownloaded to 0f
            }
            LibraryItem(p.id, p.displayName, categoryOf(p.id), p.sizeBytes, status, progress)
        }
        val packIds = fromPacks.mapTo(HashSet()) { it.id }
        val onDevice = films
            .filter { it.id != IDENTITY_ID && it.id !in packIds }
            .map { LibraryItem(it.id, it.name ?: it.id, it.category, 0L, FilmStatus.OnDevice) }
        return (fromPacks + onDevice).sortedWith(
            compareBy<LibraryItem> { it.status != FilmStatus.OnDevice }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    fun apply(items: List<LibraryItem>, filter: LibraryFilter, favorites: Set<String>): List<LibraryItem> {
        val query = filter.query.trim()
        return items.filter {
            (filter.category == null || it.category == filter.category) &&
                (!filter.onlyFavorites || it.id in favorites) &&
                (!filter.onlyOnDevice || it.status == FilmStatus.OnDevice) &&
                (query.isEmpty() || it.name.contains(query, ignoreCase = true))
        }
    }

    /** Kategorie z liczbą filmów, w stałej kolejności (alfabetycznie po id kategorii). */
    fun categoryCounts(items: List<LibraryItem>): List<Pair<String, Int>> =
        items.groupingBy { it.category }.eachCount().toList().sortedBy { it.first }

    /** Filmy, które da się teraz pobrać (jeszcze niepobrane albo po błędzie). */
    fun downloadable(items: List<LibraryItem>): List<LibraryItem> =
        items.filter { it.status == FilmStatus.NotDownloaded || it.status == FilmStatus.Failed }
}
