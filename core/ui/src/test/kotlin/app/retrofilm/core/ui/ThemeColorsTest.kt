package app.retrofilm.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun accentIsBrandAmber() {
        assertEquals(Color(0xFFF5A33B), AccentAmber)
    }

    @Test
    fun accentOnBackgroundMeetsWcagAa() {
        assertTrue(contrast(AccentAmber, RfBackground) >= 4.5f)
    }

    @Test
    fun onSurfaceVariantMeetsWcagAa() {
        assertTrue(contrast(RfOnSurfaceVariant, RfSurface) >= 4.5f)
    }
}
