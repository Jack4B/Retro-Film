package app.retrofilm.core.engine

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/** Wybór backendu: AGSL (API 33+) → GLES 3.0 z teksturą 3D → CPU. Backendy GPU spadają do CPU przy błędzie. */
object LutEngineFactory {
    private const val AGSL_MIN_API = 33

    enum class Backend { AUTO, AGSL, GLES, CPU }

    fun create(backend: Backend = Backend.AUTO): LutEngine = when (backend) {
        Backend.CPU -> CpuLutEngine()

        Backend.AGSL -> withCpuFallback(createAgsl())

        Backend.GLES -> withCpuFallback(GlesLutEngine())

        Backend.AUTO -> when {
            Build.VERSION.SDK_INT >= AGSL_MIN_API -> withCpuFallback(createAgsl())
            GlesLutEngine.isSupported() -> withCpuFallback(GlesLutEngine())
            else -> CpuLutEngine()
        }
    }

    private fun createAgsl(): LutEngine {
        check(Build.VERSION.SDK_INT >= AGSL_MIN_API) { "AGSL wymaga API $AGSL_MIN_API" }
        return AgslLutEngine()
    }

    private fun withCpuFallback(primary: LutEngine): LutEngine = FallbackLutEngine(primary, CpuLutEngine())
}

private class FallbackLutEngine(private val primary: LutEngine, private val fallback: LutEngine) : LutEngine {
    override val name: String get() = primary.name

    @Suppress("TooGenericExceptionCaught")
    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = try {
        primary.apply(src, lut, intensity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w("LutEngine", "Backend ${primary.name} zawiódł, używam CPU", e)
        fallback.apply(src, lut, intensity)
    }
}
