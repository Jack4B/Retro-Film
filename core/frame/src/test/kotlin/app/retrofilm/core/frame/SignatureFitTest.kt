package app.retrofilm.core.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Najgorszy przypadek z przeglądu: 40 szerokich znaków (W/M/Ś + polskie litery) na płótnie 4:5
 * przy marginesach 0%, 5% i 15%. Blok podpisu musi leżeć w pasie podpisu i w ramce.
 */
class SignatureFitTest {
    private val wide = "WŚMWŚMWŚMW ŻÓŁWŚMWĘŚ MWŚMWĄŚMWŃ ŹDŹBŁOMW"

    /** Szerokość znaku: szerokie 1.0·size, pozostałe 0.6·size, spacja 0.3·size; linia 1.3·size. */
    private fun advance(c: Char) = when {
        c == ' ' -> 0.3f
        c.uppercaseChar() in "WMŚŻÓŁĘĄŃŹDB" -> 1.0f
        else -> 0.6f
    }

    /** Zachłanne łamanie po słowach; słowo dłuższe niż linia łamane po znakach (jak Compose). */
    private val measurer = SignatureMeasurer { size, wrap ->
        if (wrap == null) {
            TextBlock(wide.sumOf { advance(it).toDouble() }.toFloat() * size, LINE * size, 1)
        } else {
            val widths = mutableListOf(0f)
            for (word in wide.split(' ')) {
                val ww = word.sumOf { advance(it).toDouble() }.toFloat() * size
                val space = if (widths.last() == 0f) 0f else advance(' ') * size
                if (widths.last() + space + ww <= wrap) {
                    widths[widths.lastIndex] += space + ww
                } else {
                    if (widths.last() > 0f) widths += 0f
                    for (c in word) {
                        val cw = advance(c) * size
                        if (widths.last() + cw > wrap && widths.last() > 0f) widths += 0f
                        widths[widths.lastIndex] += cw
                    }
                }
            }
            TextBlock(widths.max(), LINE * size * widths.size, widths.size)
        }
    }

    @Test fun fortyWideCharsStayInsideBandAndFrame() {
        assertEquals(FrameGeometry.SIGNATURE_MAX_CHARS, wide.length)
        for (canvasW in listOf(800f, 80f)) {
            val canvas = SizeF(canvasW, canvasW * 5f / 4f)
            for (margin in listOf(0f, 0.05f, 0.15f)) {
                val l = FrameGeometry.layout(canvas, SizeF(3f, 2f), margin, signature = true)
                val band = l.signatureBand!!
                val fit = FrameGeometry.fitSignature(l.signatureBaseSize, l.signatureMaxWidth, band.height, measurer)
                val r = FrameGeometry.signatureBounds(l, fit.width, fit.height)!!
                val ctx = "W=$canvasW m=$margin fit=$fit rect=$r band=$band"
                assertTrue(ctx, fit.fits)
                assertTrue(ctx, fit.sizePx >= FrameGeometry.SIGNATURE_FLOOR_SCALE * l.signatureBaseSize)
                assertTrue(ctx, fit.lines in 1..2)
                assertTrue(ctx, fit.width <= l.signatureMaxWidth)
                assertTrue(ctx, r.left >= band.left && r.right <= band.right)
                assertTrue(ctx, r.top >= band.top && r.bottom <= band.bottom)
                assertTrue(ctx, r.left >= 0f && r.right <= l.canvasWidth)
                assertTrue(ctx, r.top >= 0f && r.bottom <= l.canvasHeight)
                assertTrue(ctx, r.top >= l.photoArea.bottom)
            }
        }
    }

    @Test fun impossibleTextReportsDoesNotFitAtReadabilityFloor() {
        // Każdy rozmiar daje 3 linie i blok szerszy od dopuszczalnego — nie da się zmieścić.
        val tooWide = SignatureMeasurer { size, _ -> TextBlock(size * 100f, size * 3f, 3) }
        val base = 75f
        val fit = FrameGeometry.fitSignature(base, maxWidth = 500f, maxHeight = 100f, measurer = tooWide)
        assertFalse(fit.fits)
        assertEquals(FrameGeometry.SIGNATURE_FLOOR_SCALE * base, fit.sizePx, 1e-3f)
    }

    @Test fun shortTextFitsAtBaseSize() {
        val m = SignatureMeasurer { size, _ -> TextBlock(size * 3f, size * 1.3f, 1) }
        val fit = FrameGeometry.fitSignature(75f, 500f, 200f, m)
        assertTrue(fit.fits)
        assertEquals(75f, fit.sizePx, 1e-3f)
    }

    private companion object {
        const val LINE = 1.3f
    }
}
