package app.retrofilm.core.engine.gpu

import app.retrofilm.core.engine.Lut

/**
 * K: film (LUT + siła) liczony na GPU na kanałach float zapisu (wartości 0..255, bez kwantyzacji do 8 bitów –
 * dithering i kwantyzację robi dalej potok CPU). Operacja punktowa, więc kafle nie potrzebują marginesu (halo).
 * Wynik ma odpowiadać `LutStage` na CPU (interpolacja czworościenna, siła mieszana w świetle liniowym).
 */
interface PlaneLutKernel : AutoCloseable {
    /** Nazwa backendu do logu czasów (`gpu-agsl-f16`, `gpu-gles-f32`). */
    val name: String

    /** Przetwarza [count] pikseli od [from] w miejscu; rzuca wyjątek przy dowolnym błędzie GPU. */
    @Suppress("LongParameterList") // trzy płaszczyzny + zakres + film
    fun apply(r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int, lut: Lut, intensity: Float)

    override fun close() = Unit
}

/** Wspólny układ pikseli: [count] pikseli zawijanych w wiersze o szerokości [width]. */
internal class PlaneGrid(count: Int, maxWidth: Int = MAX_WIDTH) {
    val width = count.coerceIn(1, maxWidth)
    val height = (count + width - 1) / width

    companion object {
        const val MAX_WIDTH = 2048
    }
}

/** Fragment tetraedrycznego próbkowania wspólny dla AGSL i GLSL (różnią się tylko funkcją `node`). */
internal object LutShaderMath {
    /**
     * Ciało funkcji: `graded` z `rgb` (wejście przycięte do 0..1; `src` = surowe do mieszania siły), `size` i
     * `node(r, g, b)`; typy `vec3` (GLSL) lub `float3` (AGSL).
     */
    fun tetra(vec: String): String = """
        $vec p = rgb * (size - 1.0);
        $vec i0 = min(floor(p), $vec(size - 1.0));
        $vec i1 = min(i0 + 1.0, $vec(size - 1.0));
        $vec f = p - i0;
        $vec c000 = node(i0.r, i0.g, i0.b);
        $vec c111 = node(i1.r, i1.g, i1.b);
        $vec graded;
        if (f.r >= f.g && f.g >= f.b) {
            $vec a = node(i1.r, i0.g, i0.b); $vec b = node(i1.r, i1.g, i0.b);
            graded = c000 + f.r * (a - c000) + f.g * (b - a) + f.b * (c111 - b);
        } else if (f.r >= f.b && f.b >= f.g) {
            $vec a = node(i1.r, i0.g, i0.b); $vec b = node(i1.r, i0.g, i1.b);
            graded = c000 + f.r * (a - c000) + f.b * (b - a) + f.g * (c111 - b);
        } else if (f.b >= f.r && f.r >= f.g) {
            $vec a = node(i0.r, i0.g, i1.b); $vec b = node(i1.r, i0.g, i1.b);
            graded = c000 + f.b * (a - c000) + f.r * (b - a) + f.g * (c111 - b);
        } else if (f.g >= f.r && f.r >= f.b) {
            $vec a = node(i0.r, i1.g, i0.b); $vec b = node(i1.r, i1.g, i0.b);
            graded = c000 + f.g * (a - c000) + f.r * (b - a) + f.b * (c111 - b);
        } else if (f.g >= f.b && f.b >= f.r) {
            $vec a = node(i0.r, i1.g, i0.b); $vec b = node(i0.r, i1.g, i1.b);
            graded = c000 + f.g * (a - c000) + f.b * (b - a) + f.r * (c111 - b);
        } else {
            $vec a = node(i0.r, i0.g, i1.b); $vec b = node(i0.r, i1.g, i1.b);
            graded = c000 + f.b * (a - c000) + f.g * (b - a) + f.r * (c111 - b);
        }
    """.trimIndent()

    fun srgb(vec: String): String = """
        $vec toLinear($vec c) {
            return mix(c / 12.92, pow(max((c + 0.055) / 1.055, $vec(0.0)), $vec(2.4)), step($vec(0.04045), c));
        }
        $vec toSrgb($vec c) {
            return mix(c * 12.92, 1.055 * pow(max(c, $vec(0.0)), $vec(1.0 / 2.4)) - 0.055, step($vec(0.0031308), c));
        }
    """.trimIndent()

    /** Mieszanie siły jak `CpuLut.mixLinear`: dla t ≥ 1 sam LUT, inaczej w świetle liniowym; wynik 0..1. */
    fun mix(vec: String): String = """
        $vec outRgb = intensity >= 1.0 ? graded
            : toSrgb(clamp(mix(toLinear(src), toLinear(clamp(graded, 0.0, 1.0)), intensity), 0.0, 1.0));
        outRgb = clamp(outRgb, 0.0, 1.0);
    """.trimIndent()
}
