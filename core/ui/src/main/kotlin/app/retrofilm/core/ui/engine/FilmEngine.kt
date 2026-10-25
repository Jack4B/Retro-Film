package app.retrofilm.core.ui.engine

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ColorMatrix

/** Klisza (LUT) widziana przez UI. */
@Immutable
data class FilmLut(val id: String, val name: String)

/**
 * Minimalny kontrakt silnika podglądu po stronie UI.
 *
 * `core:engine` (PR #5) nie wystawia jeszcze interfejsu na `main`, więc ekrany zależą
 * wyłącznie od tego kontraktu. Prawdziwa implementacja (adapter nad `core:engine`)
 * zostanie podpięta w `app`. Wersja testowa/podglądowa: `PreviewFilmEngine` w `src/debug`.
 */
interface FilmEngine {
    fun availableLuts(): List<FilmLut>

    /** Przybliżenie LUT jako macierz koloru dla podglądu (null = oryginał). */
    fun previewMatrix(lut: FilmLut?, intensity: Float): ColorMatrix?
}
