package app.retrofilm.pipeline

import android.os.Build
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.AgslPlaneLut
import app.retrofilm.core.engine.gpu.GlesPlaneLut
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import app.retrofilm.perf.SaveTrace
import kotlin.math.abs

/**
 * Wybór backendu LUT-a przy zapisie. [gpuEnabled] (Diagnostyka „Zapis na GPU”, L: domyślnie wyłączony) włącza GPU;
 * kandydaci: AGSL F16 (API 33+), AGSL F16 z liniowym buforem, GLES3 float, w końcu CPU.
 *
 * - Próbka (siatka barw vs [LutStage] na CPU, tolerancja [TOLERANCE]) raz na proces – wynik, czasy i powody odrzuceń
 *   są zapamiętane i logowane przy każdym zapisie (`save_backend_probe`), bez ponownego liczenia (L).
 * - Pomiar (L): pierwszy pas zapisu liczony na GPU i na CPU; GPU zostaje tylko, gdy jest szybszy od CPU podzielonego
 *   przez liczbę wątków pasów (CPU liczy pasy równolegle, GPU szeregowo). Decyzja na proces (`save_backend_bench`).
 * - Błąd w trakcie zapisu → reszta pasów i kolejne zapisy na CPU.
 */
object SaveBackend {
    @Volatile
    var gpuEnabled: Boolean = false

    /** O: po „Invalid format specified 22” nie próbuj AGSL do końca procesu (i po restarcie, jeśli ustawione). */
    @Volatile
    var skipAgsl: Boolean = false

    /** Opcjonalny zapis trwałej blokady AGSL (DataStore z UI). */
    var onSkipAgsl: ((Boolean) -> Unit)? = null

    /** Do testów: lista kandydatów zamiast prawdziwego GPU. */
    @Volatile
    internal var candidates: () -> List<PlaneLutKernel> = ::deviceKernels

    /** Do testów: liczba wątków pasów CPU, z którą porównywany jest czas GPU. */
    @Volatile
    internal var cpuThreads: () -> Int = { Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS) }

    private var chosen: PlaneLutKernel? = null
    private var probed = false
    private var reason = "not_probed"
    private val probeNotes = mutableListOf<String>()
    private var bench: String? = null
    private var benchGpu: Boolean? = null

    internal const val TOLERANCE = 0.75f
    private const val PROBE_STEPS = 17
    private const val MAX_THREADS = 8
    private const val NS_MS = 1_000_000.0

    /** Etap LUT-a dla zapisu: GPU gdy włączone, sprawdzone i szybsze, inaczej [cpu]. Loguje backend i powód. */
    @Synchronized
    @Suppress("ReturnCount")
    internal fun stage(lut: Lut, intensity: Float, cpu: LutStage, wide: Boolean, trace: SaveTrace?): PlaneStage {
        // bez śladu zapisu = podgląd: zostaje na CPU (małe obrazy, narzut odczytu z GPU nie zwraca się)
        if (trace == null) return cpu
        if (!gpuEnabled || wide) {
            trace.event("backend", "lut=cpu reason=${if (wide) "wide_gamut" else "disabled"}")
            return cpu
        }
        val cached = probed
        val kernel = pick(lut, intensity, cpu)
        probeNotes.forEach { trace.event("backend_probe", if (cached) "$it cached=true" else it) }
        bench?.let { trace.event("backend_bench", "$it cached=true") }
        if (kernel == null || benchGpu == false) {
            val why = if (kernel == null) reason else "bench_cpu_faster"
            trace.event("backend", "lut=cpu reason=$why")
            return cpu
        }
        trace.event("backend", "lut=${kernel.name}")
        return GpuLutStage(kernel, lut, intensity, cpu, benchGpu == null, cpuThreads(), trace) { e ->
            trace.event("backend_fallback", "lut=cpu reason=${e.javaClass.simpleName}: ${e.message}")
            demote(kernel, e)
        }
    }

    @Synchronized
    internal fun benchDone(useGpu: Boolean, note: String) {
        benchGpu = useGpu
        bench = note
    }

    @Synchronized
    internal fun reset() {
        chosen?.close()
        chosen = null
        probed = false
        reason = "not_probed"
        probeNotes.clear()
        bench = null
        benchGpu = null
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
        val list = runCatching(candidates)
        list.exceptionOrNull()?.let { probeNotes += "name=none error=${describe(it)}" }
        val names = mutableListOf<String>()
        for (k in list.getOrDefault(emptyList())) {
            val t0 = System.nanoTime()
            val err = runCatching { probeError(k, lut, intensity, cpu) }
            val ms = "%.0f".format((System.nanoTime() - t0) / NS_MS)
            val e = err.getOrNull()
            if (e != null && e <= TOLERANCE) {
                probeNotes += "name=${k.name} result=ok diff=%.3f tol=$TOLERANCE probe_ms=$ms".format(e)
                chosen = k
                return k
            }
            val errText = err.exceptionOrNull()?.let { describe(it) }.orEmpty()
            if ("agsl" in k.name) noteAgslRejected(errText)
            probeNotes += if (e != null) {
                "name=${k.name} result=rejected diff=%.3f tol=$TOLERANCE probe_ms=$ms".format(e)
            } else {
                "name=${k.name} result=error error=$errText probe_ms=$ms"
            }
            names += k.name
            runCatching { k.close() }
        }
        reason = if (names.isEmpty()) "no_gpu" else "probe_failed(${names.joinToString(",")})"
        return null
    }

    private fun describe(e: Throwable?): String =
        generateSequence(e) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
            .replace(' ', '_').take(MAX_NOTE)

    private const val MAX_NOTE = 300

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
        if (!skipAgsl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(AgslPlaneLut(linearOut = false))
            add(AgslPlaneLut(linearOut = true))
        }
        add(GlesPlaneLut())
    }

    internal fun noteAgslRejected(error: String) {
        if (skipAgsl) return
        if ("Invalid format" in error || "format specified 22" in error) {
            skipAgsl = true
            onSkipAgsl?.invoke(true)
            probeNotes += "name=agsl result=skipped reason=format_22_cached"
        }
    }
}

