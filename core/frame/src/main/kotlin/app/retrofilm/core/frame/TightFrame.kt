package app.retrofilm.core.frame

import kotlin.math.max

/**
 * Układ ramki używany przez aplikację: cienka równa ramka z góry i po bokach, a z podpisem tylko
 * dół jest szerszy o [SIGNATURE_EXTRA]·u. Przy proporcjach „Oryginał” płótno jest dobierane do zdjęcia
 * (zdjęcie + marginesy), więc nie ma dodatkowych pustych pól po bokach.
 * Starszy [FrameGeometry.layout] zostaje dla podglądu w feature:editor.
 */
object TightFrame {
    /** Dodatkowa wysokość dołu pod podpis (ułamek u), ponad zwykły margines. */
    const val SIGNATURE_EXTRA = 0.07f
    private const val FIXED_POINT_STEPS = 12

    /** Rozmiar płótna: stałe proporcje wg [aspect], a „Oryginał” = zdjęcie + marginesy, skalowane do [longSide]. */
    @Suppress("LongParameterList")
    fun canvasSize(
        aspect: FrameAspect,
        photoW: Float,
        photoH: Float,
        margin: Float,
        signature: Boolean,
        longSide: Float
    ): Pair<Float, Float> {
        if (aspect != FrameAspect.Original) return FrameGeometry.canvasSize(aspect, photoW, photoH, longSide)
        val k = margin.coerceIn(0f, FrameGeometry.MAX_MARGIN)
        val extra = if (signature) SIGNATURE_EXTRA else 0f
        var u = minOf(photoW, photoH)
        var cw = photoW
        var ch = photoH
        repeat(FIXED_POINT_STEPS) {
            cw = photoW + 2 * k * u
            ch = photoH + 2 * k * u + extra * u
            u = minOf(cw, ch)
        }
        val scale = longSide / max(cw, ch)
        return cw * scale to ch * scale
    }

    fun layout(canvas: SizeF, photoSize: SizeF, margin: Float, signature: Boolean): FrameLayout {
        val w = canvas.width
        val h = canvas.height
        val u = minOf(w, h)
        val m = margin.coerceIn(0f, FrameGeometry.MAX_MARGIN) * u
        val extra = if (signature) SIGNATURE_EXTRA * u else 0f
        val area = RectF(m, m, w - 2 * m, (h - 2 * m - extra).coerceAtLeast(0f))
        val scale = minOf(area.width / photoSize.width, area.height / photoSize.height)
        val pw = photoSize.width * scale
        val ph = photoSize.height * scale
        val photo = RectF(area.left + (area.width - pw) / 2, area.top + (area.height - ph) / 2, pw, ph)
        val bandTop = area.top + area.height
        return FrameLayout(
            canvasWidth = w,
            canvasHeight = h,
            u = u,
            photoArea = area,
            photo = photo,
            signatureBand = if (signature) RectF(0f, bandTop, w, h - bandTop) else null,
            signatureBaseSize = FrameGeometry.SIGNATURE_BASE * u,
            signatureMaxWidth = FrameGeometry.SIGNATURE_WIDTH_FRACTION * area.width
        )
    }

    /** To samo co [SignatureFitCheck.check], ale na układzie [TightFrame]. */
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
        val (w, h) = canvasSize(aspect, photoAspect, 1f, margin, true, SignatureFitCheck.REFERENCE_LONG_SIDE)
        val l = layout(SizeF(w, h), SizeF(photoAspect, 1f), margin, signature = true)
        val band = requireNotNull(l.signatureBand) { "Pas podpisu istnieje przy signature = true" }
        val fit = FrameGeometry.fitSignature(l.signatureBaseSize, l.signatureMaxWidth, band.height, measurer)
        return if (fit.fits) SignatureFitStatus.Fits else SignatureFitStatus.TooLong
    }
}
