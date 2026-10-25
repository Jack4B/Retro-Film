package app.retrofilm.feature.editor.frame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SizeF

/** Komunikat pokazywany, gdy podpis nie mieści się nawet przy progu czytelności. */
const val SIGNATURE_TOO_LONG_MESSAGE = "Podpis za długi dla tej ramki – skróć go lub zmniejsz grubość ramki"

/** Opis dla TalkBack (bez myślnika, czytany płynnie). */
const val SIGNATURE_TOO_LONG_A11Y =
    "Błąd: podpis jest za długi dla tej ramki. Skróć podpis lub zmniejsz grubość ramki. " +
        "Podpis nie zostanie wyeksportowany."

/** Stan podpisu względem ramki; eksport MUSI zablokować/ostrzec przy [TooLong]. */
enum class SignatureFitStatus {
    /** Ramka lub podpis wyłączone albo pusty tekst — nic do rysowania. */
    None,
    Fits,
    TooLong
}

/** Dłuższy bok płótna referencyjnego; układ liczony jest w ułamkach u, więc wynik nie zależy od skali. */
private const val REFERENCE_LONG_SIDE = 1080f

/**
 * Sprawdza, czy [signature] zmieści się w ramce [settings] dla zdjęcia o proporcjach [photoAspect].
 * Ten sam algorytm co [FramePreview]; do użycia przez ekran Ramka i przez eksport.
 */
fun checkSignatureFit(
    measurer: TextMeasurer,
    pxPerSp: Float,
    settings: FrameSettings,
    signature: String,
    photoAspect: Float
): SignatureFitStatus {
    if (!settings.enabled || !settings.signatureEnabled || signature.isBlank()) return SignatureFitStatus.None
    val (w, h) = FrameGeometry.canvasSize(settings.aspect, photoAspect, 1f, REFERENCE_LONG_SIDE)
    val l = FrameGeometry.layout(SizeF(w, h), SizeF(photoAspect, 1f), settings.margin, signature = true)
    val band = requireNotNull(l.signatureBand) { "Pas podpisu istnieje przy signature = true" }
    val fit = FrameGeometry.fitSignature(
        l.signatureBaseSize,
        l.signatureMaxWidth,
        band.height,
        ComposeSignatureMeasurer(measurer, signature, signatureStyle(Color.Black, pxPerSp))
    )
    return if (fit.fits) SignatureFitStatus.Fits else SignatureFitStatus.TooLong
}

@Composable
fun rememberSignatureFitStatus(settings: FrameSettings, signature: String, photoAspect: Float): SignatureFitStatus {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    val pxPerSp = d.density * d.fontScale
    return remember(settings, signature, photoAspect, pxPerSp) {
        checkSignatureFit(measurer, pxPerSp, settings, signature, photoAspect)
    }
}
