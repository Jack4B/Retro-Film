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

    /** Domyślna wielkość podpisu (ułamek bazowej 0,075·u); suwak 100% = ta wartość, pas dołu rośnie proporcjonalnie. */
    const val DEFAULT_SIGNATURE_SCALE = 0.8f
    private const val FIXED_POINT_STEPS = 12

    /** Najwięcej linii podpisu; przy dłuższym tekście rośnie dół ramki, a nie maleje napis. */
    const val MAX_SIGNATURE_LINES = 3
    private const val SHRINK_STEP = 0.9f

    /** Rozmiar płótna: stałe proporcje wg [aspect], a „Oryginał” = zdjęcie + marginesy, skalowane do [longSide]. */
    @Suppress("LongParameterList")
    fun canvasSize(
        aspect: FrameAspect,
        photoW: Float,
        photoH: Float,
        margin: Float,
        signature: Boolean,
        longSide: Float,
        signatureScale: Float = DEFAULT_SIGNATURE_SCALE,
        signatureLines: Int = 1
    ): Pair<Float, Float> {
        if (aspect != FrameAspect.Original) return FrameGeometry.canvasSize(aspect, photoW, photoH, longSide)
        val k = margin.coerceIn(0f, FrameGeometry.MAX_MARGIN)
        val extra = if (signature) extraFor(signatureScale, signatureLines) else 0f
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

    /** Dodatkowy dół (ułamek u) rośnie z wielkością podpisu i liczbą jego linii, żeby podpis miał miejsce. */
    private fun extraFor(signatureScale: Float, lines: Int): Float =
        SIGNATURE_EXTRA * signatureScale / DEFAULT_SIGNATURE_SCALE * lines.coerceIn(1, MAX_SIGNATURE_LINES)

    /**
     * Ile linii zajmie podpis w wybranej wielkości. Wielkość wybrana suwakiem jest święta: za długi tekst
     * jest zawijany (dół ramki rośnie), a nie zmniejszany, więc suwak zawsze zmienia wielkość napisu.
     */
    @Suppress("LongParameterList")
    fun signatureLines(
        aspect: FrameAspect,
        photoW: Float,
        photoH: Float,
        margin: Float,
        signatureScale: Float,
        measurer: SignatureMeasurer
    ): Int {
        var lines = 1
        repeat(MAX_SIGNATURE_LINES) {
            val (w, h) = canvasSize(
                aspect,
                photoW,
                photoH,
                margin,
                true,
                SignatureFitCheck.REFERENCE_LONG_SIDE,
                signatureScale,
                lines
            )
            val l = layout(SizeF(w, h), SizeF(photoW, photoH), margin, true, signatureScale, lines)
            val needed = measurer.measure(l.signatureBaseSize * signatureScale, l.signatureMaxWidth).lines
                .coerceIn(1, MAX_SIGNATURE_LINES)
            if (needed <= lines) return lines
            lines = needed
        }
        return lines
    }

    /**
     * Dopasowanie podpisu: rozmiar [base] (z suwaka) jest zachowany, o ile blok (zawinięty do [maxWidth])
     * mieści się w pasie; dopiero gdy nie, rozmiar maleje krokami ×0,9 do progu czytelności.
     */
    fun fitSignature(
        base: Float,
        maxWidth: Float,
        maxHeight: Float,
        measurer: SignatureMeasurer
    ): FrameGeometry.SignatureFit {
        fun fits(b: TextBlock) = b.lines <= MAX_SIGNATURE_LINES && b.width <= maxWidth && b.height <= maxHeight
        val floor = base * FrameGeometry.SIGNATURE_FLOOR_SCALE
        var size = base
        var block = measurer.measure(size, maxWidth)
        while (!fits(block) && size > floor) {
            size = maxOf(size * SHRINK_STEP, floor)
            block = measurer.measure(size, maxWidth)
        }
        return FrameGeometry.SignatureFit(size, block.lines, block.width, block.height, fits(block))
    }

    @Suppress("LongParameterList")
    fun layout(
        canvas: SizeF,
        photoSize: SizeF,
        margin: Float,
        signature: Boolean,
        signatureScale: Float = DEFAULT_SIGNATURE_SCALE,
        signatureLines: Int = 1
    ): FrameLayout {
        val w = canvas.width
        val h = canvas.height
        val u = minOf(w, h)
        val m = margin.coerceIn(0f, FrameGeometry.MAX_MARGIN) * u
        val extra = if (signature) extraFor(signatureScale, signatureLines) * u else 0f
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
        measurer: SignatureMeasurer,
        signatureScale: Float = DEFAULT_SIGNATURE_SCALE
    ): SignatureFitStatus {
        if (!frameEnabled || !signatureEnabled || signature.isBlank()) return SignatureFitStatus.None
        val lines = signatureLines(aspect, photoAspect, 1f, margin, signatureScale, measurer)
        val (w, h) = canvasSize(
            aspect,
            photoAspect,
            1f,
            margin,
            true,
            SignatureFitCheck.REFERENCE_LONG_SIDE,
            signatureScale,
            lines
        )
        val l = layout(
            SizeF(w, h),
            SizeF(photoAspect, 1f),
            margin,
            signature = true,
            signatureScale = signatureScale,
            signatureLines = lines
        )
        val band = requireNotNull(l.signatureBand) { "Pas podpisu istnieje przy signature = true" }
        val fit = fitSignature(l.signatureBaseSize * signatureScale, l.signatureMaxWidth, band.height, measurer)
        return if (fit.fits) SignatureFitStatus.Fits else SignatureFitStatus.TooLong
    }
}
