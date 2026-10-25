package app.retrofilm.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SizeF
import app.retrofilm.feature.editor.frame.ComposeSignatureMeasurer
import app.retrofilm.feature.editor.frame.signatureStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Prawdziwy pomiar Compose (Caveat) – 40 szerokich znaków na 4:5 przy marginesach 0/5/15%. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SignatureFitRobolectricTest {
    @get:Rule val compose = createComposeRule()

    private val texts = listOf(
        "WŚMWŚMWŚMW ŻÓŁWŚMWĘŚ MWŚMWĄŚMWŃ ŹDŹBŁOMW",
        "WŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMWŚMW"
    )

    @Test fun fortyWideCharsFitBandAndFrame() {
        val measurer = TextMeasurer(
            createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
            Density(1f),
            LayoutDirection.Ltr
        )
        val style = signatureStyle(Color.Black, 1f)
        for (text in texts) {
            assertEquals(FrameGeometry.SIGNATURE_MAX_CHARS, text.length)
            for (canvasW in listOf(1080f, 400f)) {
                for (margin in listOf(0f, 0.05f, 0.15f)) {
                    val l = FrameGeometry.layout(SizeF(canvasW, canvasW * 5f / 4f), SizeF(3f, 2f), margin, true)
                    val band = l.signatureBand!!
                    val fit = FrameGeometry.fitSignature(
                        l.signatureBaseSize,
                        l.signatureMaxWidth,
                        band.height,
                        ComposeSignatureMeasurer(measurer, text, style)
                    )
                    // Ten sam pomiar co w FramePreview przy rysowaniu.
                    val drawn = measurer.measure(
                        text,
                        style(fit.sizePx),
                        maxLines = fit.lines,
                        softWrap = fit.lines > 1,
                        constraints = Constraints(maxWidth = l.signatureMaxWidth.toInt())
                    )
                    val r = FrameGeometry.signatureBounds(l, drawn.size.width.toFloat(), drawn.size.height.toFloat())!!
                    val ctx = "\"$text\" W=$canvasW m=$margin fit=$fit rect=$r band=$band"
                    assertTrue(ctx, fit.lines in 1..2)
                    assertTrue(ctx, !drawn.hasVisualOverflow)
                    assertTrue(ctx, r.left >= 0f && r.right <= l.canvasWidth)
                    assertTrue(ctx, r.width <= l.signatureMaxWidth)
                    assertTrue(ctx, r.top >= band.top && r.bottom <= band.bottom)
                    assertTrue(ctx, r.top >= l.photoArea.bottom)
                }
            }
        }
    }
}
