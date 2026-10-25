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
import app.retrofilm.core.frame.TightFrame
import app.retrofilm.core.frame.TextBlock
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
    /** Wielkość podpisu jako ułamek maksymalnej (większego i tak nie mieści ramka). */
    val signatureScale: Float = 1f
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
            measurer = PaintSignatureMeasurer(options.signature, typeface)
        )

    /**
     * Wynikowa bitmapa w przestrzeni barw [colorSpaceSource] (np. Display P3 oryginału), więc
     * `Bitmap.compress` zapisze odpowiedni profil ICC. Bez ramki – samo zdjęcie.
     */
    fun render(photo: Bitmap, options: FrameOptions, typeface: Typeface, colorSpaceSource: Bitmap = photo): Bitmap {
        if (!options.enabled) return copyInto(photo, photo.width, photo.height, colorSpaceSource)
        val (cw, ch) = TightFrame.canvasSize(
            options.aspect,
            photo.width.toFloat(),
            photo.height.toFloat(),
            options.margin,
            options.drawsSignature,
            maxOf(photo.width, photo.height).toFloat()
        )
        val w = cw.roundToInt().coerceAtLeast(1)
        val h = ch.roundToInt().coerceAtLeast(1)
        val layout = TightFrame.layout(
            SizeF(w.toFloat(), h.toFloat()),
            SizeF(photo.width.toFloat(), photo.height.toFloat()),
            options.margin,
            options.drawsSignature
        )
        val out = blank(w, h, colorSpaceSource)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val p = layout.photo
        canvas.drawBitmap(photo, null, AndroidRectF(p.left, p.top, p.right, p.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        if (options.drawsSignature) drawSignature(canvas, layout, options, typeface)
        return out
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
        val fit = FrameGeometry.fitSignature(baseSize, layout.signatureMaxWidth, band.height, measurer)
        if (!fit.fits) throw SignatureTooLongException()
        val bounds = FrameGeometry.signatureBounds(layout, fit.width, fit.height) ?: return
        val static = measurer.layout(fit.sizePx, layout.signatureMaxWidth)
        canvas.save()
        canvas.translate((layout.canvasWidth - layout.signatureMaxWidth) / 2f, bounds.top)
        static.draw(canvas)
        canvas.restore()
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
}
