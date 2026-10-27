package app.retrofilm.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.pipeline.FrameOptions

/**
 * Ramka w serii zdjęć: domyślnie osobna dla każdego zdjęcia (wchodzi do [PhotoEdit]); po włączeniu [forAll] wygląd
 * ramki (włączenie, proporcje, margines, czcionka, kolor i wielkość podpisu) jest jeden dla wszystkich zdjęć, a osobny
 * zostaje tylko tekst podpisu. Znak wodny jest zawsze wspólny dla aplikacji, więc tu go nie ma.
 */
class SeriesFrame {
    /** Czy wygląd ramki jest wspólny dla wszystkich zdjęć serii. */
    var forAll by mutableStateOf(false)

    /** Wspólny wygląd ramki (bez tekstu podpisu i znaku wodnego); używany, gdy [forAll]. */
    var style = FrameOptions()

    /** Wygląd ramki bez tekstu podpisu i znaku wodnego. */
    fun styleOf(frame: FrameOptions): FrameOptions = frame.copy(signature = "", watermark = false)

    /** Ramka zapisywana w edycji zdjęcia: przy wspólnym wyglądzie tylko tekst podpisu, inaczej cała ramka. */
    fun stored(frame: FrameOptions): FrameOptions =
        if (forAll) FrameOptions(signature = frame.signature) else frame.copy(watermark = false)

    /** Ramka zdjęcia do podglądu i zapisu z edycji [stored] i wspólnego [watermark]. */
    fun working(stored: FrameOptions, watermark: Boolean): FrameOptions =
        (if (forAll) style.copy(signature = stored.signature) else stored).copy(watermark = watermark)

    /** Zmiana ramki bieżącego zdjęcia: przy wspólnym wyglądzie staje się wyglądem wszystkich zdjęć. */
    fun onEdit(frame: FrameOptions) {
        if (forAll) style = styleOf(frame)
    }
}
