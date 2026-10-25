package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LutAffineFitTest {
    @Test
    fun identityFitsIdentityMatrix() {
        val m = LutAffineFit.fit(Lut.identity())
        for (c in 0 until 3) for (k in 0 until 4) assertEquals(if (c == k) 1f else 0f, m[c][k], 0.01f)
    }

    @Test
    fun affineLutIsRecovered() {
        val lut = Lut.generate(17) { r, g, b -> Triple(0.5f * r + 0.2f, 0.8f * g, 0.3f * b + 0.1f * r) }
        val m = LutAffineFit.fit(lut)
        assertEquals(0.5f, m[0][0], 0.01f)
        assertEquals(0.2f, m[0][3], 0.01f)
        assertEquals(0.8f, m[1][1], 0.01f)
        assertEquals(0.1f, m[2][0], 0.01f)
    }
}
