package app.retrofilm.core.frame

/** Stan podpisu względem ramki; eksport MUSI zablokować się przy [TooLong]. */
enum class SignatureFitStatus {
    /** Ramka lub podpis wyłączone albo pusty tekst — nic do rysowania. */
    None,
    Fits,
    TooLong
}

/**
 * Sprawdzenie, czy podpis zmieści się w ramce (ten sam algorytm co podgląd ramki).
 * Czysty Kotlin — pomiar tekstu dostarcza [SignatureMeasurer] (np. Compose w feature:editor),
 * dzięki czemu z wyniku korzysta też core:export bez zależności od modułów feature.
 */
object SignatureFitCheck {
    /** Dłuższy bok płótna referencyjnego; układ liczony w ułamkach u, więc wynik nie zależy od skali. */
    const val REFERENCE_LONG_SIDE = 1080f

    @Suppress("LongParameterList")
    fun check(
        frameEnabled: Boolean,
        signatureEnabled: Boolean,
        signature: String,
        aspect: FrameAspect,
        margin: Float,
        photoAspect: Float,
        measurer: SignatureMeasurer
    ): SignatureFitStatus {
        if (!frameEnabled || !signatureEnabled || signature.isBlank()) return SignatureFitStatus.None
        val (w, h) = FrameGeometry.canvasSize(aspect, photoAspect, 1f, REFERENCE_LONG_SIDE)
        val l = FrameGeometry.layout(SizeF(w, h), SizeF(photoAspect, 1f), margin, signature = true)
        val band = requireNotNull(l.signatureBand) { "Pas podpisu istnieje przy signature = true" }
        val fit = FrameGeometry.fitSignature(l.signatureBaseSize, l.signatureMaxWidth, band.height, measurer)
        return if (fit.fits) SignatureFitStatus.Fits else SignatureFitStatus.TooLong
    }
}
