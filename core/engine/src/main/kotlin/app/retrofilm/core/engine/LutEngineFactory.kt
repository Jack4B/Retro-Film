package app.retrofilm.core.engine

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/** Wybór backendu: AGSL (API 33+) → GLES 3.0 z teksturą 3D → CPU. Backendy GPU spadają do CPU przy błędzie. */
object LutEngineFactory {
    private const val AGSL_MIN_API = 33

    enum class Backend { AUTO, AGSL, GLES, CPU }

    /**
     * [interpolation] dotyczy AGSL i CPU; GLES zawsze używa sprzętowej trójliniowej
     * (przy TETRAHEDRAL tryb AUTO pomija GLES i spada do CPU).
     */
    fun create(backend: Backend = Backend.AUTO, interpolation: Interpolation = Interpolation.TRILINEAR): LutEngine =
        when (backend) {
            Backend.CPU -> CpuLutEngine(interpolation)

            Backend.AGSL -> withCpuFallback(createAgsl(interpolation), interpolation)

            Backend.GLES -> withCpuFallback(GlesLutEngine(), interpolation)

            Backend.AUTO -> when {
                Build.VERSION.SDK_INT >= AGSL_MIN_API -> withCpuFallback(createAgsl(interpolation), interpolation)

                interpolation == Interpolation.TRILINEAR && GlesLutEngine.isSupported() ->
                    withCpuFallback(GlesLutEngine(), interpolation)

                else -> CpuLutEngine(interpolation)
            }
        }

    /** Backend bez awaryjnego CPU – do testów porównawczych (błąd backendu nie jest maskowany). */
    fun createStrict(backend: Backend, interpolation: Interpolation = Interpolation.TRILINEAR): LutEngine =
        when (backend) {
            Backend.AGSL -> createAgsl(interpolation)
            Backend.GLES -> GlesLutEngine()
            Backend.CPU -> CpuLutEngine(interpolation)
            Backend.AUTO -> create(Backend.AUTO, interpolation)
        }

    private fun createAgsl(interpolation: Interpolation): LutEngine {
        check(Build.VERSION.SDK_INT >= AGSL_MIN_API) { "AGSL wymaga API $AGSL_MIN_API" }
        return AgslLutEngine(interpolation)
    }

    private fun withCpuFallback(primary: LutEngine, interpolation: Interpolation): LutEngine =
        FallbackLutEngine(primary, CpuLutEngine(interpolation))
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
