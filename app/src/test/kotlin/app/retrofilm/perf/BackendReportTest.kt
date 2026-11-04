package app.retrofilm.perf

import android.graphics.Bitmap
import app.retrofilm.core.engine.CpuLutEngine
import app.retrofilm.core.engine.FallbackLutEngine
import app.retrofilm.core.engine.Interpolation
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Log czasów pokazuje faktycznie użyty backend (nie klasę opakowania) i powód spadku do CPU. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackendReportTest {
    private class Broken(private val fail: Boolean) : LutEngine {
        override val name = "agsl-tetra"

        override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap {
            check(!fail) { "brak shadera" }
            return src
        }
    }

    private val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun reportsPrimaryWhileItWorks() = runBlocking {
        val e = FallbackLutEngine(Broken(fail = false), CpuLutEngine(Interpolation.TETRAHEDRAL))
        assertEquals("agsl-tetra", e.activeBackend)
        e.apply(bmp, Lut.identity(2), 1f)
        assertEquals("agsl-tetra", e.activeBackend)
        assertNull(e.fallbackReason)
    }

    @Test
    fun reportsCpuAndReasonAfterFailure() = runBlocking {
        val e = FallbackLutEngine(Broken(fail = true), CpuLutEngine(Interpolation.TETRAHEDRAL))
        e.apply(bmp, Lut.identity(2), 1f)
        assertEquals("cpu-tetra", e.activeBackend)
        assertTrue(e.fallbackReason!!.contains("agsl-tetra"))
        assertTrue(e.fallbackReason!!.contains("IllegalStateException: brak shadera"))
    }

    @Test
    fun plainEngineReportsItsName() {
        val e: LutEngine = CpuLutEngine()
        assertEquals("cpu", e.activeBackend)
        assertNull(e.fallbackReason)
    }
}
