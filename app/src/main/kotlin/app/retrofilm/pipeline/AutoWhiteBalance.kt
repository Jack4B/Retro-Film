package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** Wynik [AutoWhiteBalance]: suwaki, reszta poza krzywą dzienną (xy), oszacowane oświetlenie (RGB) i pewność 0..1. */
class WbEstimate(
    val temperature: Float,
    val tint: Float,
    val dx: Float,
    val dy: Float,
    val illuminant: DoubleArray?,
    val confidence: Float
) {
    companion object {
        val NONE = WbEstimate(0f, 0f, 0f, 0f, null, 0f)
    }
}

/**
 * Automatyczny balans bieli dla [AutoEnhance]. Oświetlenie szacują cztery niezależne estymatory na próbkach
 * neutralnych (bez nieba i przepalonych; punkt startowy z [GreySearch] – najgęstszego skupiska próbek przy krzywej
 * Plancka): shades of gray p = 5 i p = 2 (Finlayson i Trezzi 2004),
 * gray-edge (van de Weijer i in. 2007, średni moduł gradientu) i white-patch (najjaśniejsze 5%). Wynikiem jest mediana
 * ich chromatyczności rg, a pewność rośnie, gdy estymatory się zgadzają (rozrzut < 10°). Drugi przebieg powtarza
 * szacowanie tylko na próbkach, które po pierwszej korekcie są prawie szare. Korekta to adaptacja Bradforda z
 * oszacowanego xy do D65 – także poza krzywą dzienną (reszta w [WbEstimate.dx]/[WbEstimate.dy]) – z siłą zależną od
 * pewności, dominującej barwy sceny i liczby próbek neutralnych, ograniczona do odległości 0,2 w xy. Odcień skóry
 * po korekcie nie może oddalić się od swojego przedziału.
 */
@Suppress("MagicNumber", "TooManyFunctions") // progi heurystyki są danymi, opisanymi w dokumentacji klasy
internal object AutoWhiteBalance {
    private const val MIN_USABLE = 0.12f
    private const val MIN_SAMPLES = 16
    private const val MIN_ILLUMINANT_SAMPLES = 64
    private const val SKIN_HUE = 55f
    private const val SKIN_HUE_SLACK = 12f
    private const val MAX_SHIFT = 0.2
    private const val SPREAD_DEG = 10.0
    private const val NEAR_GREY = 0.08
    private const val REFINE_PASSES = 2
    private const val DOMINANT = 0.55f
    private const val TRUSTED = 0.7
    private const val LIMIT = 1f
    private val SKIN_STEPS = listOf(1f, 0.75f, 0.5f, 0.25f)

    fun estimate(s: Sample, scene: SceneAnalysis): WbEstimate {
        // Pierwszy przebieg bez progu chromy (pod mocnym światłem szarości wyglądają na barwne), potem zawężanie.
        val open = BooleanArray(s.count) { !scene.sky[it] && !(scene.hasFaces && scene.skin[it]) }
        val fallback = combined(s, open)
        val seed = GreySearch.densestGrey(s, open) ?: fallback?.first
        var est = seed?.let { combined(s, nearGrey(s, open, it)) } ?: fallback ?: return WbEstimate.NONE
        repeat(REFINE_PASSES) { est = combined(s, nearGrey(s, open, est.first)) ?: est }
        val (e, conf) = est
        // Dominującą barwę oceniamy po usunięciu oświetlenia: zabarwienie od światła to nie barwa sceny.
        val dominance = SceneAnalyzer.analyze(divided(s, e), emptyList()).dominance
        val dominanceFactor = if (dominance <= DOMINANT) 1.0 else maxOf(0.2, 1.0 - (dominance - DOMINANT) * 1.6)
        val usable = if (scene.usableRatio >= MIN_USABLE) 1.0 else 0.4
        val strength = dominanceFactor * usable * (0.5 + 0.5 * conf)
        val xy = Cat.xyOf(e)
        var ddx = (xy.first - Cat.D65_XY.first) * strength
        var ddy = (xy.second - Cat.D65_XY.second) * strength
        val dist = hypot(ddx, ddy)
        if (dist > MAX_SHIFT) {
            ddx *= MAX_SHIFT / dist
            ddy *= MAX_SHIFT / dist
        }
        val target = Cat.D65_XY.first + ddx to Cat.D65_XY.second + ddy
        // Strażnik skóry tylko przy twarzach albo niepewnym oświetleniu – przy zgodnych estymatorach ufamy pomiarowi.
        val fitted = fit(target)
        val guarded = if (scene.hasFaces || conf < TRUSTED) guardSkin(s, scene, fitted) else fitted
        return WbEstimate(guarded[0], guarded[1], guarded[2], guarded[3], e, conf.toFloat())
    }

