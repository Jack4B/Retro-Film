package app.retrofilm.pipeline

import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpenerTest {
    private fun step(w: Int, h: Int, lo: Float = 80f, hi: Float = 170f) =
        FloatArray(w * h) { if (it % w < w / 2) lo else hi }

    @Test fun zeroAmountAndFlatImageGiveNoChange() {
        val flat = FloatArray(40 * 20) { 100f }
        assertTrue(Sharpener.delta(flat, 40, 20, SharpenParams(1f)).all { it == 0f })
        assertTrue(Sharpener.delta(step(40, 20), 40, 20, SharpenParams(0f)).all { it == 0f })
    }

    @Test fun unsharpOvershootsBothSidesOfAnEdge() {
        val w = 64
        val d = Sharpener.delta(step(w, 8), w, 8, SharpenParams(1f, mode = SharpenMode.Unsharp))
        assertTrue("jasna strona jaśniejsza", d[4 * w + w / 2] > 5f)
        assertTrue("ciemna strona ciemniejsza", d[4 * w + w / 2 - 1] < -5f)
        assertEquals("daleko od krawędzi bez zmian", 0f, d[4 * w + 2], 0.01f)
    }

    @Test fun haloIsLimitedAtLowDetail() {
        val w = 64
        val strong = step(w, 8, 20f, 240f)
        val low = Sharpener.delta(strong, w, 8, SharpenParams(1f, detail = 0f, mode = SharpenMode.Unsharp))
        val high = Sharpener.delta(strong, w, 8, SharpenParams(1f, detail = 1f, mode = SharpenMode.Standard))
        val maxLow = low.maxOf { abs(it) }
        assertTrue("USM ograniczona soft-clipem (< 30)", maxLow < 30f)
        assertTrue(high.maxOf { abs(it) } > 0f)
    }

    @Test fun maskingProtectsTextureButKeepsStrongEdges() {
        val w = 120
        val rnd = Random(3)
        val img = FloatArray(w * 40) { i ->
            val x = i % w
            val tex = 100f + (rnd.nextFloat() - 0.5f) * 14f
            if (x < w / 2) tex else tex + 120f
        }
        val none = Sharpener.delta(img, w, 40, SharpenParams(1f, masking = 0f, mode = SharpenMode.Unsharp))
        val masked = Sharpener.delta(img, w, 40, SharpenParams(1f, masking = 1f, mode = SharpenMode.Unsharp))
        fun energy(d: FloatArray, xs: IntRange) =
            xs.sumOf { x -> (5 until 35).sumOf { y -> abs(d[y * w + x]).toDouble() } }
        assertTrue("faktura bez maski jest wyostrzana", energy(none, 5..40) > 50.0)
        assertTrue("maska chroni gładką fakturę", energy(masked, 5..40) < energy(none, 5..40) * 0.25)
        assertTrue("mocna krawędź zostaje", energy(masked, 58..61) > energy(none, 58..61) * 0.5)
    }

    @Test fun deconvolutionSteepensABlurredEdge() {
        val w = 80
        val sharpStep = step(w, 6)
        val blurred = Sharpener.gauss(sharpStep, w, 6, 1.5f)
        val d = Sharpener.delta(blurred, w, 6, SharpenParams(1f, radius = 1.5f, mode = SharpenMode.Deconvolution))
        fun slope(a: FloatArray) = (w / 2 - 3 until w / 2 + 3).maxOf { abs(a[3 * w + it + 1] - a[3 * w + it]) }
        val restored = FloatArray(blurred.size) { blurred[it] + d[it] }
        assertTrue("krawędź po dekonwolucji bardziej stroma", slope(restored) > slope(blurred) * 1.15f)
    }

    @Test fun stripsGiveTheSameResultAsOnePass() {
        val w = 50
        val h = 90
        val rnd = Random(9)
        val img = FloatArray(w * h) { 60f + rnd.nextFloat() * 120f }
        val p = SharpenParams(0.8f, masking = 0.4f)
        val whole = Sharpener.delta(img, w, h, p, stripRows = 1000)
        val strips = Sharpener.delta(img, w, h, p, stripRows = 17)
        for (i in whole.indices) assertEquals(whole[i], strips[i], 0.05f)
    }

    @Test fun maskPreviewIsWhiteWithoutMaskingAndDarkOnFlat() {
        val flat = FloatArray(30 * 30) { 90f }
        assertTrue(Sharpener.maskOnly(flat, 30, 30, SharpenParams(1f, masking = 0f)).all { it == 1f })
        assertTrue(Sharpener.maskOnly(flat, 30, 30, SharpenParams(1f, masking = 0.7f)).all { it < 0.01f })
    }
}
