package app.retrofilm.core.engine

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Golden GPU vs CPU: zapisuje diff.json (max/średnia delta na kanał) w filesDir aplikacji testowej. */
@RunWith(AndroidJUnit4::class)
class GpuVsCpuTest {
    @Test
    fun gpuMatchesCpu() = runBlocking {
        val src = testImage()
        val lut = filmLikeLut()
        val reference = CpuLutEngine().apply(src, lut, 1f)
        val engines = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(AgslLutEngine())
            if (GlesLutEngine.isSupported()) add(GlesLutEngine())
        }
        val report = JSONObject().put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
            .put("tolerance", MAX_DELTA).put("width", W).put("height", H)
        val results = JSONArray()
        val failures = mutableListOf<String>()
        for (engine in engines) {
            val stats = diff(reference, engine.apply(src, lut, 1f))
            results.put(stats.toJson(engine.name))
            if (stats.max.any { it > MAX_DELTA }) failures += "${engine.name}: max=${stats.max.toList()}"
        }
        report.put("backends", results)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.filesDir, "diff.json").writeText(report.toString(2))
        assertTrue("Przekroczona tolerancja $MAX_DELTA/255: $failures", failures.isEmpty())
    }

    private class Stats(val max: IntArray, val mean: DoubleArray) {
        fun toJson(name: String): JSONObject {
            val o = JSONObject().put("backend", name)
            CHANNELS.forEachIndexed { i, c ->
                o.put("max_$c", max[i]).put("mean_$c", mean[i])
            }
            return o
        }
    }

    private fun diff(a: Bitmap, b: Bitmap): Stats {
        val pa = IntArray(W * H).also { a.getPixels(it, 0, W, 0, 0, W, H) }
        val pb = IntArray(W * H).also { b.getPixels(it, 0, W, 0, 0, W, H) }
        val max = IntArray(3)
        val sum = LongArray(3)
        for (i in pa.indices) {
            for (c in 0 until 3) {
                val shift = 16 - c * 8
                val d = abs((pa[i] shr shift and 0xFF) - (pb[i] shr shift and 0xFF))
                if (d > max[c]) max[c] = d
                sum[c] += d.toLong()
            }
        }
        return Stats(max, DoubleArray(3) { sum[it].toDouble() / pa.size })
    }

    private fun testImage(): Bitmap {
        val px = IntArray(W * H) { i ->
            val x = i % W
            val y = i / W
            val r = x * 255 / (W - 1)
            val g = y * 255 / (H - 1)
            val b = (x * 7 + y * 13) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
    }

    private fun filmLikeLut(): Lut = Lut.generate(Lut.DEFAULT_SIZE) { r, g, b ->
        Triple(
            (r * 0.9f + 0.08f * sin(g * 3f) + 0.05f).coerceIn(0f, 1f),
            (g * 0.85f + 0.1f * r * b).coerceIn(0f, 1f),
            (b * 0.8f + 0.1f * cos(r * 2f)).coerceIn(0f, 1f)
        )
    }

    private companion object {
        const val W = 512
        const val H = 384
        const val MAX_DELTA = 2
        val CHANNELS = listOf("r", "g", "b")
    }
}