    /** Mediana chromatyczności rg czterech estymatorów i pewność z ich rozrzutu kątowego. */
    private fun combined(s: Sample, mask: BooleanArray): Pair<DoubleArray, Double>? {
        val idx = (0 until s.count).filter { mask[it] && !s.clipped[it] && midtone(s.rgb, it) }
        if (idx.size < MIN_ILLUMINANT_SAMPLES) return null
        val list = listOfNotNull(minkowski(s, idx, 5.0), minkowski(s, idx, 2.0), grayEdge(s, mask), whitePatch(s, idx))
        val r = list.map { it[0] / it.sum() }.sorted()
        val g = list.map { it[1] / it.sum() }.sorted()
        fun med(v: List<Double>) = if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
        val e = doubleArrayOf(med(r), med(g), 1 - med(r) - med(g))
        val spread = list.maxOf { angle(it, e) }
        return e to (1.0 - spread / SPREAD_DEG).coerceIn(0.0, 1.0)
    }

    private fun minkowski(s: Sample, idx: List<Int>, p: Double): DoubleArray {
        val sum = DoubleArray(3)
        for (i in idx) for (k in 0..2) sum[k] += s.rgb[3 * i + k].toDouble().pow(p)
        return DoubleArray(3) { (sum[it] / idx.size).pow(1.0 / p) }
    }

    /** Gray-edge (p = 1): średni moduł różnic sąsiednich próbek, gdy obie są neutralne i nieprzepalone. */
    private fun grayEdge(s: Sample, mask: BooleanArray): DoubleArray? {
        val sum = DoubleArray(3)
        var n = 0
        fun ok(i: Int) = mask[i] && !s.clipped[i]
        for (y in 0 until s.gh) {
            for (x in 0 until s.gw) {
                val i = y * s.gw + x
                if (!ok(i)) continue
                if (x + 1 < s.gw && ok(i + 1)) n += addEdge(s, i, i + 1, sum)
                if (y + 1 < s.gh && ok(i + s.gw)) n += addEdge(s, i, i + s.gw, sum)
            }
        }
        val e = DoubleArray(3) { sum[it] / maxOf(n, 1) }
        return if (n < MIN_ILLUMINANT_SAMPLES || e.sum() < 1e-4) null else e
    }

    private fun addEdge(s: Sample, i: Int, j: Int, sum: DoubleArray): Int {
        for (k in 0..2) sum[k] += abs(s.rgb[3 * i + k] - s.rgb[3 * j + k]).toDouble()
        return 1
    }

    /** White-patch: średnia najjaśniejszych 5% próbek neutralnych (bez przepalonych). */
    private fun whitePatch(s: Sample, idx: List<Int>): DoubleArray {
        val top = idx.sortedByDescending { s.rgb[3 * it] + s.rgb[3 * it + 1] + s.rgb[3 * it + 2] }
            .take(maxOf(MIN_SAMPLES, idx.size / 20))
        return DoubleArray(3) { k -> top.sumOf { s.rgb[3 * it + k].toDouble() } / top.size }
    }

