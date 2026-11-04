package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF as AndroidRectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.retrofilm.core.export.SignatureTooLongException
import app.retrofilm.core.frame.FrameAspect
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.core.frame.SignatureMeasurer
import app.retrofilm.core.frame.SizeF
import app.retrofilm.core.frame.TextBlock
import app.retrofilm.core.frame.TightFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Ustawienia ramki i podpisu dla eksportu. */
data class FrameOptions(
    val enabled: Boolean = false,
    val aspect: FrameAspect = FrameAspect.Original,
    val margin: Float = FrameGeometry.DEFAULT_MARGIN,
    val signatureEnabled: Boolean = false,
    val signature: String = "",
    val font: SignatureFont = SignatureFont.Caveat,
    /** Kolor atramentu (ARGB). */
    val inkColor: Int = PaintSignatureMeasurer.SIGNATURE_COLOR,
    /** Wielkość podpisu jako ułamek bazowej; domyślnie 0,8 (suwak 100%), do 1,0 (suwak 125%). */
    val signatureScale: Float = TightFrame.DEFAULT_SIGNATURE_SCALE,
    /** Znak wodny: kod QR z linkiem do aplikacji w rogu zdjęcia. */
    val watermark: Boolean = false,
    /**
     * K: tryb ramki (Ustawienia). true = „Ramka na zewnątrz” (J): zdjęcie 1:1, plik większy o margines.
     * false = „Oryginalny rozmiar pliku”: zdjęcie zmniejszone do okna ramki, plik ma wymiary oryginału.
     */
    val outward: Boolean = true,
    /**
     * Dłuższy bok płótna w trybie „Oryginalny rozmiar pliku” (0 = dłuższy bok zdjęcia). Zapis ustawia go, gdy
     * zdjęcie zostało już zmniejszone do okna ([FrameRenderer.originalWindow]) przed potokiem – wtedy kopia 1:1.
     */
    val fitLongSide: Int = 0
) {
    val drawsSignature get() = enabled && signatureEnabled && signature.isNotBlank()
}

