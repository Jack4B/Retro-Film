package app.retrofilm.core.engine.pack

/**
 * Flaga funkcji: pobieranie filtrów (D8) jest **wyłączone**, dopóki silnik i ekran ustawień
 * nie są podpięte do prawdziwego repozytorium. Gdy wyłączone: brak wpisów do pobrania i brak zadań sieciowych.
 */
object LutDownloadFeature {
    const val ENABLED = false
}
