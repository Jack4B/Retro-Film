package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChromaEdgeTest {
    private val w = 96
    private val h = 48
    private val edge = w / 2

    private fun truth(x: Int) = if (x < edge) intArrayOf(200, 60, 60) else intArrayOf(60, 60, 200)

    private fun luma(c: IntArray) = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]

    /** Zdjęcie jak po 4:2:0: jasność ostra, kolor (Cb, Cr) rozmyty w poziomie oknem 5 pikseli. */
    private fun degraded(): Bitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var cb = 0f
                var cr = 0f
                for (k in -2..2) {
                    val t = truth((x + k).coerceIn(0, w - 1))
                    cb += (t[2] - luma(t)) / 5f
                    cr += (t[0] - luma(t)) / 5f
                }
                val yy = luma(truth(x))
                val r = yy + cr
                val b = yy + cb
                val g = (yy - 0.299f * r - 0.114f * b) / 0.587f
                px[y * w + x] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun edgeError(bmp: Bitmap): Float {
        var sum = 0f
        var n = 0
        for (y in 0 until h) {
            for (x in edge - 4 until edge + 4) {
                val c = bmp.getPixel(x, y)
                val t = truth(x)
                sum += abs(Color.red(c) - t[0]) + abs(Color.blue(c) - t[2])
                n += 2
            }
        }
        return sum / n
    }

    @Test fun colourEdgeSnapsToLuminanceEdge() {
        val src = degraded()
        val out = AdjustmentsApplier.apply(src, Adjustments(chromaEdges = 1f))
        val before = edgeError(src)
        val after = edgeError(out)
        assertTrue("błąd przy krawędzi koloru spada o ≥ 30%: $before → $after", after <= 0.7f * before)
    }

    @Test fun flatColoursAreUnchanged() {
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(180, 90, 60))
        val c = AdjustmentsApplier.apply(src, Adjustments(chromaEdges = 1f)).getPixel(w / 2, h / 2)
        assertEquals(180f, Color.red(c).toFloat(), 1.5f)
        assertEquals(90f, Color.green(c).toFloat(), 1.5f)
        assertEquals(60f, Color.blue(c).toFloat(), 1.5f)
    }
}
