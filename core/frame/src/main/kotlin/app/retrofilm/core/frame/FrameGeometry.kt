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

    data class SignatureFit(val sizePx: Float, val lines: Int)

    /**
     * Dopasowanie podpisu: od 0.075·u zmniejsza do 60%, potem 2 linie przy 60%.
     * [widthAt] zwraca szerokość tekstu w jednej linii dla danego rozmiaru.
     */
    fun fitSignature(base: Float, maxWidth: Float, widthAt: (Float) -> Float): SignatureFit {
        val full = widthAt(base)
        if (full <= maxWidth) return SignatureFit(base, 1)
        val needed = base * maxWidth / full
        val min = base * SIGNATURE_MIN_SCALE
        return if (needed >= min) SignatureFit(needed, 1) else SignatureFit(min, 2)
    }
}