/**
 * LUT na GPU dla pasa; wywołania szeregowane (jeden kontekst), wyjątek = ten i kolejne pasy na CPU. Gdy [measure],
 * pierwszy pas liczony na obu backendach (wynik z CPU), a szybszy zostaje do końca procesu.
 */
@Suppress("LongParameterList")
internal class GpuLutStage(
    private val kernel: PlaneLutKernel,
    private val lut: Lut,
    private val intensity: Float,
    private val cpu: LutStage,
    measure: Boolean,
    private val threads: Int,
    private val trace: SaveTrace,
    private val onFailure: (Throwable) -> Unit
) : PlaneStage {
    @Volatile
    private var failed = false

    @Volatile
    private var measuring = measure

    @Volatile
    private var useCpu = false

    init {
        kernel.times?.reset()
    }

    override fun apply(planes: Planes, from: Int, count: Int) {
        if (!failed && !useCpu) {
            val ok = synchronized(kernel) {
                when {
                    failed || useCpu -> false

                    measuring -> {
                        measuring = false
                        benchmark(planes, from, count)
                    }

                    else -> gpu(planes.r, planes.g, planes.b, from, count)
                }
            }
            if (ok) return
        }
        cpu.apply(planes, from, count)
    }

    /** Pas na GPU (kopia) i na CPU (w miejscu); true = wynik już w [planes]. */
    private fun benchmark(planes: Planes, from: Int, count: Int): Boolean {
        val r = planes.r.copyOfRange(from, from + count)
        val g = planes.g.copyOfRange(from, from + count)
        val b = planes.b.copyOfRange(from, from + count)
        var g0 = 0L
        // rozgrzewka (kontekst, LUT 3D), potem pomiar całego pasa
        val ran = gpu(r, g, b, 0, minOf(count, WARMUP)) && run {
            g0 = System.nanoTime()
            gpu(r, g, b, 0, count)
        }
        if (!ran) return false
        val gpuNs = System.nanoTime() - g0
        val c0 = System.nanoTime()
        cpu.apply(planes, from, count)
        val cpuNs = System.nanoTime() - c0
        val useGpu = gpuNs * threads < cpuNs
        val note = "gpu_ms=%.1f cpu_ms=%.1f threads=$threads px=$count use=%s".format(
            gpuNs / NS_MS,
            cpuNs / NS_MS,
            if (useGpu) kernel.name else "cpu"
        )
        trace.event("backend_bench", note)
        SaveBackend.benchDone(useGpu, note)
        if (!useGpu) useCpu = true
        return true
    }

    private fun gpu(r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int): Boolean = try {
        kernel.apply(r, g, b, from, count, lut, intensity)
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        failed = true
        onFailure(e)
        false
    }

    /** Podział czasu GPU w tym zapisie (`save_lut_gpu_split`). */
    fun report(trace: SaveTrace?) {
        kernel.times?.let { trace?.event("lut_gpu_split", "backend=${kernel.name} ${it.note()}") }
    }

    private companion object {
        const val WARMUP = 1024
        const val NS_MS = 1_000_000.0
    }
}
