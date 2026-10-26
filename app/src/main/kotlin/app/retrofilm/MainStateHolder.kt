package app.retrofilm

import androidx.lifecycle.ViewModel
import app.retrofilm.ui.MainState

/**
 * Trzyma [MainState] poza aktywnością: zmiana konfiguracji (obrót, tryb ciemny, rozmiar czcionki) odtwarza
 * aktywność, ale zdjęcie, seria i wszystkie edycje zostają. Stan nie może trzymać referencji do aktywności.
 */
internal class MainStateHolder(val state: MainState) : ViewModel()
