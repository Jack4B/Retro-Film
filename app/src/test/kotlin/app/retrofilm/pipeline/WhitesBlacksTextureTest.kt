package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
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
class WhitesBlacksTextureTest {
    private val size = 192

    @Test fun endsCurveKeepsEndpointsAndStaysMonotonic() {
        for ((w, b) in listOf(1f to 1f, -1f to -1f, 1f to -1f, -1f to 1f)) {
            assertEquals(0f, EndsCurve.at(0f, w, b), 1e-6f)
            assertEquals(1f, EndsCurve.at(1f, w, b), 1e-6f)
            var prev = -1f
            for (i in 0..1000) {
                val v = EndsCurve.at(i / 1000f, w, b)
                assertTrue("niemonotoniczna przy ${i / 1000f} (w=$w, b=$b)", v >= prev - 1e-6f)
                prev = v
            }
        }
    }

    private fun flat(v: Int): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        it.eraseColor(Color.rgb(v, v, v))
    }

    private fun red(b: Bitmap) = Color.red(b.getPixel(size / 2, size / 2))

    @Test fun whitesAndBlacksMoveTheEndsButNotBlackOrWhitePoint() {
        fun out(v: Int, adj: Adjustments) = red(AdjustmentsApplier.apply(flat(v), adj))
        assertTrue("biele + rozjaśniają jasne tony", out(200, Adjustments(whites = 1f)) >= 205)
        assertTrue("biele − przyciemniają jasne tony", out(200, Adjustments(whites = -1f)) <= 195)
        assertEquals(255, out(255, Adjustments(whites = -1f)))
        assertTrue("czernie + unoszą okolice czerni", out(40, Adjustments(blacks = 1f)) >= 43)
        assertEquals(0, out(0, Adjustments(blacks = 1f)))
        assertEquals(128f, out(128, Adjustments(whites = 1f, blacks = 1f)).toFloat(), 1.5f)
    }

    private fun texture(): Bitmap {
        val px = IntArray(size * size) {
            val x = it % size
            val y = it / size
            val v = 150f + 25f * sin(2f * PI.toFloat() * x / 12f) * sin(2f * PI.toFloat() * y / 12f)
            Color.rgb(v.toInt(), v.toInt(), v.toInt())
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun lumas(b: Bitmap): FloatArray {
        val px = IntArray(size * size)
        b.getPixels(px, 0, size, 0, 0, size, size)
        return FloatArray(px.size) { Color.red(px[it]).toFloat() }
    }

    private fun stdev(v: FloatArray): Float {
        val mean = v.average().toFloat()
        return sqrt(v.fold(0f) { acc, x -> acc + (x - mean) * (x - mean) } / v.size)
    }

    @Test fun textureChangesLocalContrastButNotMeanBrightness() {
        val src = lumas(texture())
        val up = lumas(AdjustmentsApplier.apply(texture(), Adjustments(texture = 1f)))
        val down = lumas(AdjustmentsApplier.apply(texture(), Adjustments(texture = -1f)))
        val upRatio = stdev(up) / stdev(src)
        val downRatio = stdev(down) / stdev(src)
        assertTrue("tekstura + wzmacnia kontrast struktur: $upRatio", upRatio >= 1.3f)
        assertTrue("tekstura − wygładza: $downRatio", downRatio <= 0.75f)
        assertTrue("średnia jasność prawie bez zmian", abs(up.average() - src.average()) <= 4.0)
        assertTrue("średnia jasność prawie bez zmian", abs(down.average() - src.average()) <= 4.0)
    }
}
