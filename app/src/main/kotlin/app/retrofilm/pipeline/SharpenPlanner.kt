package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** Gotowe zestawy wyostrzania; [Auto] dobiera ustawienia do analizy zdjęcia ([SharpenPlanner.suggest]). */
enum class SharpenPreset(val params: SharpenParams?) {
    Auto(null),
    Universal(SharpenParams(amount = 0.5f, radius = 1f, detail = 0.3f, masking = 0.25f)),
    Landscape(SharpenParams(amount = 0.65f, radius = 1.2f, detail = 0.55f, masking = 0.15f)),
    Portrait(SharpenParams(amount = 0.4f, radius = 0.9f, detail = 0.12f, masking = 0.65f))
}

/** Cechy zdjęcia istotne dla wyostrzania: udział odcieni skóry, gęstość krawędzi, szum (σ w skali 0..255). */
data class SceneStats(val skinRatio: Float, val edgeDensity: Float, val noiseSigma: Float)

/**
 * Dobór wyostrzania do zdjęcia: portret (dużo skóry) dostaje delikatne wyostrzanie z maską chroniącą gładkie
 * powierzchnie, krajobraz (dużo krawędzi i faktury) mocniejsze z większym promieniem i „detalem”, a zaszumione
 * zdjęcie mniejszą ilość i silniejsze maskowanie (żeby nie wzmacniać ziarna). Szum szacuje metoda Immerkaera (1996).
 */
@Suppress("MagicNumber") // współczynniki BT.601 (skóra) i Immerkaera (szum) są stałymi z publikacji
object SharpenPlanner {
    private const val SKIN_PORTRAIT = 0.12f
    private const val EDGE_LANDSCAPE = 0.035f
    private const val NOISE_HIGH = 4f
    private const val NOISE_AMOUNT = 0.7f
    private const val NOISE_MASKING = 0.5f
    private const val NOISE_DETAIL = 0.5f
    private const val CB_MIN = 77
    private const val CB_MAX = 127
    private const val CR_MIN = 133
    private const val CR_MAX = 173
    private const val EDGE_STEP = 12f

    /** Jasność BT.601 (0..255) każdego piksela ARGB. */
    fun lumaOf(px: IntArray): FloatArray = FloatArray(px.size) {
        val c = px[it]
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
    }

    fun analyze(px: IntArray, w: Int, h: Int): SceneStats {
        val luma = FloatArray(px.size)
        var skin = 0
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xFF
            val g = (px[i] shr 8) and 0xFF
            val b = px[i] and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = 128 + (-0.169 * r - 0.331 * g + 0.5 * b)
            val cr = 128 + (0.5 * r - 0.419 * g - 0.081 * b)
            if (cb in CB_MIN.toDouble()..CB_MAX.toDouble() && cr in CR_MIN.toDouble()..CR_MAX.toDouble() &&
                luma[i] > 40f
            ) {
                skin++
            }
        }
        var edges = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (abs(luma[i + 1] - luma[i - 1]) + abs(luma[i + w] - luma[i - w]) > EDGE_STEP) edges++
            }
        }
        val pixels = max1(px.size)
        return SceneStats(skin.toFloat() / pixels, edges.toFloat() / pixels, noiseSigma(luma, w, h))
    }

    /** Immerkaer: σ = √(π/2)/(6·(W−2)(H−2)) · Σ|I ∗ N|, N = [[1,−2,1],[−2,4,−2],[1,−2,1]]. */
    fun noiseSigma(luma: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var sum = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val v = luma[i - w - 1] - 2 * luma[i - w] + luma[i - w + 1] -
                    2 * luma[i - 1] + 4 * luma[i] - 2 * luma[i + 1] +
                    luma[i + w - 1] - 2 * luma[i + w] + luma[i + w + 1]
                sum += abs(v)
            }
        }
        return (sqrt(PI / 2) * sum / (6.0 * (w - 2) * (h - 2))).toFloat()
    }

    fun suggest(s: SceneStats): SharpenParams {
        val base = when {
            s.skinRatio >= SKIN_PORTRAIT -> SharpenPreset.Portrait
            s.edgeDensity >= EDGE_LANDSCAPE -> SharpenPreset.Landscape
            else -> SharpenPreset.Universal
        }.params!!
        return if (s.noiseSigma > NOISE_HIGH) {
            base.copy(
                amount = base.amount * NOISE_AMOUNT,
                masking = maxOf(base.masking, NOISE_MASKING),
                detail = base.detail * NOISE_DETAIL
            )
        } else {
            base
        }
    }

    private fun max1(n: Int) = if (n < 1) 1 else n
}
