package app.retrofilm.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.retrofilm.core.engine.CpuLutEngine
import app.retrofilm.core.engine.GlesLutEngine
import app.retrofilm.core.engine.Lut
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** GLES kaflami (wymuszony mały kafel) ma dawać ten sam obraz co CPU (tolerancja 2 poziomy, jak test parzystości). */
@RunWith(AndroidJUnit4::class)
class GlesTilingTest {
    private fun lut(): Lut = Lut.identity(17).let { base ->
        val d = base.data.copyOf()
        for (i in d.indices) d[i] = ((d[i].toInt() and 0xFF) * 3 / 4 + 32).toByte()
        Lut(17, d)
    }

    private fun image(w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x xor y) and 0xFF)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun tiledGlesMatchesCpu() = runBlocking {
        assumeTrue("GLES 3 niedostępny", GlesLutEngine.isSupported())
        val src = image(301, 203)
        val l = lut()
        val cpu = CpuLutEngine().apply(src, l, 0.8f)
        for (engine in listOf(GlesLutEngine(tileLimit = 64, dither = false), GlesLutEngine(dither = false))) {
            val gl = engine.apply(src, l, 0.8f)
            var diff = 0
            for (y in 0 until src.height) {
                for (x in 0 until src.width) {
                    val a = cpu.getPixel(x, y)
                    val b = gl.getPixel(x, y)
                    for (s in intArrayOf(16, 8, 0)) diff = max(diff, abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF)))
                }
            }
            assertTrue("max różnica $diff", diff <= 2)
        }
    }
}
