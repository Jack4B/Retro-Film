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
     * (przy TETRAHEDRAL tryb AUTO pomija GLES i spada do CPU). [defaultAuto] = AGSL czworościenny (API 33+),
     * poniżej – GLES/CPU trójliniowe; backendy GPU z ditheringiem TPDF.
     */
    fun defaultAuto(): LutEngine = if (Build.VERSION.SDK_INT >= AGSL_MIN_API) {
        withCpuFallback(createAgsl(Interpolation.TETRAHEDRAL), Interpolation.TETRAHEDRAL)
    } else {
        create(Backend.AUTO, Interpolation.TRILINEAR)
    }

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

    /**
     * Backend bez awaryjnego CPU i bez ditheru – do testów porównawczych (błąd backendu nie jest maskowany,
     * wynik deterministyczny względem CPU).
     */
    fun createStrict(backend: Backend, interpolation: Interpolation = Interpolation.TRILINEAR): LutEngine =
        when (backend) {
            Backend.AGSL -> {
                check(Build.VERSION.SDK_INT >= AGSL_MIN_API) { "AGSL wymaga API $AGSL_MIN_API" }
                AgslLutEngine(interpolation, dither = false)
            }

            Backend.GLES -> GlesLutEngine(dither = false)

            Backend.CPU -> CpuLutEngine(interpolation)

            Backend.AUTO -> create(Backend.AUTO, interpolation)
        }

    private fun createAgsl(interpolation: Interpolation): LutEngine {
        check(Build.VERSION.SDK_INT >= AGSL_MIN_API) { "AGSL wymaga API $AGSL_MIN_API" }
        return AgslLutEngine(interpolation, dither = true)
    }

    private fun withCpuFallback(primary: LutEngine, interpolation: Interpolation): LutEngine =
        FallbackLutEngine(primary, CpuLutEngine(interpolation))
}

/**
 * Backend GPU z awaryjnym CPU. Po pierwszej awarii zostaje na CPU (ponowne próby GPU przy każdej klatce tylko by
 * dokładały czas wyjątku); [activeBackend] i [fallbackReason] mówią logowi czasów, co naprawdę liczy.
 */
class FallbackLutEngine(private val primary: LutEngine, private val fallback: LutEngine) : LutEngine {
    override val name: String get() = primary.name

    @Volatile override var fallbackReason: String? = null
        private set

    override val activeBackend: String get() = if (fallbackReason == null) primary.activeBackend else fallback.name

    @Suppress("TooGenericExceptionCaught")
    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap {
        if (fallbackReason != null) return fallback.apply(src, lut, intensity)
        return try {
            primary.apply(src, lut, intensity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            fallbackReason = "${primary.name}: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            Log.w("LutEngine", "Backend ${primary.name} zawiódł, używam CPU", e)
            fallback.apply(src, lut, intensity)
        }
    }

    override fun warm(lut: Lut) = primary.warm(lut)
}
