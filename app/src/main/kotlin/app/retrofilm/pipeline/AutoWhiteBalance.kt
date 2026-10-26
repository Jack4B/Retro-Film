package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.pow

/**
 * Automatyczny balans bieli dla [AutoEnhance]: oświetlenie szacowane metodą „shades of gray” (Finlayson i Trezzi
 * 2004, norma Minkowskiego p = 2) tylko z pikseli, które się do tego nadają ([SceneAnalysis.usable]), potem
 * temperatura i tint dobrane tak, żeby po adaptacji Bradforda ([WhiteBalance]) oświetlenie było jak najbliższe
 * szarości. Siła korekty (50%) maleje, gdy mało jest pikseli neutralnych albo scena ma dominującą barwę, a odcień
 * skóry po korekcie nie może oddalić się od swojego przedziału.
 */
@Suppress("MagicNumber") // progi heurystyki są danymi, opisanymi w dokumentacji klasy
internal object AutoWhiteBalance {
    private const val GRAY_P = 2.0
    private const val STRENGTH = 0.5
    private const val LIMIT = 0.4f
    private const val MIN_USABLE = 0.12f
    private const val MIN_SAMPLES = 16
    private const val SKIN_HUE = 55f
    private const val SKIN_HUE_SLACK = 12f
    private const val LUM_R = 0.2126f
    private const val LUM_G = 0.7152f
    private const val LUM_B = 0.0722f
    private val SKIN_STEPS = listOf(1f, 0.75f, 0.5f, 0.25f)

    /** Temperatura i tint (−1..1) zbliżające szacowane oświetlenie do szarości, z siłą zależną od sceny. */
    fun estimate(s: Sample, scene: SceneAnalysis): Pair<Float, Float> {
        val e = illuminant(s, scene) ?: return 0f to 0f
        val mean = (e[0] + e[1] + e[2]) / 3.0
        val dominanceFactor = if (scene.dominance <= 0.45f) 1.0 else maxOf(0.2, 1.0 - (scene.dominance - 0.45) * 1.6)
        val confidence = if (scene.usableRatio >= MIN_USABLE) 1.0 else 0.4
        val strength = STRENGTH * dominanceFactor * confidence
        val target = DoubleArray(3) { (e[it] / mean).pow(strength) }
        return guardSkin(s, scene, bestTempTint(target))
    }

    /** Oświetlenie (RGB, shades of gray) z pikseli nadających się do tego i nieskrajnych jasnościowo. */
    private fun illuminant(s: Sample, scene: SceneAnalysis): DoubleArray? {
        val sum = DoubleArray(3)
        var n = 0
        for (i in 0 until s.count) {
            if (scene.usable[i] && midtone(s.rgb, i)) {
                for (k in 0..2) sum[k] += s.rgb[3 * i + k].toDouble().pow(GRAY_P)
                n++
            }
        }
        return if (n < MIN_SAMPLES) null else DoubleArray(3) { (sum[it] / n).pow(1.0 / GRAY_P) }
    }

    private fun midtone(rgb: FloatArray, i: Int): Boolean {
        val r = rgb[3 * i]
        val g = rgb[3 * i + 1]
        val b = rgb[3 * i + 2]
        return LUM_R * r + LUM_G * g + LUM_B * b >= 0.01f && maxOf(r, g, b) <= 0.9f
    }

    /**
     * Korekta nie może oddalić odcienia skóry od jej przedziału: zmniejszamy ją w krokach, aż odcień skóry po korekcie
     * jest w granicach lub nie gorszy niż przed nią; gdy żaden krok nie pasuje, balans zostaje bez zmian.
     */
    private fun guardSkin(s: Sample, scene: SceneAnalysis, wb: Pair<Float, Float>): Pair<Float, Float> {
        val skin = meanColor(s, scene.skin)
        val step = if (skin == null) {
            1f
        } else {
            val before = abs(hueOf(skin) - SKIN_HUE)
            SKIN_STEPS.firstOrNull { f ->
                abs(hueOf(corrected(skin, wb, f)) - SKIN_HUE) <= maxOf(before, SKIN_HUE_SLACK)
            }
                ?: 0f
        }
        return wb.first * step to wb.second * step
    }

    private fun corrected(skin: DoubleArray, wb: Pair<Float, Float>, f: Float): DoubleArray {
        val m = WhiteBalance.matrix(wb.first * f, wb.second * f)
        return DoubleArray(3) { r -> m[3 * r] * skin[0] + m[3 * r + 1] * skin[1] + m[3 * r + 2] * skin[2] }
    }

    private fun meanColor(s: Sample, mask: BooleanArray): DoubleArray? {
        val sum = DoubleArray(3)
        var n = 0
        for (i in 0 until s.count) {
            if (mask[i]) {
                for (k in 0..2) sum[k] += s.rgb[3 * i + k].toDouble()
                n++
            }
        }
        return if (n < MIN_SAMPLES) null else DoubleArray(3) { sum[it] / n }
    }

    private fun hueOf(linear: DoubleArray): Float {
        val ok = OkLab()
        val rgb = FloatArray(3) { AdjustmentProcessor.linearToSrgb(linear[it].toFloat().coerceIn(0f, 1f)) }
        ok.fromRgb(rgb)
        return OkLab.hueDegrees(ok.lab[1], ok.lab[2])
    }

    private fun bestTempTint(e: DoubleArray): Pair<Float, Float> {
        var best = 0f to 0f
        var bestCost = cost(0f, 0f, e)
        var step = 0.1f
        repeat(4) {
            val center = best
            for (i in -5..5) {
                for (j in -5..5) {
                    val t = (center.first + i * step).coerceIn(-LIMIT, LIMIT)
                    val g = (center.second + j * step).coerceIn(-LIMIT, LIMIT)
                    val c = cost(t, g, e)
                    if (c < bestCost - 1e-9) {
                        bestCost = c
                        best = t to g
                    }
                }
            }
            step /= 3f
        }
        return best
    }

    /** Odchylenie od szarości po zastosowaniu balansu bieli do oświetlenia [e] (suma |kanał/średnia − 1|). */
    private fun cost(t: Float, tint: Float, e: DoubleArray): Double {
        val m = WhiteBalance.matrix(t, tint)
        val out = DoubleArray(3) { r -> m[3 * r] * e[0] + m[3 * r + 1] * e[1] + m[3 * r + 2] * e[2] }
        val mean = (out[0] + out[1] + out[2]) / 3.0
        return out.sumOf { abs(it / mean - 1.0) }
    }
}
