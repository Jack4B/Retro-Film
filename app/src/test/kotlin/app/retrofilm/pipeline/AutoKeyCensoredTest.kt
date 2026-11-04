package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** J: klucz ekspozycji z cenzurą przepalonych – bez przepalonych bez zmian, z przepalonymi ≥ dolnej granicy. */
class AutoKeyCensoredTest {
    private val gw = 16
    private val gh = 16
    private val lumas = FloatArray(gw * gh) { 0.02f + 0.6f * (it % gw) / gw }

    @Test fun withoutClippingKeyIsPlainLogMean() {
        val plain = AutoEnhance.key(lumas, Sample(FloatArray(3 * gw * gh), gw, gh))
        val again = AutoEnhance.key(lumas, Sample(FloatArray(3 * gw * gh), gw, gh, BooleanArray(gw * gh)))
        assertEquals(plain, again, 0f)
    }

    @Test fun clippedBlocksRaiseKeyBoundedly() {
        val clipped = BooleanArray(gw * gh) { it % gw >= gw - 3 }
        val lower = AutoEnhance.key(lumas, Sample(FloatArray(3 * gw * gh), gw, gh))
        val censored = AutoEnhance.key(lumas, Sample(FloatArray(3 * gw * gh), gw, gh, clipped))
        assertTrue("$censored vs $lower", censored > lower)
        assertTrue("$censored vs $lower", censored < lower * 1.11f) // ≤ e^0,1 przy części przepalonej
    }
}
