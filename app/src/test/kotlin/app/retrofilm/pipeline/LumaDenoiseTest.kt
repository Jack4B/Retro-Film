package app.retrofilm.pipeline

import java.util.Random
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaDenoiseTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun psnr(a: IntArray, b: IntArray): Double {
        var se = 0.0
        for (i in a.indices) {
            val d = ((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF)
            se += d * d
        }
        return 10 * log10(255.0 * 255.0 / (se / a.size))
    }

    private fun scene(w: Int, h: Int) = IntArray(w * h) { gray(if (it % w < w / 2) 70 else 180) }

    private fun noisy(clean: IntArray, sigma: Double): IntArray {
        val rnd = Random(21)
        return IntArray(clean.size) {
            gray(((clean[it] and 0xFF) + rnd.nextGaussian() * sigma).toInt().coerceIn(0, 255))
        }
    }

    @Test fun zeroAmountIsIdentity() {
        val px = noisy(scene(60, 40), 6.0)
        val copy = px.copyOf()
        LumaDenoise.apply(px, 60, 40, 0f)
        assertTrue(px.contentEquals(copy))
    }

    @Test fun denoisingImprovesPsnrAndKeepsTheEdge() {
        val w = 120
        val h = 80
        val clean = scene(w, h)
        val noisy = noisy(clean, 9.0)
        val out = noisy.copyOf()
        LumaDenoise.apply(out, w, h, 0.8f)
        assertTrue("PSNR rośnie o > 3 dB", psnr(clean, out) > psnr(clean, noisy) + 3.0)
        val dark = out[40 * w + 20] and 0xFF
        val bright = out[40 * w + 100] and 0xFF
        assertEquals(70f, dark.toFloat(), 8f)
        assertEquals(180f, bright.toFloat(), 8f)
        assertTrue("krawędź zachowana", (out[40 * w + w / 2 + 3] and 0xFF) - (out[40 * w + w / 2 - 4] and 0xFF) > 80)
    }

    @Test fun stripsGiveTheSameResultAsOnePass() {
        val w = 70
        val h = 90
        val base = noisy(scene(w, h), 7.0)
        val whole = base.copyOf().also { LumaDenoise.apply(it, w, h, 0.6f, sigma = 7f, stripRows = 1000) }
        val strips = base.copyOf().also { LumaDenoise.apply(it, w, h, 0.6f, sigma = 7f, stripRows = 13) }
        var maxDiff = 0
        for (i in whole.indices) maxDiff = maxOf(maxDiff, kotlin.math.abs((whole[i] and 0xFF) - (strips[i] and 0xFF)))
        assertTrue("paski = jeden przebieg (±1)", maxDiff <= 1)
    }
}
