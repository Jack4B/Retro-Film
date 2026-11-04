package app.retrofilm.pipeline

import android.graphics.Bitmap
import app.retrofilm.core.engine.Lut
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** G: zapis jednym przebiegiem (film + wykończenie) = dawne dwa przebiegi (w granicach ditheringu). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FusedLookTest {
    private val w = 480
    private val h = 640

    private fun photo() = Bitmap.createBitmap(
        IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val edge = if ((x / 40 + y / 40) % 2 == 0) 40 else 0
            (0xFF shl 24) or ((x * 200 / w + edge) shl 16) or ((y * 200 / h + edge / 2) shl 8) or (60 + (x xor y) % 7)
        },
        w,
        h,
        Bitmap.Config.ARGB_8888
    )

    private fun lut(): Lut {
        val n = 17
        val data = ByteArray(n * n * n * 3) { i ->
            val c = i % 3
            val idx = i / 3
            val v = when (c) {
                0 -> idx % n
                1 -> idx / n % n
                else -> idx / n / n
            } / (n - 1f)
            (v.pow(if (c == 2) 1.3f else 0.8f) * 255).toInt().toByte()
        }
        return Lut(n, data)
    }

    private val adj = Adjustments(
        exposure = 0.3f, temperature = 0.2f, tint = 0.05f, whiteDx = 0.01f, toneInversion = 0.5f, saturation = -0.1f,
        sharpness = 0.5f, grain = 0.3f, vignette = 0.3f, fade = 0.1f, shadowHue = 0.6f, shadowAmount = 0.2f
    )

    private fun diff(space: OutputSpace, a: Adjustments) {
        val src = photo()
        val old = FloatExport.look(src, lut(), 0.8f, a, space, fused = false)
        val new = FloatExport.look(src, lut(), 0.8f, a, space)
        val p = IntArray(w * h).also { old.getPixels(it, 0, w, 0, 0, w, h) }
        val q = IntArray(w * h).also { new.getPixels(it, 0, w, 0, 0, w, h) }
        var max = 0
        var sum = 0L
        for (i in p.indices) {
            for (s in intArrayOf(0, 8, 16)) {
                val d = abs((p[i] shr s and 0xFF) - (q[i] shr s and 0xFF))
                max = maxOf(max, d)
                sum += d
            }
        }
        val mean = sum.toDouble() / (p.size * 3)
        assertTrue("max=$max mean=$mean", max <= 2 && mean < 0.25)
    }

    @Test fun srgbMatchesTwoPass() = diff(OutputSpace.Srgb, adj)

    @Test fun p3MatchesTwoPass() = diff(OutputSpace.DisplayP3, adj)

    @Test fun smartSharpenMatchesTwoPass() = diff(OutputSpace.Srgb, adj.copy(grain = 0f, sharpness = 1f))
}