/** Pomiar podpisu przez [StaticLayout] – te same parametry co przy rysowaniu w [FrameRenderer]. */
class PaintSignatureMeasurer(
    private val text: String,
    private val typeface: Typeface,
    private val inkColor: Int = SIGNATURE_COLOR
) : SignatureMeasurer {
    fun paint(sizePx: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@PaintSignatureMeasurer.typeface
        textSize = sizePx
        color = inkColor
    }

    fun layout(sizePx: Float, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(sizePx), ceil(width).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

    override fun measure(sizePx: Float, wrapWidth: Float?): TextBlock {
        if (wrapWidth == null) {
            // Bez łamania: każda jawna linia (\n) liczy się osobno.
            val p = paint(sizePx)
            val fm = p.fontMetrics
            val lines = text.split('\n')
            return TextBlock(lines.maxOf { p.measureText(it) }, (fm.descent - fm.ascent) * lines.size, lines.size)
        }
        val l = layout(sizePx, wrapWidth)
        val width = (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f
        return TextBlock(width, l.height.toFloat(), l.lineCount)
    }

    companion object {
        val SIGNATURE_COLOR = Color.rgb(0x2B, 0x2B, 0x2B)
    }
}

/** Składa ramkę (RAMKA-SPEC v2, [FrameGeometry]) i podpis na bitmapie wyjściowej. */
@Suppress("TooManyFunctions")
object FrameRenderer {
    /** Status podpisu – ten sam algorytm co podgląd i blokada w `JpegExporter`. */
    fun status(options: FrameOptions, photoW: Int, photoH: Int, typeface: Typeface): SignatureFitStatus =
        TightFrame.check(
            frameEnabled = options.enabled,
            signatureEnabled = options.signatureEnabled,
            signature = options.signature,
            aspect = options.aspect,
            margin = options.margin,
            photoAspect = photoW.toFloat() / photoH,
            measurer = PaintSignatureMeasurer(options.signature, typeface),
            signatureScale = options.signatureScale
        )

    /**
     * Wynikowa bitmapa w przestrzeni barw [colorSpaceSource] (np. Display P3 oryginału), więc
     * `Bitmap.compress` zapisze odpowiedni profil ICC. Bez ramki – samo zdjęcie.
     */
    fun render(photo: Bitmap, options: FrameOptions, typeface: Typeface, colorSpaceSource: Bitmap = photo): Bitmap {
        if (!options.enabled) return plain(photo, options, colorSpaceSource)
        val lines = linesFor(options, photo.width, photo.height, typeface)
        val layout = if (options.outward) {
            outwardLayout(photo, options, lines)
        } else {
            val side = options.fitLongSide.takeIf { it > 0 } ?: maxOf(photo.width, photo.height)
            layoutFor(photo, options, lines, side.toFloat())
        }
        val w = layout.canvasWidth.roundToInt().coerceAtLeast(1)
        val h = layout.canvasHeight.roundToInt().coerceAtLeast(1)
        val out = blank(w, h, colorSpaceSource)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val p = layout.photo
        drawPhoto(canvas, photo, AndroidRectF(p.left, p.top, p.right, p.bottom))
        if (options.drawsSignature) drawSignature(canvas, layout, options, typeface)
        if (options.watermark) Watermark.draw(canvas, AndroidRectF(p.left, p.top, p.right, p.bottom))
        return out
    }

    /**
     * J: płótno dobrane tak, by okno zdjęcia miało (po zaokrągleniu) jego rozmiar – zdjęcie kopiowane 1:1, ramka
     * rośnie na zewnątrz. Układ jest liniowy w długości boku: dwa przybliżenia.
     */
    private fun outwardLayout(photo: Bitmap, options: FrameOptions, lines: Int): app.retrofilm.core.frame.FrameLayout {
        val first = layoutFor(photo, options, lines, maxOf(photo.width, photo.height).toFloat())
        val grow = photo.width / first.photo.width.coerceAtLeast(1f)
        val longSide = maxOf(first.canvasWidth, first.canvasHeight) * grow.coerceIn(1f, MAX_GROW)
        return layoutFor(photo, options, lines, longSide)
    }

    /**
     * K: rozmiar okna zdjęcia w trybie „Oryginalny rozmiar pliku” dla zdjęcia [w]×[h] – zapis zmniejsza zdjęcie do
     * niego jeszcze przed potokiem (mniej pikseli do obróbki, ziarno i dithering bez rozmycia), a ramka kopiuje 1:1.
     * null = ramka wyłączona albo tryb „na zewnątrz”.
     */
    fun originalWindow(options: FrameOptions, w: Int, h: Int, typeface: Typeface): Pair<Int, Int>? {
        if (!options.enabled || options.outward) return null
        val lines = linesFor(options, w, h, typeface)
        val (cw, ch) = TightFrame.canvasSize(
            options.aspect,
            w.toFloat(),
            h.toFloat(),
            options.margin,
            options.drawsSignature,
            maxOf(w, h).toFloat(),
            options.signatureScale,
            lines
        )
        val layout = TightFrame.layout(
            SizeF(cw.roundToInt().coerceAtLeast(1).toFloat(), ch.roundToInt().coerceAtLeast(1).toFloat()),
            SizeF(w.toFloat(), h.toFloat()),
            options.margin,
            options.drawsSignature,
            options.signatureScale,
            lines
        )
        return layout.photo.width.roundToInt().coerceAtLeast(1) to layout.photo.height.roundToInt().coerceAtLeast(1)
    }

    private fun linesFor(options: FrameOptions, w: Int, h: Int, typeface: Typeface): Int = if (options.drawsSignature) {
        TightFrame.signatureLines(
            options.aspect,
            w.toFloat(),
            h.toFloat(),
            options.margin,
            options.signatureScale,
            PaintSignatureMeasurer(options.signature, typeface)
        )
    } else {
        1
    }

    private fun layoutFor(
        photo: Bitmap,
        options: FrameOptions,
        lines: Int,
        longSide: Float
    ): app.retrofilm.core.frame.FrameLayout {
        val (cw, ch) = TightFrame.canvasSize(
            options.aspect,
            photo.width.toFloat(),
            photo.height.toFloat(),
            options.margin,
            options.drawsSignature,
            longSide,
            options.signatureScale,
            lines
        )
        val w = cw.roundToInt().coerceAtLeast(1)
        val h = ch.roundToInt().coerceAtLeast(1)
        return TightFrame.layout(
            SizeF(w.toFloat(), h.toFloat()),
            SizeF(photo.width.toFloat(), photo.height.toFloat()),
            options.margin,
            options.drawsSignature,
            options.signatureScale,
            lines
        )
    }

    private fun drawSignature(
        canvas: Canvas,
        layout: app.retrofilm.core.frame.FrameLayout,
        options: FrameOptions,
        typeface: Typeface
    ) {
        val band = layout.signatureBand ?: return
        val measurer = PaintSignatureMeasurer(options.signature, typeface, options.inkColor)
        val baseSize = layout.signatureBaseSize * options.signatureScale
        val fit = TightFrame.fitSignature(baseSize, layout.signatureMaxWidth, band.height, measurer)
        if (!fit.fits) throw SignatureTooLongException()
        val bounds = FrameGeometry.signatureBounds(layout, fit.width, fit.height) ?: return
        val static = measurer.layout(fit.sizePx, layout.signatureMaxWidth)
        canvas.save()
        canvas.translate((layout.canvasWidth - layout.signatureMaxWidth) / 2f, bounds.top)
        static.draw(canvas)
        canvas.restore()
    }

    /** Bez ramki: samo zdjęcie, ewentualnie ze znakiem wodnym (na kopii, oryginał zostaje nietknięty). */
    private fun plain(photo: Bitmap, options: FrameOptions, colorSpaceSource: Bitmap): Bitmap {
        val base = copyInto(photo, photo.width, photo.height, colorSpaceSource)
        if (!options.watermark) return base
        val out = if (base === photo || !base.isMutable) base.copy(Bitmap.Config.ARGB_8888, true) else base
        Watermark.draw(Canvas(out), AndroidRectF(0f, 0f, out.width.toFloat(), out.height.toFloat()))
        return out
    }

    private fun blank(w: Int, h: Int, csSource: Bitmap): Bitmap {
        val cs = csSource.colorSpace
        return if (cs != null) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    private fun copyInto(photo: Bitmap, w: Int, h: Int, csSource: Bitmap): Bitmap {
        if (photo.colorSpace == csSource.colorSpace) return photo
        val out = blank(w, h, csSource)
        Canvas(out).drawBitmap(photo, 0f, 0f, null)
        return out
    }

    /**
     * Zdjęcie w oknie ramki. Gdy okno ma (po zaokrągleniu) rozmiar zdjęcia – kopia piksel w piksel w całkowitym
     * położeniu, bez filtrowania; gdy trzeba skalować – Lanczos w świetle liniowym ([Resampler]) zamiast
     * dwuliniowego filtra Canvas, który rozmywa szczegóły.
     */
    private fun drawPhoto(canvas: Canvas, photo: Bitmap, rect: AndroidRectF) {
        val w = rect.width().roundToInt().coerceAtLeast(1)
        val h = rect.height().roundToInt().coerceAtLeast(1)
        val left = rect.left.roundToInt().toFloat()
        val top = rect.top.roundToInt().toFloat()
        // I: okno różne od zdjęcia tylko o zaokrąglenie (≤ 2 px) – kopia 1:1 wyśrodkowana i przycięta do okna
        // zamiast pełnego Lanczosa (~0,4 s na 12 MP); różnica ≤ 1 px brzegu, niewidoczna w białej ramce.
        if (abs(w - photo.width) <= SNAP_PX && abs(h - photo.height) <= SNAP_PX) {
            canvas.save()
            canvas.clipRect(left, top, left + w, top + h)
            canvas.drawBitmap(photo, left + (w - photo.width) / 2, top + (h - photo.height) / 2, null)
            canvas.restore()
            return
        }
        val px = Resampler.resize(photo, w, h)
        val scaled = blank(w, h, photo)
        scaled.setPixels(px, 0, w, 0, 0, w, h)
        canvas.drawBitmap(scaled, left, top, null)
        scaled.recycle()
    }
}

private const val SNAP_PX = 2

/** Płótno co najwyżej tyle razy dłuższe niż zdjęcie (bardzo szerokie marginesy lub proporcje). */
private const val MAX_GROW = 2f
