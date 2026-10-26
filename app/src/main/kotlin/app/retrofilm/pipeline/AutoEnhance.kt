package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Wynik analizy [AutoEnhance]: propozycje suwaków (exposure w EV, reszta −1..1 jak w [Adjustments]). */
data class AutoResult(
    val exposure: Float,
    val contrast: Float,
    val highlights: Float,
    val shadows: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float
) {
    /** Nakłada propozycję na [base] (pozostałe korekty zostają). */
    fun applyTo(base: Adjustments): Adjustments = base.copy(
        exposure = exposure,
        contrast = contrast,
        highlights = highlights,
        shadows = shadows,
        saturation = saturation,
        temperature = temperature,
        tint = tint
    )
}

/**
 * Automatyczne ulepszenie z analizy histogramu na zmniejszonej kopii (bez uczenia maszynowego, deterministyczne):
 * - balans bieli: oświetlenie szacowane metodą „shades of gray” (Finlayson i Trezzi 2004, norma Minkowskiego p = 2,
 *   z pominięciem skrajnych jasności), korygowane częściowo (50% w skali logarytmicznej) przez dobranie temperatury i
 *   tintu, które po adaptacji Bradforda najbardziej zbliżają szacowane oświetlenie do szarości;
 * - ekspozycja: mediana jasności liniowej do ok. 18% szarości, ale tylko w 60% (zdjęcia ciemne lub jasne z założenia
 *   zostają takie), z limitem tak, żeby 99. percentyl nie przekroczył bieli o więcej niż światła da się odzyskać;
 * - kontrast z rozstępu percentyli 5–95, cienie z 10. percentyla, światła z 98. percentyla;
 * - nasycenie z średniej chromy OKLab w półtonach (zdjęcia wyblakłe dostają trochę, przesycone tracą).
 * Wszystkie wartości są ograniczone, żeby auto nigdy nie robiło drastycznych zmian.
 */
@Suppress("MagicNumber") // progi i współczynniki heurystyki (opisane powyżej) są danymi, nie „magicznymi liczbami”
object AutoEnhance {
    private const val SAMPLE_SIDE = 256
    private const val GRAY_P = 2.0
    private const val WB_STRENGTH = 0.5
    private const val WB_LIMIT = 0.4f
    private const val TARGET_MEDIAN = 0.18f
    private const val EXPOSURE_STRENGTH = 0.6f
    private const val EXPOSURE_LIMIT = 1.5f
    private const val CLIP_ALLOWANCE = 1.6f
    private const val SPREAD_TARGET = 0.80f
    private const val LUM_R = 0.2126f
    private const val LUM_G = 0.7152f
    private const val LUM_B = 0.0722f

    fun suggest(px: IntArray, w: Int, h: Int): AutoResult {
        val sample = sample(px, w, h)
        val wb = whiteBalance(sample)
        val m = WhiteBalance.matrix(wb.first, wb.second)
        val lumas = FloatArray(sample.size / 3) { i ->
            val r = sample[3 * i]
            val g = sample[3 * i + 1]
            val b = sample[3 * i + 2]
            LUM_R * (m[0] * r + m[1] * g + m[2] * b) + LUM_G * (m[3] * r + m[4] * g + m[5] * b) +
                LUM_B * (m[6] * r + m[7] * g + m[8] * b)
        }
        val ev = exposure(lumas)
        val gain = 2f.pow(ev)
        val enc = FloatArray(lumas.size) { AdjustmentProcessor.linearToSrgb((lumas[it] * gain).coerceIn(0f, 1f)) }
        enc.sort()
        fun pct(p: Float) = enc[((enc.size - 1) * p).toInt()]
        val spread = pct(0.95f) - pct(0.05f)
        val contrast = ((SPREAD_TARGET - spread) * 1.2f).coerceIn(-0.15f, 0.45f)
        val shadows = ((0.14f - pct(0.10f)) * 4.5f).coerceIn(0f, 0.7f)
        val highlights = -((pct(0.98f) - 0.90f) * 6f).coerceIn(0f, 0.7f)
        return AutoResult(ev, contrast, highlights, shadows, saturation(sample, m), wb.first, wb.second)
    }

