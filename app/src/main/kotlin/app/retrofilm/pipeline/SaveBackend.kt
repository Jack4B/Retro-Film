package app.retrofilm.pipeline

import android.os.Build
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.AgslPlaneLut
import app.retrofilm.core.engine.gpu.GlesPlaneLut
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import app.retrofilm.perf.SaveTrace
import kotlin.math.abs

/**
 * K: wybór backendu LUT-a przy zapisie. [gpuEnabled] (Diagnostyka „Zapis na GPU”) włącza GPU; kolejność kandydatów:
 * AGSL F16 (API 33+), AGSL F16 z liniowym buforem, GLES3 float, w końcu CPU. Każdy kandydat przechodzi próbkę
 * (siatka barw vs [LutStage] na CPU, tolerancja [TOLERANCE] w skali 0..255) – zły wynik albo wyjątek = następny.
 * Wybór jest zapamiętywany na proces; błąd w trakcie zapisu przełącza na CPU do końca procesu.
 */
object SaveBackend {
    @Volatile
    var gpuEnabled: Boolean = true

    /** Do testów: lista kandydatów zamiast prawdziwego GPU. */
    @Volatile
    internal var candidates: () -> List<PlaneLutKernel> = ::deviceKernels

    private var chosen: PlaneLutKernel? = null
    private var probed = false
    private var reason = "not_probed"

    internal const val TOLERANCE = 0.75f
    private const val PROBE_STEPS = 17

    /** Etap LUT-a dla zapisu: GPU gdy włączone i sprawdzone, inaczej [cpu]. Loguje wybrany backend. */
    @Synchronized
    @Suppress("ReturnCount")
    internal fun stage(lut: Lut, intensity: Float, cpu: LutStage, wide: Boolean, trace: SaveTrace?): PlaneStage {
        // bez śladu zapisu = podgląd: zostaje na CPU (małe obrazy, narzut odczytu z GPU nie zwraca się)
        if (trace == null) return cpu
        if (!gpuEnabled || wide) {
            trace?.event("backend", "lut=cpu reason=${if (wide) "wide_gamut" else "disabled"}")
            return cpu
        }
        val kernel = pick(lut, intensity, cpu)
        if (kernel == null) {
            trace?.event("backend", "lut=cpu reason=$reason")
            return cpu
        }
        trace?.event("backend", "lut=${kernel.name}")
        return GpuLutStage(kernel, lut, intensity, cpu) { e ->
            trace?.event("backend_fallback", "lut=cpu reason=${e.javaClass.simpleName}")
            demote(kernel, e)
        }
    }

    @Synchronized
    internal fun reset() {
        chosen?.close()
        chosen = null
        probed = false
        reason = "not_probed"
    }

    @Synchronized
    private fun demote(kernel: PlaneLutKernel, e: Throwable) {
        if (chosen === kernel) {
            runCatching { kernel.close() }
            chosen = null
            reason = "runtime_${e.javaClass.simpleName}"
        }
    }

    @Suppress("ReturnCount")
    private fun pick(lut: Lut, intensity: Float, cpu: LutStage): PlaneLutKernel? {
        if (probed) return chosen
        probed = true
        val notes = mutableListOf<String>()
        for (k in runCatching(candidates).getOrDefault(emptyList())) {
            val err = runCatching { probeError(k, lut, intensity, cpu) }
            val e = err.getOrNull()
            if (e != null && e <= TOLERANCE) {
                chosen = k
                return k
            }
            notes += "${k.name}:" + (e?.let { "diff=%.2f".format(it) } ?: err.exceptionOrNull()?.javaClass?.simpleName)
            runCatching { k.close() }
        }
        reason = if (notes.isEmpty()) "no_gpu" else "probe_failed(${notes.joinToString(",")})"
        return null
    }

    /** Największa różnica kanału (0..255) między [k] a CPU na siatce barw i rampach. */
    @Suppress("MagicNumber") // przesunięcia próbek między węzłami
    internal fun probeError(k: PlaneLutKernel, lut: Lut, intensity: Float, cpu: LutStage): Float {
        val n = PROBE_STEPS * PROBE_STEPS * PROBE_STEPS
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        var i = 0
        val step = 255f / (PROBE_STEPS - 1)
        for (bb in 0 until PROBE_STEPS) {
            for (gg in 0 until PROBE_STEPS) {
                for (rr in 0 until PROBE_STEPS) {
                    // lekkie przesunięcie, żeby trafiać też między węzły
                    r[i] = (rr * step + (i % 7) * 0.37f).coerceAtMost(255f)
                    g[i] = (gg * step + (i % 5) * 0.53f).coerceAtMost(255f)
                    b[i] = (bb * step + (i % 3) * 0.71f).coerceAtMost(255f)
                    i++
                }
            }
        }
        val ref = Planes(r.copyOf(), g.copyOf(), b.copyOf())
        cpu.apply(ref, 0, n)
        k.apply(r, g, b, 0, n, lut, intensity)
        var max = 0f
        for (j in 0 until n) {
            max = maxOf(max, abs(r[j] - ref.r[j]), abs(g[j] - ref.g[j]), abs(b[j] - ref.b[j]))
        }
        return if (max.isNaN()) Float.MAX_VALUE else max
    }

    private fun deviceKernels(): List<PlaneLutKernel> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(AgslPlaneLut(linearOut = false))
            add(AgslPlaneLut(linearOut = true))
        }
        add(GlesPlaneLut())
    }
}

/** LUT na GPU dla pasa; wywołania szeregowane (jeden kontekst), wyjątek = ten i kolejne pasy na CPU. */
internal class GpuLutStage(
    private val kernel: PlaneLutKernel,
    private val lut: Lut,
    private val intensity: Float,
    private val cpu: LutStage,
    private val onFailure: (Throwable) -> Unit
) : PlaneStage {
    @Volatile
    private var failed = false

    override fun apply(planes: Planes, from: Int, count: Int) {
        if (!failed) {
            val ok = synchronized(kernel) {
                if (failed) {
                    false
                } else {
                    try {
                        kernel.apply(planes.r, planes.g, planes.b, from, count, lut, intensity)
                        true
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        failed = true
                        onFailure(e)
                        false
                    }
                }
            }
            if (ok) return
        }
        cpu.apply(planes, from, count)
    }
}
