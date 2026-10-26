package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Potok pasmowy ([StripPipeline]) ma dawać ten sam obraz co dawny ([AdjustmentsApplier.applyLegacy]) z dokładnością do
 * zaokrągleń: pośrednie kwantyzacje znikają, a mapy globalne liczą się z podpróbkowania. Progi są średnią i
 * 99. percentylem bezwzględnej różnicy kanałów (poziomy 0..255); liczby trafiają do komunikatu błędu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StripPipelineTest {
    private fun image(w: Int = W, h: Int = H): Bitmap {
        val rnd = Random(SEED)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val checker = if ((x / EDGE + y / EDGE) % 2 == 0) EDGE_HIGH else EDGE_LOW
            fun noisy(v: Int) = (v + rnd.nextInt(-NOISE, NOISE + 1)).coerceIn(0, MAX)
            Color.rgb(noisy(x * MAX / w), noisy(y * MAX / h), noisy(checker))
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pixels(b: Bitmap): IntArray {
        val out = IntArray(b.width * b.height)
        b.getPixels(out, 0, b.width, 0, 0, b.width, b.height)
        return out
    }

    private class Diff(val mean: Double, val p99: Int)

    private fun diff(a: Bitmap, b: Bitmap): Diff {
        val pa = pixels(a)
        val pb = pixels(b)
        val hist = IntArray(MAX + 1)
        var sum = 0L
        for (i in pa.indices) {
            for (shift in intArrayOf(RED, GREEN, 0)) {
                val d = abs(((pa[i] shr shift) and MASK) - ((pb[i] shr shift) and MASK))
                hist[d]++
                sum += d
            }
        }
        val total = pa.size * CHANNELS
        var seen = 0
        var p99 = 0
        while (seen < total * P99 / PERCENT) seen += hist[p99++]
        return Diff(sum.toDouble() / total, p99)
    }

    private fun check(name: String, adj: Adjustments, meanMax: Double, p99Max: Int) {
        val src = image()
        val d = diff(AdjustmentsApplier.apply(src, adj), AdjustmentsApplier.applyLegacy(src, adj))
        assertTrue(
            "$name: średnia różnica ${"%.2f".format(d.mean)} (próg $meanMax), p99 ${d.p99} (próg $p99Max)",
            d.mean <= meanMax && d.p99 <= p99Max
        )
    }

    @Test fun basicCorrectionsMatchLegacy() {
        val adj = Adjustments(exposure = 0.3f, contrast = 0.2f, saturation = 0.2f, fade = 0.2f)
        check("korekty podstawowe", adj, 0.3, 2)
    }

    @Test fun shadowsAndHighlightsMatchLegacy() {
        check("cienie i światła", Adjustments(shadows = 0.6f, highlights = -0.4f), 1.0, 5)
    }

    @Test fun sharpnessAndClarityMatchLegacy() {
        check("ostrość i wyrazistość", Adjustments(sharpness = 0.5f, clarity = 0.5f), 1.0, 6)
    }

    @Test fun restorationMatchesLegacy() {
        check("odszumianie i dehaze", Adjustments(dehaze = 0.5f, denoise = 0.6f, lumaDenoise = 0.4f), 2.5, 12)
    }

    @Test fun everythingTogetherMatchesLegacy() {
        val adj = Adjustments(
            shadows = 0.4f,
            clarity = 0.4f,
            sharpness = 0.3f,
            dehaze = 0.4f,
            denoise = 0.4f,
            lumaDenoise = 0.3f,
            vignette = 0.3f
        )
        check("wszystko", adj, 3.0, 14)
    }

    @Test fun grainAndVignetteMatchLegacy() {
        check("ziarno i winieta", Adjustments(grain = 0.4f, vignette = 0.4f), 0.3, 2)
    }

    @Test fun resultDoesNotDependOnScheduling() {
        val src = image()
        val adj = Adjustments(shadows = 0.4f, sharpness = 0.4f, clarity = 0.3f, dehaze = 0.3f, lumaDenoise = 0.3f)
        assertArrayEquals(pixels(AdjustmentsApplier.apply(src, adj)), pixels(AdjustmentsApplier.apply(src, adj)))
    }

    @Test fun smallImagesAreHandled() {
        val src = image(SMALL, SMALL)
        val adj = Adjustments(shadows = 0.4f, sharpness = 0.4f, clarity = 0.3f, dehaze = 0.3f, denoise = 0.3f)
        val out = AdjustmentsApplier.apply(src, adj)
        assertTrue(out.width == SMALL && out.height == SMALL)
    }

    private companion object {
        const val W = 641
        const val H = 477
        const val SMALL = 37
        const val SEED = 7
        const val EDGE = 40
        const val EDGE_HIGH = 190
        const val EDGE_LOW = 60
        const val NOISE = 6
        const val MAX = 255
        const val MASK = 0xFF
        const val RED = 16
        const val GREEN = 8
        const val CHANNELS = 3
        const val P99 = 99
        const val PERCENT = 100
    }
}
