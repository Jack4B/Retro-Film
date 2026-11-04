package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Analiza pasmowa ma dawać bit w bit ten sam wynik przy każdym uruchomieniu (redukcja w stałej kolejności). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeterminismTest {
    @Test
    fun reduceIsBitIdenticalAndMatchesFixedGrouping() {
        val rnd = Random(7)
        val values = FloatArray(10_000) { rnd.nextFloat() * 1e4f + 1e-3f }
        val chunks = 997
        fun run() = Parallel.reduce(chunks, { FloatArray(1) }, { a, b -> a[0] += b[0] }) { acc, c ->
            var i = c
            while (i < values.size) {
                acc[0] += values[i]
                i += chunks
            }
        }[0]
        val expected = run()
        repeat(RUNS) { assertEquals(expected.toRawBits(), run().toRawBits()) }
        // ta sama wartość co sekwencyjna redukcja w stałych grupach (niezależnie od liczby wątków)
        val groups = minOf(Parallel.REDUCE_GROUPS, chunks)
        var total = 0f
        for (g in 0 until groups) {
            var part = 0f
            for (c in g * chunks / groups until (g + 1) * chunks / groups) {
                var i = c
                while (i < values.size) {
                    part += values[i]
                    i += chunks
                }
            }
            total = if (g == 0) part else total + part
        }
        assertEquals(total.toRawBits(), expected.toRawBits())
    }

    @Test
    fun stripPipelineRepeatsBitIdentical() {
        val w = 640
        val h = 480
        val rnd = Random(11)
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            fun n(v: Int) = (v + rnd.nextInt(-12, 13)).coerceIn(0, 255)
            Color.rgb(n(x * 255 / w), n(y * 255 / h), n((x xor y) and 255))
        }
        val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val adj = Adjustments(
            denoise = 0.5f,
            dehaze = 0.4f,
            shadows = 0.5f,
            highlights = -0.4f,
            clarity = 0.5f,
            sharpness = 0.5f
        )
        fun pixels(): IntArray {
            val out = AdjustmentsApplier.apply(src, adj)
            return IntArray(w * h).also { out.getPixels(it, 0, w, 0, 0, w, h) }
        }
        val first = pixels()
        repeat(RUNS) { assertArrayEquals(first, pixels()) }
    }

    private companion object {
        const val RUNS = 5
    }
}
