package app.retrofilm.pipeline

import android.graphics.Bitmap
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import app.retrofilm.perf.SaveTrace
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        private val failAfter: Int = Int.MAX_VALUE
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

    @After
    fun tearDown() {
        SaveBackend.reset()
        SaveBackend.gpuEnabled = true
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
        assertTrue(
            events.toString(),
            events.any {
                it.startsWith("save_backend lut=cpu reason=probe_failed(bad:diff=3.00,bad2:")
            }
        )
    }

    @Test
    fun runtimeErrorSwitchesToCpuWithIdenticalOutput() {
        val ref = cpuReference()
        SaveBackend.candidates = { listOf(FakeKernel("flaky", failAfter = 1)) }
        assertArrayEquals(ref, save())
        assertTrue(events.toString(), events.any { it == "save_backend_fallback lut=cpu reason=IllegalStateException" })
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
    }
}
