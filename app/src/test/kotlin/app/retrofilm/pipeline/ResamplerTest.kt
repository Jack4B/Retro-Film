package app.retrofilm.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        val out = Resampler.resizeEncoded(src, 64, 64, 32, 32)
        val inner = (4 until 28).flatMap { y -> (4 until 28).map { x -> red(out[y * 32 + x]) } }
        assertTrue("szachownica bez aliasingu → szarość", inner.all { it in 118..137 })
    }

    @Test fun checkerboardAveragesToLinearMidGrayInLinearLight() {
        val src = IntArray(64 * 64) { gray(if ((it % 64 + it / 64) % 2 == 0) 0 else 255) }
        val out = Resampler.resize(src, 64, 64, 32, 32)
        val inner = (4 until 28).flatMap { y -> (4 until 28).map { x -> red(out[y * 32 + x]) } }
        // 50% światła liniowego = 188 w sRGB (uśrednianie w gammie przyciemniało do 128)
        assertTrue("światło liniowe: ${inner.minOrNull()}..${inner.maxOrNull()}", inner.all { it in 180..196 })
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

    @Test fun everyLevelSurvivesLinearRoundTrip() {
        val src = IntArray(256 * 2) { gray(it % 256) }
        val out = Resampler.resize(src, 256, 2, 256, 1)
        for (v in 0..255) assertEquals("poziom $v", v, red(out[v]))
    }

    @Test fun bitmapPathMatchesArrayPath() {
        val w = 300
        val h = 257
        val px = IntArray(w * h) { (0xFF shl 24) or ((it * 2654435761L).toInt() and 0xFFFFFF) }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val a = Resampler.resize(px, w, h, 97, 83)
        val b = Resampler.resize(bmp, 97, 83)
        assertArrayEquals(a, b)
        assertArrayEquals(a, Resampler.resize(px, w, h, 97, 83))
    }

    @Test fun workingSetIsFarBelowFullCopy() {
        // 50 MP (8160×6120) → 2048 px: dawniej kopia źródła 200 MB + bufor dw×sh 50 MB
        val perWorker = Resampler.bandBytes(8160, 6120, 2048, 1536)
        val old = 8160L * 6120 * 4 + 2048L * 6120 * 4
        assertTrue("pas $perWorker B", perWorker * 4 < old / 5)
    }
}
