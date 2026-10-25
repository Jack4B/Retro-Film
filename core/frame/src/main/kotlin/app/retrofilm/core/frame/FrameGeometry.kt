package app.retrofilm.core.frame

/** Proporcje płótna ramki. Domyślnie 1:1. */
@Suppress("MagicNumber") // proporcje są danymi, nie „magicznymi liczbami”
enum class FrameAspect(val label: String, val ratio: Float?) {
    Square("1:1", 1f),
    Portrait45("4:5", 4f / 5f),
    Classic32("3:2", 3f / 2f),
    Original("Oryginał", null)
}

data class RectF(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}

/** Rozmiar w pikselach (szerokość × wysokość). */
data class SizeF(val width: Float, val height: Float)

data class FrameLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val u: Float,
    val photoArea: RectF,
    val photo: RectF,
    val signatureBand: RectF?,
    val signatureBaseSize: Float,
    val signatureMaxWidth: Float
)

/**
 * Geometria ramki wg RAMKA-SPEC v2. Wszystko w ułamkach u = min(W, H).
 * margin = 0..0.15 (domyślnie 0.05).
 */
object FrameGeometry {
    const val DEFAULT_MARGIN = 0.05f
    const val MAX_MARGIN = 0.15f
    const val SIGNATURE_BAND = 0.20f
    const val SIGNATURE_BASE = 0.075f
    const val SIGNATURE_MIN_SCALE = 0.60f
    const val SIGNATURE_WIDTH_FRACTION = 0.90f
    const val SIGNATURE_MAX_CHARS = 40

    fun canvasSize(aspect: FrameAspect, photoW: Float, photoH: Float, longSide: Float): Pair<Float, Float> {
        val ratio = aspect.ratio ?: (photoW / photoH)
        return if (ratio >= 1f) longSide to longSide / ratio else longSide * ratio to longSide
    }

    fun layout(canvas: SizeF, photoSize: SizeF, margin: Float, signature: Boolean): FrameLayout {
        val w = canvas.width
        val h = canvas.height
        val photoW = photoSize.width
        val photoH = photoSize.height
        val u = minOf(w, h)
        val m = margin.coerceIn(0f, MAX_MARGIN) * u
        val band = if (signature) SIGNATURE_BAND * h else null
        val areaH = if (band != null) h - m - band else h - 2 * m
        val area = RectF(m, m, w - 2 * m, areaH.coerceAtLeast(0f))
        // contain: zdjęcie zawsze w całości, wyśrodkowane
        val scale = minOf(area.width / photoW, area.height / photoH)
        val pw = photoW * scale
        val ph = photoH * scale
        val photo = RectF(area.left + (area.width - pw) / 2, area.top + (area.height - ph) / 2, pw, ph)
        return FrameLayout(
            canvasWidth = w,
            canvasHeight = h,
            u = u,
            photoArea = area,
            photo = photo,
            signatureBand = band?.let { RectF(0f, h - it, w, it) },
            signatureBaseSize = SIGNATURE_BASE * u,
            signatureMaxWidth = SIGNATURE_WIDTH_FRACTION * area.width
        )
    }

    /** Krok dodatkowego zmniejszania podpisu w 2 liniach (poniżej 60%), patrz RAMKA-SPEC. */
    const val SIGNATURE_SHRINK_STEP = 0.9f

    /** Dolna granica bezpieczeństwa: 1% rozmiaru bazowego (pętla zawsze się kończy). */
    const val SIGNATURE_FLOOR_SCALE = 0.01f

    /** Wynik dopasowania: rozmiar fontu, liczba linii i wymiary bloku tekstu. */
    data class SignatureFit(val sizePx: Float, val lines: Int, val width: Float, val height: Float)

    /**
     * Dopasowanie podpisu do pasa (RAMKA-SPEC, „Podpis”):
     * 1. rozmiar bazowy w jednej linii;
     * 2. zmniejszanie w jednej linii najwyżej do 60% bazowego;
     * 3. zawinięcie do 2 linii przy 60%;
     * 4. gdy 2 linie nadal nie mieszczą się w [maxWidth] × [maxHeight] (albo łamanie daje > 2 linie),
     *    dalsze zmniejszanie krokami ×0.9 aż do zmieszczenia (granica bezpieczeństwa 1% bazowego).
     */
    fun fitSignature(base: Float, maxWidth: Float, maxHeight: Float, measurer: SignatureMeasurer): SignatureFit {
        fun fits(b: TextBlock, maxLines: Int) = b.lines <= maxLines && b.width <= maxWidth && b.height <= maxHeight
        val full = measurer.measure(base, null)
        val oneLineSize = (base * maxWidth / full.width).coerceIn(base * SIGNATURE_MIN_SCALE, base)
        val oneLine = if (oneLineSize == base) full else measurer.measure(oneLineSize, null)
        return if (fits(oneLine, 1)) {
            SignatureFit(oneLineSize, 1, oneLine.width, oneLine.height)
        } else {
            fitTwoLines(base, maxWidth, measurer, ::fits)
        }
    }

    private fun fitTwoLines(
        base: Float,
        maxWidth: Float,
        measurer: SignatureMeasurer,
        fits: (TextBlock, Int) -> Boolean
    ): SignatureFit {
        val floor = base * SIGNATURE_FLOOR_SCALE
        var size = base * SIGNATURE_MIN_SCALE
        var wrapped = measurer.measure(size, maxWidth)
        while (!fits(wrapped, 2) && size * SIGNATURE_SHRINK_STEP >= floor) {
            size *= SIGNATURE_SHRINK_STEP
            wrapped = measurer.measure(size, maxWidth)
        }
        return SignatureFit(size, wrapped.lines, wrapped.width, wrapped.height)
    }

    /** Prostokąt bloku podpisu: wyśrodkowany poziomo na płótnie i pionowo w pasie; null bez pasa. */
    fun signatureBounds(layout: FrameLayout, width: Float, height: Float): RectF? = layout.signatureBand?.let { band ->
        RectF((layout.canvasWidth - width) / 2f, band.top + (band.height - height) / 2f, width, height)
    }
}

/** Wymiary zmierzonego bloku tekstu. */
data class TextBlock(val width: Float, val height: Float, val lines: Int)

/** Wstrzykiwany pomiar tekstu podpisu (Compose w aplikacji, atrapa w testach). */
fun interface SignatureMeasurer {
    /** [wrapWidth] = null: jedna linia bez łamania; inaczej łamanie do tej szerokości bez limitu linii. */
    fun measure(sizePx: Float, wrapWidth: Float?): TextBlock
}
