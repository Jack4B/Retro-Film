package app.retrofilm.feature.editor

import app.retrofilm.core.ui.engine.FilmEngine
import app.retrofilm.core.ui.preview.PreviewFilmEngine
import app.retrofilm.feature.editor.frame.SeriesPhoto

/** Przykładowe dane tylko dla testów zrzutów. */
fun sampleEditorState(engine: FilmEngine = PreviewFilmEngine) =
    EditorUiState(luts = engine.availableLuts(), selectedLutId = "portra400", canUndo = true)

/** Seria 3 zdjęć, drugie ma własny podpis. */
val SampleSeries = listOf(
    SeriesPhoto("a", 0, 1.5f),
    SeriesPhoto("b", 1, 2f / 3f, signatureOverride = "Jezioro Bachotek"),
    SeriesPhoto("c", 2, 1.5f)
)
