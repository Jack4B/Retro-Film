package app.retrofilm.feature.editor.frame

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import app.retrofilm.core.frame.SignatureFitCheck
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.feature.editor.R

/** Komunikat pokazywany, gdy podpis nie mieści się nawet przy progu czytelności. */
@StringRes
val SIGNATURE_TOO_LONG_MESSAGE = R.string.frame_signature_too_long

/** Opis dla TalkBack (bez myślnika, czytany płynnie). */
@StringRes
val SIGNATURE_TOO_LONG_A11Y = R.string.frame_signature_too_long_a11y

/**
 * Sprawdza, czy [signature] zmieści się w ramce [settings] dla zdjęcia o proporcjach [photoAspect].
 * Deleguje do [SignatureFitCheck] z core:frame (z niego korzysta też eksport).
 */
fun checkSignatureFit(
    measurer: TextMeasurer,
    pxPerSp: Float,
    settings: FrameSettings,
    signature: String,
    photoAspect: Float
): SignatureFitStatus = SignatureFitCheck.check(
    frameEnabled = settings.enabled,
    signatureEnabled = settings.signatureEnabled,
    signature = signature,
    aspect = settings.aspect,
    margin = settings.margin,
    photoAspect = photoAspect,
    measurer = ComposeSignatureMeasurer(measurer, signature, signatureStyle(Color.Black, pxPerSp))
)

@Composable
fun rememberSignatureFitStatus(settings: FrameSettings, signature: String, photoAspect: Float): SignatureFitStatus {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    val pxPerSp = d.density * d.fontScale
    return remember(settings, signature, photoAspect, pxPerSp) {
        checkSignatureFit(measurer, pxPerSp, settings, signature, photoAspect)
    }
}
