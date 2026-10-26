package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
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
class HighlightRecoveryTest {
    private val size = 192

    /** Jasna scena z teksturą (chmury): tło 205 ± 25, fala o okresie 12 px, lekko niebieska. */
    private fun clouds(): Bitmap {
        val px = IntArray(size * size) {
            val x = it % size
            val y = it / size
            val v = 205f + 25f * sin(2f * PI.toFloat() * x / 12f) * sin(2f * PI.toFloat() * y / 12f)
            Color.rgb((v - 10f).toInt(), (v - 2f).toInt(), (v + 8f).toInt().coerceAtMost(255))
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun lumaOf(bmp: Bitmap): FloatArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return FloatArray(px.size) {
            0.2126f * Color.red(px[it]) + 0.7152f * Color.green(px[it]) + 0.0722f * Color.blue(px[it])
        }
    }

    private fun stdev(v: FloatArray): Float {
        val mean = v.average().toFloat()
        return sqrt(v.fold(0f) { acc, x -> acc + (x - mean) * (x - mean) } / v.size)
    }

    private fun chroma(bmp: Bitmap): Float {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px.map {
            val hi = maxOf(Color.red(it), Color.green(it), Color.blue(it))
            val lo = minOf(Color.red(it), Color.green(it), Color.blue(it))
            (hi - lo).toFloat()
        }.average().toFloat()
    }

    @Test fun darkensBrightBaseButKeepsLocalContrastAndColour() {
        val src = clouds()
        val out = AdjustmentsApplier.apply(src, Adjustments(highlights = -1f))
        val before = lumaOf(src)
        val after = lumaOf(out)
        assertTrue("średnia jasność spada o ≥ 15", before.average() - after.average() >= 15.0)
        val ratio = stdev(after) / stdev(before)
        assertTrue("kontrast lokalny ≥ 0,85 oryginału: $ratio", ratio >= 0.85f)
        assertTrue("chroma ≥ 0,95 oryginału: ${chroma(out)} vs ${chroma(src)}", chroma(out) >= 0.95f * chroma(src))
    }

    @Test fun blownFlatWhiteIsNotTurnedGrey() {
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.WHITE)
        val out = AdjustmentsApplier.apply(src, Adjustments(highlights = -1f))
        val red = Color.red(out.getPixel(10, 10))
        assertTrue("wypalona biel zostaje ≥ 225: $red", red >= 225)
    }

    @Test fun midtonesAndShadowsAreUntouched() {
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(90, 90, 90))
        val out = AdjustmentsApplier.apply(src, Adjustments(highlights = -1f))
        assertEquals(90f, Color.red(out.getPixel(10, 10)).toFloat(), 1.5f)
    }
}
