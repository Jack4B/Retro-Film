package app.retrofilm.pipeline

import android.graphics.Bitmap
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.KernelTimes
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import app.retrofilm.perf.SaveTrace
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** K: wybór backendu LUT-a przy zapisie – próbka vs CPU, awaryjne CPU, logowanie backendu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveBackendTest {
    private val lut = Lut.generate(17) { r, g, b -> Triple(g * 0.9f + 0.05f, (r + b) / 2f, 1f - r * 0.8f) }
    private val events = mutableListOf<String>()
    private val trace = SaveTrace(sink = { n, _, note -> events += "$n $note" }, usedBytes = { 0L })

    /** „GPU” liczące tak jak CPU (opcjonalnie z błędem lub wyjątkiem po [failAfter] wywołaniach). */
    private class FakeKernel(
        override val name: String,
        private val offset: Float = 0f,
        private val failAfter: Int = Int.MAX_VALUE,
        private val delayMs: Long = 0
    ) : PlaneLutKernel {
        var calls = 0

        override fun apply(
            r: FloatArray,
            g: FloatArray,
            b: FloatArray,
            from: Int,
            count: Int,
            lut: Lut,
            intensity: Float
        ) {
            if (calls++ >= failAfter) error("gpu lost")
            if (delayMs > 0) Thread.sleep(delayMs)
            val p = Planes(r, g, b)
            LutStage(lut, intensity, false).apply(p, from, count)
            for (i in from until from + count) r[i] = (r[i] + offset).coerceIn(0f, 255f)
        }
    }

    private fun photo() = Bitmap.createBitmap(
        IntArray(64 * 48) { i -> (0xFF shl 24) or ((i * 7 % 256) shl 16) or ((i * 3 % 256) shl 8) or (i % 256) },
        64,
        48,
        Bitmap.Config.ARGB_8888
    )

    private fun px(b: Bitmap) = IntArray(b.width * b.height).also {
        b.getPixels(it, 0, b.width, 0, 0, b.width, b.height)
    }

    private fun save(t: SaveTrace? = trace) =
        px(FloatExport.look(photo(), lut, 0.8f, Adjustments.None, OutputSpace.Srgb, trace = t))

    @Before
    fun setUp() {
        SaveBackend.reset()
        SaveBackend.gpuEnabled = true
        SaveBackend.cpuThreads = { 0 } // 0 = pomiar zawsze wybiera GPU (atrapa liczy tyle co CPU)
    }

    @After
    fun tearDown() {
        SaveBackend.reset()
        SaveBackend.gpuEnabled = false
        SaveBackend.cpuThreads = { 1 }
    }

    private fun cpuReference(): IntArray {
        SaveBackend.gpuEnabled = false
        return save().also {
            SaveBackend.gpuEnabled = true
            events.clear()
        }
    }

    @Test
    fun matchingKernelIsChosenAndLogged() {
        val ref = cpuReference()
        val k = FakeKernel("gpu-fake")
        SaveBackend.candidates = { listOf(k) }
        val out = save()
        assertArrayEquals(ref, out)
        assertTrue(events.toString(), events.any { it == "save_backend lut=gpu-fake" })
        assertTrue(k.calls > 1)
    }

    @Test
    fun kernelFailingProbeFallsBackToNextThenCpu() {
        val ref = cpuReference()
        SaveBackend.candidates = { listOf(FakeKernel("bad", offset = 3f), FakeKernel("bad2", offset = 1f)) }
        assertArrayEquals(ref, save())
        assertTrue(events.toString(), events.any { it == "save_backend lut=cpu reason=probe_failed(bad,bad2)" })
        assertTrue(
            events.toString(),
            events.any { it.startsWith("save_backend_probe name=bad result=rejected diff=3.000 tol=0.75 probe_ms=") }
        )
        assertTrue(
            events.toString(),
            events.any {
                it.startsWith("save_backend_probe name=bad2 result=rejected diff=1.000")
            }
        )
    }

    @Test
    fun runtimeErrorSwitchesToCpuWithIdenticalOutput() {
        val ref = cpuReference()
        SaveBackend.candidates = { listOf(FakeKernel("flaky", failAfter = 1)) }
        assertArrayEquals(ref, save())
        assertTrue(
            events.toString(),
            events.count { it == "save_backend_fallback lut=cpu reason=IllegalStateException: gpu lost" } == 1
        )
        events.clear()
        save()
        assertTrue(events.toString(), events.any { it == "save_backend lut=cpu reason=runtime_IllegalStateException" })
    }

    @Test
    fun disabledToggleAndPreviewStayOnCpu() {
        val k = FakeKernel("gpu-fake")
        SaveBackend.candidates = { listOf(k) }
        SaveBackend.gpuEnabled = false
        save()
        assertEquals(listOf("save_backend lut=cpu reason=disabled"), events.filter { it.startsWith("save_backend") })
        SaveBackend.gpuEnabled = true
        save(t = null)
        assertEquals(0, k.calls)
    }

    @Test
    fun throwingCandidateListMeansCpu() {
        SaveBackend.candidates = { error("no egl") }
        save()
        assertTrue(events.toString(), events.any { it == "save_backend lut=cpu reason=no_gpu" })
        assertTrue(
            events.toString(),
            events.any {
                it.startsWith("save_backend_probe name=none error=IllegalStateException:_no_egl")
            }
        )
    }

    @Test
    fun gpuToggleIsOffByDefault() {
        tearDown()
        assertEquals(false, SaveBackend.gpuEnabled)
        val k = FakeKernel("gpu-fake")
        SaveBackend.candidates = { listOf(k) }
        save()
        assertEquals(0, k.calls)
    }

    @Test
    fun probeRunsOncePerProcessAndIsReportedAsCached() {
        val k = FakeKernel("gpu-fake")
        SaveBackend.candidates = { listOf(k) }
        save()
        val after = k.calls
        events.clear()
        save()
        assertTrue(
            events.toString(),
            events.any { it.startsWith("save_backend_probe name=gpu-fake result=ok") && it.endsWith("cached=true") }
        )
        // drugi zapis: bez próbki i bez pomiaru – tylko pasy (tu jeden pas)
        assertEquals(after + 1, k.calls)
    }

    @Test
    fun benchmarkPicksCpuWhenGpuSlowerAndRemembersIt() {
        SaveBackend.cpuThreads = { 1 }
        val ref = cpuReference()
        val k = FakeKernel("slow-gpu", delayMs = 60)
        SaveBackend.candidates = { listOf(k) }
        assertArrayEquals(ref, save())
        assertTrue(
            events.toString(),
            events.any {
                it.startsWith("save_backend_bench gpu_ms=") && it.endsWith("use=cpu")
            }
        )
        val calls = k.calls
        events.clear()
        assertArrayEquals(ref, save())
        assertEquals(calls, k.calls)
        assertTrue(events.toString(), events.any { it == "save_backend lut=cpu reason=bench_cpu_faster" })
    }

    @Test
    fun benchmarkKeepsGpuWhenFasterAndLogsSplit() {
        val ref = cpuReference()
        SaveBackend.candidates = { listOf(TimedKernel()) }
        assertArrayEquals(ref, save())
        assertTrue(events.toString(), events.any { it.startsWith("save_backend_bench") && it.endsWith("use=timed") })
        val split = Regex("save_lut_gpu_split backend=timed upload_ms=\\d+ draw_ms=\\d+ readback_ms=\\d+ calls=\\d+")
        assertTrue(events.toString(), events.any { it.matches(split) })
    }

    private class TimedKernel : PlaneLutKernel {
        override val name = "timed"
        override val times = KernelTimes()

        override fun apply(
            r: FloatArray,
            g: FloatArray,
            b: FloatArray,
            from: Int,
            count: Int,
            lut: Lut,
            intensity: Float
        ) {
            LutStage(lut, intensity, false).apply(Planes(r, g, b), from, count)
            times.calls++
        }
    }
}
