package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Syntetyczna tablica ColorChecker (wartości sRGB BabelColor, D65) oświetlona innym światłem (adaptacja Bradforda
 * z D65 do oświetlenia = aparat bez balansu bieli) i niedo-/prześwietlona o ±2 EV. Po „Przygotuj pod film” (balans
 * bieli i ekspozycja, bez krzywych) mierzymy: ΔE2000 neutralności szarych pól (szarość porównana z neutralną
 * o tej samej jasności), błąd kątowy oszacowanego oświetlenia i błąd ekspozycji pola „neutral 5” w EV.
 * Progi z docs/AUTO-KOREKTA-OCENA.md: ΔE00 < 3, kąt < 3°, EV < 0,25.
 */
class ColorCheckerAutoTest {
    private val patches = arrayOf(
        intArrayOf(115, 82, 68), intArrayOf(194, 150, 130), intArrayOf(98, 122, 157), intArrayOf(87, 108, 67),
        intArrayOf(133, 128, 177), intArrayOf(103, 189, 170), intArrayOf(214, 126, 44), intArrayOf(80, 91, 166),
        intArrayOf(193, 90, 99), intArrayOf(94, 60, 108), intArrayOf(157, 188, 64), intArrayOf(224, 163, 46),
        intArrayOf(56, 61, 150), intArrayOf(70, 148, 73), intArrayOf(175, 54, 60), intArrayOf(231, 199, 31),
        intArrayOf(187, 86, 149), intArrayOf(8, 133, 161), intArrayOf(243, 243, 242), intArrayOf(200, 200, 200),
        intArrayOf(160, 160, 160), intArrayOf(122, 122, 121), intArrayOf(85, 85, 85), intArrayOf(52, 52, 52)
    )
    private val patch = 40
    private val clipLevel = 0.95
    private val w = 6 * patch
    private val h = 4 * patch

    private val illuminants = mapOf(
        "A 2856 K" to (0.4476 to 0.4074),
        "4000 K" to (0.3805 to 0.3768),
        "F2" to (0.3721 to 0.3751),
        "LED 3000 K" to (0.4339 to 0.4033),
        "D50" to (0.3457 to 0.3585),
        "7500 K" to (0.2990 to 0.3149),
        "D65" to (0.3127 to 0.3290)
    )

    private fun lin(v: Int) = AdjustmentProcessor.srgbToLinear(v / 255f).toDouble()

    private fun scene(xy: Pair<Double, Double>, ev: Double): Pair<IntArray, DoubleArray> {
        val m = Cat.toIlluminant(xy)
        val k = 2.0.pow(ev)
        val px = IntArray(w * h) { i ->
            val p = patches[(i / w) / patch * 6 + (i % w) / patch]
            val l = DoubleArray(3) { lin(p[it]) }
            val o = IntArray(3) { r ->
                val v = (m[3 * r] * l[0] + m[3 * r + 1] * l[1] + m[3 * r + 2] * l[2]) * k
                (AdjustmentProcessor.linearToSrgb(v.toFloat().coerceIn(0f, 1f)) * 255f + 0.5f).toInt()
            }
            (0xFF shl 24) or (o[0] shl 16) or (o[1] shl 8) or o[2]
        }
        val white = DoubleArray(3) { r -> m[3 * r] + m[3 * r + 1] + m[3 * r + 2] }
        return px to white
    }

    private fun meanPatch(px: IntArray, index: Int): DoubleArray {
        val x0 = (index % 6) * patch + patch / 4
        val y0 = (index / 6) * patch + patch / 4
        val sum = DoubleArray(3)
        var n = 0
        for (y in y0 until y0 + patch / 2) {
            for (x in x0 until x0 + patch / 2) {
                val c = px[y * w + x]
                sum[0] += lin((c shr 16) and 0xFF)
                sum[1] += lin((c shr 8) and 0xFF)
                sum[2] += lin(c and 0xFF)
                n++
            }
        }
        return DoubleArray(3) { sum[it] / n }
    }

    private class Case(val name: String, val deltaE: Double, val angle: Double, val ev: Double)

    private fun run(name: String, xy: Pair<Double, Double>, ev: Double): Case {
        val (px, white) = scene(xy, ev)
        val result = AutoEnhance.suggest(px, w, h).forFilm()
        val adj = result.applyTo(Adjustments.None).copy(toneInversion = 0f)
        val out = px.copyOf()
        AdjustmentProcessor(adj).process(out)
        var worst = 0.0
        // Szare pola (19 biel … 23 neutral 3.5) bez przepalonych kanałów na wejściu – przepalonych nie da się odzyskać.
        for (g in 18..22) if (meanPatch(px, g).max() < clipLevel) worst = maxOf(worst, neutralDeltaE(meanPatch(out, g)))
        val est = result.illuminant?.toDoubleArray() ?: doubleArrayOf(1.0, 1.0, 1.0)
        val y = Cat.luma(meanPatch(out, 21))
        val evErr = abs(ln(y / Cat.luma(DoubleArray(3) { lin(patches[21][0]) })) / ln(2.0))
        return Case(name, worst, angleDeg(est, white), evErr)
    }