    /**
     * Próbki najbardziej szare po podzieleniu przez oszacowane oświetlenie [e]: najmniej barwna ćwiartka próbek
     * neutralnych (co najmniej [MIN_ILLUMINANT_SAMPLES]), ale nie dalej niż [NEAR_GREY] od szarości w rg.
     */
    private fun nearGrey(s: Sample, mask: BooleanArray, e: DoubleArray): BooleanArray {
        val dist = DoubleArray(s.count) { i ->
            val c = DoubleArray(3) { s.rgb[3 * i + it] / e[it] }
            val sum = c.sum()
            if (!mask[i] || s.clipped[i] ||
                sum < 1e-4
            ) {
                Double.MAX_VALUE
            } else {
                hypot(c[0] / sum - 1.0 / 3, c[1] / sum - 1.0 / 3)
            }
        }
        val sorted = dist.filter { it < Double.MAX_VALUE }.sorted()
        if (sorted.isEmpty()) return BooleanArray(s.count)
        val limit =
            minOf(NEAR_GREY, sorted[maxOf(MIN_ILLUMINANT_SAMPLES, sorted.size / 10).coerceAtMost(sorted.size - 1)])
        return BooleanArray(s.count) { dist[it] <= limit }
    }

    private fun divided(s: Sample, e: DoubleArray): Sample {
        val k = DoubleArray(3) { e.sum() / 3 / e[it] }
        return Sample(
            FloatArray(s.rgb.size) {
                (s.rgb[it] * k[it % 3]).toFloat().coerceIn(0f, 1f)
            },
            s.gw,
            s.gh,
            s.clipped
        )
    }

    private fun angle(a: DoubleArray, b: DoubleArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val cross = sqrt(
            (a[1] * b[2] - a[2] * b[1]).pow(2) + (a[2] * b[0] - a[0] * b[2]).pow(2) + (a[0] * b[1] - a[1] * b[0]).pow(2)
        )
        return Math.toDegrees(atan2(cross, dot))
    }

    private fun midtone(rgb: FloatArray, i: Int): Boolean {
        val r = rgb[3 * i]
        val g = rgb[3 * i + 1]
        val b = rgb[3 * i + 2]
        return Cat.luma(doubleArrayOf(r.toDouble(), g.toDouble(), b.toDouble())) >= 0.01 && maxOf(r, g, b) <= 0.95f
    }

    /** Suwaki temperatury i tintu najbliższe docelowemu xy (siatka, ±1) i reszta (dx, dy). */
    private fun fit(target: Pair<Double, Double>): FloatArray {
        var best = 0f to 0f
        var bestCost = cost(0f, 0f, target)
        var step = 0.2f
        repeat(5) {
            val center = best
            for (i in -5..5) {
                for (j in -5..5) {
                    val t = (center.first + i * step).coerceIn(-LIMIT, LIMIT)
                    val g = (center.second + j * step).coerceIn(-LIMIT, LIMIT)
                    val c = cost(t, g, target)
                    if (c < bestCost - 1e-12) {
                        bestCost = c
                        best = t to g
                    }
                }
            }
            step /= 3f
        }
        val src = WhiteBalance.sourceXy(best.first, best.second)
        return floatArrayOf(
            best.first,
            best.second,
            (target.first - src.first).toFloat(),
            (target.second - src.second).toFloat()
        )
    }

    private fun cost(t: Float, g: Float, target: Pair<Double, Double>): Double {
        val src = WhiteBalance.sourceXy(t, g)
        return (src.first - target.first).pow(2) + (src.second - target.second).pow(2)
    }

    /**
     * Korekta nie może oddalić odcienia skóry od jej przedziału: zmniejszamy ją w krokach, aż odcień skóry po korekcie
     * jest w granicach lub nie gorszy niż przed nią; gdy żaden krok nie pasuje, balans zostaje bez zmian.
     */
    private fun guardSkin(s: Sample, scene: SceneAnalysis, wb: FloatArray): FloatArray {
        val skin = meanColor(s, scene.skin) ?: return wb
        val before = abs(hueOf(skin) - SKIN_HUE)
        val step = SKIN_STEPS.firstOrNull { f ->
            abs(hueOf(corrected(skin, wb, f)) - SKIN_HUE) <= maxOf(before, SKIN_HUE_SLACK)
        } ?: 0f
        return FloatArray(4) { wb[it] * step }
    }

    private fun corrected(skin: DoubleArray, wb: FloatArray, f: Float): DoubleArray {
        val m = WhiteBalance.matrix(wb[0] * f, wb[1] * f, wb[2] * f, wb[3] * f)
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
}