    /** Próbki liniowego RGB (trójki) z siatki o dłuższym boku ok. [SAMPLE_SIDE]. */
    private fun sample(px: IntArray, w: Int, h: Int): FloatArray {
        val step = maxOf(1, maxOf(w, h) / SAMPLE_SIDE)
        val out = ArrayList<Float>()
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val c = px[y * w + x]
                out.add(AdjustmentProcessor.srgbToLinear(((c shr 16) and 0xFF) / 255f))
                out.add(AdjustmentProcessor.srgbToLinear(((c shr 8) and 0xFF) / 255f))
                out.add(AdjustmentProcessor.srgbToLinear((c and 0xFF) / 255f))
            }
        }
        return out.toFloatArray()
    }

    /** Temperatura i tint (−1..1) zbliżające oświetlenie szacowane metodą shades of gray do szarości. */
    private fun whiteBalance(s: FloatArray): Pair<Float, Float> {
        val sum = DoubleArray(3)
        var n = 0
        for (i in 0 until s.size / 3) {
            val r = s[3 * i]
            val g = s[3 * i + 1]
            val b = s[3 * i + 2]
            val y = LUM_R * r + LUM_G * g + LUM_B * b
            if (y < 0.01f || maxOf(r, g, b) > 0.9f) continue
            sum[0] += r.toDouble().pow(GRAY_P)
            sum[1] += g.toDouble().pow(GRAY_P)
            sum[2] += b.toDouble().pow(GRAY_P)
            n++
        }
        if (n < MIN_SAMPLES) return 0f to 0f
        val e = DoubleArray(3) { (sum[it] / n).pow(1.0 / GRAY_P) }
        val mean = (e[0] + e[1] + e[2]) / 3.0
        // częściowa korekta: dążymy do neutralizacji tylko ułamka odchylenia (w skali logarytmicznej)
        val target = DoubleArray(3) { (e[it] / mean).pow(WB_STRENGTH) }
        return bestTempTint(target)
    }

    private fun bestTempTint(e: DoubleArray): Pair<Float, Float> {
        var best = 0f to 0f
        var bestCost = cost(0f, 0f, e)
        var step = 0.1f
        repeat(4) {
            val center = best
            for (i in -5..5) {
                for (j in -5..5) {
                    val t = (center.first + i * step).coerceIn(-WB_LIMIT, WB_LIMIT)
                    val g = (center.second + j * step).coerceIn(-WB_LIMIT, WB_LIMIT)
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

    /** Ekspozycja w EV: mediana do 18% szarości w 60%, ograniczona tak, by światła nie wypalały się ponad miarę. */
    private fun exposure(lumas: FloatArray): Float {
        val sorted = lumas.sortedArray()
        val median = sorted[sorted.size / 2].coerceAtLeast(MIN_LUMA)
        val p99 = sorted[((sorted.size - 1) * 0.99f).toInt()].coerceAtLeast(MIN_LUMA)
        var ev = (ln(TARGET_MEDIAN / median) / ln(2f)) * EXPOSURE_STRENGTH
        val headroom = ln(CLIP_ALLOWANCE / p99) / ln(2f)
        if (ev > headroom) ev = headroom
        return ev.coerceIn(-EXPOSURE_LIMIT, EXPOSURE_LIMIT).let { if (abs(it) < MIN_EV) 0f else it }
    }

    /** Nasycenie z średniej chromy OKLab pikseli o średniej jasności (po balansie bieli). */
    private fun saturation(s: FloatArray, m: FloatArray): Float {
        val ok = OkLab()
        val rgb = FloatArray(3)
        var sum = 0.0
        var n = 0
        for (i in 0 until s.size / 3) {
            val r = m[0] * s[3 * i] + m[1] * s[3 * i + 1] + m[2] * s[3 * i + 2]
            val g = m[3] * s[3 * i] + m[4] * s[3 * i + 1] + m[5] * s[3 * i + 2]
            val b = m[6] * s[3 * i] + m[7] * s[3 * i + 1] + m[8] * s[3 * i + 2]
            rgb[0] = AdjustmentProcessor.linearToSrgb(r.coerceIn(0f, 1f))
            rgb[1] = AdjustmentProcessor.linearToSrgb(g.coerceIn(0f, 1f))
            rgb[2] = AdjustmentProcessor.linearToSrgb(b.coerceIn(0f, 1f))
            ok.fromRgb(rgb)
            if (ok.lab[0] in 0.25f..0.85f) {
                sum += kotlin.math.hypot(ok.lab[1].toDouble(), ok.lab[2].toDouble())
                n++
            }
        }
        if (n < MIN_SAMPLES) return 0f
        val chroma = (sum / n).toFloat()
        return when {
            chroma < MONOCHROME -> 0f
            chroma < LOW_CHROMA -> ((0.09f - chroma) * 6f).coerceIn(0f, 0.35f)
            else -> -((chroma - 0.16f) * 3f).coerceIn(0f, 0.2f)
        }
    }

    private const val MIN_SAMPLES = 64
    private const val MONOCHROME = 0.01f
    private const val LOW_CHROMA = 0.07f
    private const val MIN_LUMA = 1e-4f
    private const val MIN_EV = 0.05f
}
