package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResamplerTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun red(c: Int) = (c shr 16) and 0xFF

    @Test fun kernelIsLanczosThree() {
        assertEquals(1f, Resampler.kernel(0f), 1e-6f)
        for (k in 1..2) assertEquals(0f, Resampler.kernel(k.toFloat()), 1e-5f)
        assertEquals(0f, Resampler.kernel(3f), 0f)
        assertEquals(Resampler.kernel(0.7f), Resampler.kernel(-0.7f), 1e-7f)
    }

    @Test fun constantImageStaysConstantWhenScaled() {
        val src = IntArray(40 * 30) { gray(97) }
        for ((w, h) in listOf(20 to 15, 13 to 7, 80 to 60)) {
            val out = Resampler.resize(src, 40, 30, w, h)
            assertTrue("stały kolor zachowany", out.all { red(it) == 97 })
        }
    }

    @Test fun checkerboardAveragesToMidGrayWhenHalved() {
        val src = IntArray(64 * 64) { gray(if ((it % 64 + it / 64) % 2 == 0) 0 else 255) }
        val out = Resampler.resize(src, 64, 64, 32, 32)
        val inner = (4 until 28).flatMap { y -> (4 until 28).map { x -> red(out[y * 32 + x]) } }
        assertTrue("szachownica bez aliasingu → szarość", inner.all { it in 118..137 })
    }

    @Test fun linearRampIsPreservedWhenDownscaled() {
        val src = IntArray(100 * 4) { gray((it % 100) * 255 / 99) }
        val out = Resampler.resize(src, 100, 4, 50, 4)
        for (x in 5 until 45) {
            val expected = ((x + 0.5f) * 2f - 0.5f) * 255f / 99f
            assertEquals("rampa x=$x", expected, red(out[x]).toFloat(), 3f)
        }
    }

    @Test fun sharpEdgeStaysSharpAndRingingIsClamped() {
        val src = IntArray(60 * 4) { gray(if (it % 60 < 30) 20 else 230) }
        val out = Resampler.resize(src, 60, 4, 30, 4)
        assertTrue(red(out[10]) <= 25 && red(out[20]) >= 225)
        assertTrue(out.all { red(it) in 0..255 })
        val transition = (12 until 18).count { red(out[it]) in 40..210 }
        assertTrue("przejście zajmuje najwyżej 2 piksele", transition <= 2)
    }
}
