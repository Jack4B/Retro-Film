package app.retrofilm.core.engine.pack

/**
 * Flaga funkcji: pobieranie filtrów (D8). Włączone na polecenie Jack4B: pobrane filmy są widoczne
 * w silniku i w Edytorze (`EngineFilmEngine` + `DownloadedFilms`). Wyłączenie = brak wpisów do pobrania
 * i brak zadań sieciowych.
 */
object LutDownloadFeature {
    const val ENABLED = true
}