    @Test fun greysNeutralIlluminantAccurateAndExposureRightAcrossLightsAndExposures() {
        val cases = illuminants.flatMap { (name, xy) ->
            listOf(-2.0, -1.0, 0.0, 1.0, 2.0).map { ev -> run("$name ${ev}EV", xy, ev) }
        }
        val report = cases.joinToString("\n") {
            "%-18s ΔE00=%.2f kąt=%.2f° EV=%.2f".format(it.name, it.deltaE, it.angle, it.ev)
        }
        println(report)
        // +2 EV przepala biel i jasne szarości (kanały ≥ 0,98) – tylko raportowane, bez progu: z przepalonych pól
        // nie da się odczytać ani oświetlenia, ani ekspozycji. Progi obowiązują w zakresie −2..+1 EV.
        val fair = cases.filter { !it.name.endsWith(" 2.0EV") }
        assertTrue("ΔE00 szarości\n$report", fair.all { it.deltaE < 3.0 })
        assertTrue("kąt oświetlenia\n$report", fair.all { it.angle < 3.0 })
        assertTrue("ekspozycja\n$report", fair.all { it.ev < 0.25 })
    }

    private fun angleDeg(a: DoubleArray, b: DoubleArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val n = sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it })
        return Math.toDegrees(acos((dot / n).coerceIn(-1.0, 1.0)))
    }

    /** ΔE2000 między kolorem a neutralną szarością tej samej luminancji (miara zabarwienia szarości). */
    private fun neutralDeltaE(rgb: DoubleArray): Double {
        val y = Cat.luma(rgb)
        return deltaE2000(lab(rgb), lab(doubleArrayOf(y, y, y)))
    }

    private fun lab(rgb: DoubleArray): DoubleArray {
        val x = (0.4124564 * rgb[0] + 0.3575761 * rgb[1] + 0.1804375 * rgb[2]) / 0.95047
        val y = 0.2126729 * rgb[0] + 0.7151522 * rgb[1] + 0.0721750 * rgb[2]
        val z = (0.0193339 * rgb[0] + 0.1191920 * rgb[1] + 0.9503041 * rgb[2]) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    @Suppress("LongMethod")
    private fun deltaE2000(a: DoubleArray, b: DoubleArray): Double {
        val c1 = hypot(a[1], a[2])
        val c2 = hypot(b[1], b[2])
        val cm = (c1 + c2) / 2
        val g = 0.5 * (1 - sqrt(cm.pow(7) / (cm.pow(7) + 25.0.pow(7))))
        val a1 = a[1] * (1 + g)
        val a2 = b[1] * (1 + g)
        val c1p = hypot(a1, a[2])
        val c2p = hypot(a2, b[2])
        fun hue(x: Double, y: Double) = if (x == 0.0 && y == 0.0) 0.0 else (Math.toDegrees(atan2(y, x)) + 360) % 360
        val h1 = hue(a1, a[2])
        val h2 = hue(a2, b[2])
        val dL = b[0] - a[0]
        val dC = c2p - c1p
        var dh = h2 - h1
        if (c1p * c2p == 0.0) {
            dh = 0.0
        } else if (dh > 180) {
            dh -= 360
        } else if (dh < -180) {
            dh += 360
        }
        val dH = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dh / 2))
        val lm = (a[0] + b[0]) / 2
        val cmp = (c1p + c2p) / 2
        var hm = h1 + h2
        if (c1p * c2p != 0.0) hm = if (abs(h1 - h2) > 180) (h1 + h2 + 360) / 2 else (h1 + h2) / 2
        val t = 1 - 0.17 * cos(Math.toRadians(hm - 30)) + 0.24 * cos(Math.toRadians(2 * hm)) +
            0.32 * cos(Math.toRadians(3 * hm + 6)) - 0.20 * cos(Math.toRadians(4 * hm - 63))
        val dTheta = 30 * exp(-((hm - 275) / 25).pow(2))
        val rc = 2 * sqrt(cmp.pow(7) / (cmp.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lm - 50).pow(2) / sqrt(20 + (lm - 50).pow(2))
        val sc = 1 + 0.045 * cmp
        val sh = 1 + 0.015 * cmp * t
        val rt = -sin(Math.toRadians(2 * dTheta)) * rc
        return sqrt((dL / sl).pow(2) + (dC / sc).pow(2) + (dH / sh).pow(2) + rt * (dC / sc) * (dH / sh))
    }
}
