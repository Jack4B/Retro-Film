package app.retrofilm.pipeline

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Punkt startowy szacowania oświetlenia: szare powierzchnie pod światłem [T] mają chromatyczność tego światła, więc
 * szukamy wzdłuż krzywej Plancka (1700–12000 K, ±tint) miejsca, przy którym leży najwięcej próbek (histogram rg
 * próbek). W przeciwieństwie do „gray world” nie przesuwa go jedna duża barwna powierzchnia, a pod mocnym
 * światłem nie gubi szarości. Krzywa Plancka z przybliżenia Kima i in. (2002).
 */
@Suppress("MagicNumber") // współczynniki publicznego przybliżenia i rozmiary histogramu
internal object GreySearch {
    private const val BINS = 128
    private const val RADIUS = 2
    private const val MIN_WEIGHT = 8.0
    private const val MIN_SUM = 0.03f
    private val CANDIDATES: List<DoubleArray> = buildList {
        var mired = 1e6 / 12000
        while (mired <= 1e6 / 1700) {
            val (x, y) = planckXy(1e6 / mired)
            for (dy in -2..2) add(rgb(x, y + dy * 0.008))
            mired += 8.0
        }
    }

    /** Oświetlenie (liniowe RGB) z najgęstszego skupiska próbek przy krzywej Plancka albo null. */
    fun densestGrey(s: Sample, mask: BooleanArray): DoubleArray? {
        val hist = DoubleArray(BINS * BINS)
        for (i in 0 until s.count) {
            val r = s.rgb[3 * i]
            val g = s.rgb[3 * i + 1]
            val sum = r + g + s.rgb[3 * i + 2]
            if (mask[i] && !s.clipped[i] && sum >= MIN_SUM) hist[bin(r / sum) * BINS + bin(g / sum)] += 1.0
        }
        var best: DoubleArray? = null
        var bestScore = MIN_WEIGHT
        for (c in CANDIDATES) {
            val sum = c.sum()
            val br = bin((c[0] / sum).toFloat())
            val bg = bin((c[1] / sum).toFloat())
            var score = 0.0
            for (i in maxOf(0, br - RADIUS)..minOf(BINS - 1, br + RADIUS)) {
                for (j in maxOf(0, bg - RADIUS)..minOf(BINS - 1, bg + RADIUS)) score += hist[i * BINS + j]
            }
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    private fun bin(v: Float) = (v * BINS).toInt().coerceIn(0, BINS - 1)

    private fun rgb(x: Double, y: Double): DoubleArray {
        val xyz = doubleArrayOf(x / y, 1.0, (1 - x - y) / y)
        val m = doubleArrayOf(
            3.2404542, -1.5371385, -0.4985314, -0.9692660, 1.8760108, 0.0415560, 0.0556434, -0.2040259, 1.0572252
        )
        return Cat.mul3(m, xyz).map { it.coerceAtLeast(1e-4) }.toDoubleArray()
    }

    /** Chromatyczność xy ciała doskonale czarnego (Kim i in. 2002), 1667–25000 K. */
    fun planckXy(t: Double): Pair<Double, Double> {
        val x = if (t <= 4000) {
            -0.2661239e9 / t.pow(3) - 0.2343589e6 / t.pow(2) + 0.8776956e3 / t + 0.179910
        } else {
            -3.0258469e9 / t.pow(3) + 2.1070379e6 / t.pow(2) + 0.2226347e3 / t + 0.240390
        }
        val y = when {
            t <= 2222 -> -1.1063814 * x.pow(3) - 1.34811020 * x.pow(2) + 2.18555832 * x - 0.20219683
            t <= 4000 -> -0.9549476 * x.pow(3) - 1.37418593 * x.pow(2) + 2.09137015 * x - 0.16748867
            else -> 3.0817580 * x.pow(3) - 5.87338670 * x.pow(2) + 3.75112997 * x - 0.37001483
        }
        return x to y
    }
}
