package app.retrofilm.engine

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.AgslPlaneLut
import app.retrofilm.core.engine.gpu.GlesPlaneLut
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import app.retrofilm.pipeline.LutStage
import app.retrofilm.pipeline.Planes
import app.retrofilm.pipeline.SaveBackend
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * K: jądra LUT-a na GPU vs [LutStage] na CPU – różnica pikseli (skala 0..255, przed ditheringiem) w granicy szumu
 * ditheringu ([SaveBackend.TOLERANCE]) dla każdego jądra, które na tym urządzeniu działa; co najmniej jedno musi.
 * Raportuje czasy pasa 1 MP (emulator liczy GPU programowo – czasy tylko orientacyjne).
 */
@RunWith(AndroidJUnit4::class)
class GpuSaveKernelTest {
    private val lut = Lut.generate(33) { r, g, b ->
        Triple((g * 0.7f + r * 0.3f).coerceIn(0f, 1f), (r * r + 0.1f).coerceIn(0f, 1f), 1f - b * 0.85f)
    }

    private fun report(name: String, v: Long) {
        Log.i("RetroFilmPerf", "$name=$v")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putLong(name, v) })
    }

    private fun planes(n: Int): Planes {
        val r = FloatArray(n) { (it * 0.618f) % 256f * 255f / 256f }
        val g = FloatArray(n) { (it * 0.377f + 40f) % 256f * 255f / 256f }
        val b = FloatArray(n) { (it * 1.123f + 90f) % 256f * 255f / 256f }
        return Planes(r, g, b)
    }

    private fun kernels(): List<PlaneLutKernel> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(AgslPlaneLut(false))
            add(AgslPlaneLut(true))
        }
        add(GlesPlaneLut())
    }

    @Test
    fun gpuKernelsMatchCpuWithinDitherNoise() {
        val n = 1_000_000
        val cpu = LutStage(lut, 0.8f, false)
        val ref = planes(n)
        val t0 = System.nanoTime()
        cpu.apply(ref, 0, n)
        report("gpu_kernel_cpu_1mp_ms", (System.nanoTime() - t0) / 1_000_000)
        val good = mutableListOf<String>()
        for (k in kernels()) {
            val key = k.name.replace('-', '_')
            val p = planes(n)
            val result = runCatching {
                k.apply(p.r, p.g, p.b, 0, 1024, lut, 0.8f) // rozgrzewka (kontekst, atlas LUT-a)
                val p2 = planes(n)
                val t = System.nanoTime()
                k.apply(p2.r, p2.g, p2.b, 0, n, lut, 0.8f)
                report("gpu_kernel_${key}_1mp_ms", (System.nanoTime() - t) / 1_000_000)
                var max = 0f
                for (i in 0 until n) {
                    max = maxOf(max, abs(p2.r[i] - ref.r[i]), abs(p2.g[i] - ref.g[i]), abs(p2.b[i] - ref.b[i]))
                }
                max
            }
            runCatching { k.close() }
            val diff = result.getOrNull()
            val err = result.exceptionOrNull()
            Log.i("RetroFilmPerf", "${k.name}: diff=$diff error=$err", err)
            err?.let { e ->
                val msg = generateSequence(e) {
                    it.cause
                }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                InstrumentationRegistry.getInstrumentation().sendStatus(
                    0,
                    Bundle().apply {
                        putString("gpu_kernel_${key}_error", msg.take(400))
                    }
                )
            }
            report("gpu_kernel_${key}_diff_x100", ((diff ?: -1f) * 100).toLong())
            if (diff != null && diff <= SaveBackend.TOLERANCE) good += k.name
        }
        assertTrue("żadne jądro GPU nie zgadza się z CPU", good.isNotEmpty())
    }
}
