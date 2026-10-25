package app.retrofilm.core.engine

import android.graphics.Bitmap
import android.os.Build
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Porównanie backendów GPU z referencją CPU (trójliniowa) – wspólne dla `GpuVsCpuTest`
 * i ekranu samotestu w buildzie debug. Każdy backend wymuszany jawnie (bez awaryjnego CPU).
 */
object BackendComparison {
    const val TOLERANCE = 2
    const val WIDTH = 512
    const val HEIGHT = 384
    private const val CHANNELS = 3
    private const val BYTE = 0xFF
    private const val SHIFT_R = 16
    private const val BITS = 8

    data class LutResult(val lut: String, val max: IntArray, val mean: DoubleArray) {
        val maxAll: Int get() = max.max()
    }

    /** Czasy: render obrazu 12 MP oraz wgranie LUT-a (N → ms, render 16×16 na zimnym silniku). */
    data class Timing(val render12MpMs: Long, val uploadMs: Map<Int, Long>)

    data class BackendResult(
        val backend: String,
        val error: String?,
        val luts: List<LutResult>,
        val timing: Timing? = null
    ) {
        val maxDiff: Int get() = luts.maxOfOrNull { it.maxAll } ?: -1
        val pass: Boolean get() = error == null && luts.isNotEmpty() && maxDiff <= TOLERANCE
    }

    data class Report(val gpu: String, val backends: List<BackendResult>) {
        val pass: Boolean get() = backends.isNotEmpty() && backends.all { it.pass }

        fun toJson(): JSONObject = JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("gpu_renderer", gpu)
            .put("tolerance", TOLERANCE)
            .put("width", WIDTH)
            .put("height", HEIGHT)
            .put("verdict", if (pass) "PASS" else "FAIL")
            .put(
                "backends",
                JSONArray(
                    backends.map { b ->
                        JSONObject().put("backend", b.backend).put("max_diff", b.maxDiff)
                            .put("verdict", if (b.pass) "PASS" else "FAIL").put("error", b.error ?: JSONObject.NULL)
                            .put("render_12mp_ms", b.timing?.render12MpMs ?: JSONObject.NULL)
                            .put(
                                "lut_upload_ms",
                                b.timing?.let { t ->
                                    JSONObject().apply { t.uploadMs.forEach { (n, ms) -> put(n.toString(), ms) } }
                                } ?: JSONObject.NULL
                            )
                            .put(
                                "luts",
                                JSONArray(
                                    b.luts.map { l ->
                                        JSONObject().put("lut", l.lut).put("max_diff", l.maxAll)
                                            .put("max_r", l.max[0]).put("max_g", l.max[1]).put("max_b", l.max[2])
                                            .put("mean_r", l.mean[0]).put("mean_g", l.mean[1]).put("mean_b", l.mean[2])
                                    }
                                )
                            )
                    }
                )
            )
    }

    /** Backendy dostępne na urządzeniu (AGSL tylko API 33+). */
    fun available(): List<LutEngineFactory.Backend> = buildList {
        if (Build.VERSION.SDK_INT >= AGSL_MIN_API) add(LutEngineFactory.Backend.AGSL)
        add(LutEngineFactory.Backend.GLES)
        add(LutEngineFactory.Backend.CPU)
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun run(
        backends: List<LutEngineFactory.Backend>,
        luts: List<Pair<String, Lut>>,
        withTiming: Boolean = false
    ): Report {
        val src = testImage()
        val cpu = CpuLutEngine()
        val refs = luts.map { (_, lut) -> cpu.apply(src, lut, 1f) }
        val results = backends.map { backend ->
            try {
                val engine = LutEngineFactory.createStrict(backend)
                BackendResult(
                    engine.name,
                    null,
                    luts.mapIndexed { i, (id, lut) -> diff(id, refs[i], engine.apply(src, lut, 1f)) },
                    if (withTiming) measure(backend, luts.firstOrNull()?.second ?: Lut.identity()) else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                BackendResult(backend.name.lowercase(), "${e.javaClass.simpleName}: ${e.message}", emptyList())
            }
        }
        return Report(GlesLutEngine.gpuInfo() ?: "GLES 3 niedostępny", results)
    }

    /** Rozmiary siatek do pomiaru czasu wgrywania LUT-a. */
    val UPLOAD_SIZES = listOf(33, 65, 144)
    private const val MP12_W = 4000
    private const val MP12_H = 3000
    private const val SMALL = 16

    /** Mierzy czas renderu 12 MP i wgrania syntetycznych LUT-ów tożsamościowych 33/65/144. */
    suspend fun measure(backend: LutEngineFactory.Backend, lut: Lut): Timing {
        val small = Bitmap.createBitmap(SMALL, SMALL, Bitmap.Config.ARGB_8888)
        val upload = UPLOAD_SIZES.associateWith { n ->
            val id = Lut.identity(n)
            val engine = LutEngineFactory.createStrict(backend)
            timed { engine.apply(small, id, 1f) }
        }
        val big = Bitmap.createBitmap(MP12_W, MP12_H, Bitmap.Config.ARGB_8888).apply { eraseColor(MID_GRAY) }
        val engine = LutEngineFactory.createStrict(backend)
        engine.apply(small, lut, 1f) // rozgrzanie + wgranie LUT-a
        val render = timed { engine.apply(big, lut, 1f).recycle() }
        big.recycle()
        return Timing(render, upload)
    }

    private const val MID_GRAY = -0x7f7f80
    private const val NANOS_PER_MS = 1_000_000L

    private inline fun timed(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / NANOS_PER_MS
    }

    fun diff(id: String, a: Bitmap, b: Bitmap): LutResult {
        val n = WIDTH * HEIGHT
        val pa = IntArray(n).also { a.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
        val pb = IntArray(n).also { b.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
        val max = IntArray(CHANNELS)
        val sum = LongArray(CHANNELS)
        for (i in 0 until n) {
            for (c in 0 until CHANNELS) {
                val shift = SHIFT_R - c * BITS
                val d = abs((pa[i] shr shift and BYTE) - (pb[i] shr shift and BYTE))
                if (d > max[c]) max[c] = d
                sum[c] += d.toLong()
            }
        }
        return LutResult(id, max, DoubleArray(CHANNELS) { sum[it].toDouble() / n })
    }

    @Suppress("MagicNumber")
    fun testImage(): Bitmap {
        val px = IntArray(WIDTH * HEIGHT) { i ->
            val x = i % WIDTH
            val y = i / WIDTH
            val r = x * 255 / (WIDTH - 1)
            val g = y * 255 / (HEIGHT - 1)
            val b = (x * 7 + y * 13) and BYTE
            (BYTE shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }

    /** Syntetyczne LUT-y (gdy brak katalogu w assetach). */
    @Suppress("MagicNumber")
    fun syntheticLuts(): List<Pair<String, Lut>> = listOf(
        "synthetic/film_like" to Lut.generate(Lut.DEFAULT_SIZE) { r, g, b ->
            Triple(
                (r * 0.9f + 0.08f * sin(g * 3f) + 0.05f).coerceIn(0f, 1f),
                (g * 0.85f + 0.1f * r * b).coerceIn(0f, 1f),
                (b * 0.8f + 0.1f * cos(r * 2f)).coerceIn(0f, 1f)
            )
        },
        "synthetic/invert" to Lut.generate(Lut.DEFAULT_SIZE) { r, g, b -> Triple(1 - r, 1 - g, 1 - b) },
        "synthetic/bw" to Lut.generate(Lut.DEFAULT_SIZE) { r, g, b ->
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            Triple(y, y, y)
        }
    )

    private const val AGSL_MIN_API = 33
}
