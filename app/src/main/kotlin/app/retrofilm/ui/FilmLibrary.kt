package app.retrofilm.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.core.ui.lut.PackState
import app.retrofilm.engine.AppGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Biblioteka filmów w Edytorze: filmy startowe z APK i pobrane (D8), oraz ulubione (DataStore).
 * Nic tu nie dotyka WorkManagera, dopóki UI nie wywoła [observeDownloads].
 */
class FilmLibrary(private val graph: AppGraph) {
    private var refreshed by mutableStateOf<List<FilmOption>?>(null)
    private val initial: List<FilmOption> by lazy { build() }

    /** Filmy startowe i pobrane; lista odświeża się po pobraniu filmu ([observeDownloads]). */
    val films: List<FilmOption> get() = refreshed ?: initial

    /** Ulubione filmy (id z katalogu); odczyt przez [observeFavorites]. */
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun build(): List<FilmOption> = listOf(FilmOption(IDENTITY_ID, null)) +
        graph.filmEngine.availableLuts().filter { it.id != IDENTITY_ID }.map { FilmOption(it.id, it.name) }

    /** Odświeża [films], gdy zmieni się liczba pobranych filmów (nie przy każdym procencie postępu). */
    suspend fun observeDownloads() {
        graph.lutPackRepository.packs
            .map { packs -> packs.count { it.state == PackState.Ready } }
            .distinctUntilChanged()
            .collect { refreshed = build() }
    }

    suspend fun observeFavorites() {
        graph.appSettings.favoriteFilms.collect { favorites = it }
    }

    suspend fun toggleFavorite(id: String) {
        graph.appSettings.setFavorite(id, id !in favorites)
    }
}
