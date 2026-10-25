package app.retrofilm.core.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class SignatureFitCheckTest {
    /** Miara „monospace”: szerokość = liczba znaków × 0,6 × rozmiar, brak łamania. */
    private fun measurer(text: String) = SignatureMeasurer { size, _ ->
        TextBlock(width = text.length * 0.6f * size, height = size * 1.2f, lines = 1)
    }

    private fun check(text: String, enabled: Boolean = true) = SignatureFitCheck.check(
        frameEnabled = enabled,
        signatureEnabled = true,
        signature = text,
        aspect = FrameAspect.entries.first(),
        margin = FrameGeometry.DEFAULT_MARGIN,
        photoAspect = 1.5f,
        measurer = measurer(text)
    )

    @Test fun disabledOrBlankIsNone() {
        assertEquals(SignatureFitStatus.None, check("abc", enabled = false))
        assertEquals(SignatureFitStatus.None, check("  "))
    }

    @Test fun shortFits() = assertEquals(SignatureFitStatus.Fits, check("Ala"))

    @Test fun hugeIsTooLong() = assertEquals(SignatureFitStatus.TooLong, check("x".repeat(2000)))
}
