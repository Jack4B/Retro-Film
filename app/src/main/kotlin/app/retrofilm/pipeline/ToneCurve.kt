package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Punkt krzywej: wejście [x] i wyjście [y], oba 0..1 (kodowanie gamma). */
data class CurvePoint(val x: Float, val y: Float)

/**
 * Krzywa tonalna jak w Lightroomie / Photoshopie: punkty kontrolne rosnące po x, pierwszy w x = 0 i ostatni w x = 1.
 * Interpolacja to monotoniczny sześcienny splajn Hermite'a (Fritsch i Carlson, PCHIP): przechodzi przez punkty,
 * nie ma nadstrzałów między nimi (krzywa przez rosnące punkty jest rosnąca, więc tony się nie odwracają) i jest
 * gładka w pierwszej pochodnej, w przeciwieństwie do splajnu naturalnego, który faluje między punktami.
 */
data class ToneCurve(val points: List<CurvePoint> = IDENTITY) {
    val isIdentity: Boolean get() = points.all { abs(it.x - it.y) < EPS }

    /** Wartość krzywej w [x] (0..1), przycięta do 0..1. */
    fun eval(x: Float): Float {
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }
        return hermite(xs, ys, slopes(xs, ys), x.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    /** Tablica [size] + 1 wartości krzywej dla x = i / size (do szybkiego odczytu z interpolacją liniową). */
    fun lut(size: Int): FloatArray {
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }
        val m = slopes(xs, ys)
        return FloatArray(size + 1) { hermite(xs, ys, m, it.toFloat() / size).coerceIn(0f, 1f) }
    }

    /** Przesuwa punkt [index]; końce zostają w x = 0 / 1, reszta nie wychodzi poza sąsiadów. */
    fun moved(index: Int, x: Float, y: Float): ToneCurve {
        if (index !in points.indices) return this
        val last = points.lastIndex
        val nx = if (index == 0 || index == last) {
            points[index].x
        } else {
            val lo = points[index - 1].x + MIN_GAP
            val hi = points[index + 1].x - MIN_GAP
            if (lo >= hi) points[index].x else x.coerceIn(lo, hi)
        }
        return ToneCurve(points.toMutableList().also { it[index] = CurvePoint(nx, y.coerceIn(0f, 1f)) })
    }

    /** Dodaje punkt w ([x], [y]); zwraca nową krzywą i indeks punktu (przy zbyt bliskim sąsiedzie – jego indeks). */
    fun inserted(x: Float, y: Float): Pair<ToneCurve, Int> {
        val near = points.indexOfFirst { abs(it.x - x) < MIN_GAP }
        if (near >= 0 || points.size >= MAX_POINTS) return this to nearest(x)
        val at = points.indexOfFirst { it.x > x }
        val list = points.toMutableList().also { it.add(at, CurvePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))) }
        return ToneCurve(list) to at
    }

    /** Usuwa punkt [index] (końce zostają). */
    fun removed(index: Int): ToneCurve =
        if (index <= 0 || index >= points.lastIndex) this else ToneCurve(points.filterIndexed { i, _ -> i != index })

    /** Indeks punktu najbliższego [x]. */
    fun nearest(x: Float): Int = points.indices.minBy { abs(points[it].x - x) }

    /** Ustawia krzywą tak, by w [x] miała wartość [y]: przesuwa punkt blisko [x] albo dodaje nowy. */
    fun with(x: Float, y: Float): ToneCurve {
        val near = points.indexOfFirst { abs(it.x - x) < SNAP }
        return if (near > 0 && near < points.lastIndex) moved(near, points[near].x, y) else inserted(x, y).first
    }

    companion object {
        val IDENTITY = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        const val MAX_POINTS = 12
        const val MIN_GAP = 0.03f
        private const val SNAP = 0.04f
        private const val EPS = 1e-4f
        private const val THREE = 3f
        private const val TWO = 2f

        /** Nachylenia w punktach (Fritsch–Carlson / Fritsch–Butland: średnia harmoniczna ważona, 0 przy ekstremum). */
        internal fun slopes(xs: FloatArray, ys: FloatArray): FloatArray {
            val n = xs.size
            val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
            val h = FloatArray(n - 1) { xs[it + 1] - xs[it] }
            val m = FloatArray(n)
            if (n == 2) {
                m[0] = d[0]
                m[1] = d[0]
                return m
            }
            for (k in 1 until n - 1) {
                m[k] = if (d[k - 1] * d[k] <= 0f) {
                    0f
                } else {
                    val w1 = TWO * h[k] + h[k - 1]
                    val w2 = h[k] + TWO * h[k - 1]
                    (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
                }
            }
            val last = n - 1
            m[0] = endSlope(h[0], h[1], d[0], d[1])
            m[last] = endSlope(h[last - 1], h[last - 2], d[last - 1], d[last - 2])
            return m
        }

        /** Trójpunktowe nachylenie na końcu z zachowaniem kształtu (nie przekracza trzykrotności siecznej). */
        private fun endSlope(h0: Float, h1: Float, d0: Float, d1: Float): Float {
            var s = ((TWO * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
            if (s * d0 <= 0f) {
                s = 0f
            } else if (d0 * d1 <= 0f && abs(s) > abs(THREE * d0)) {
                s = THREE * d0
            }
            return s
        }

        private fun hermite(xs: FloatArray, ys: FloatArray, m: FloatArray, x: Float): Float {
            var k = xs.size - 2
            for (i in 0 until xs.size - 1) {
                if (x <= xs[i + 1]) {
                    k = i
                    break
                }
            }
            val h = xs[k + 1] - xs[k]
            val t = ((x - xs[k]) / h).coerceIn(0f, 1f)
            val t2 = t * t
            val t3 = t2 * t
            return (TWO * t3 - THREE * t2 + 1f) * ys[k] + (t3 - TWO * t2 + t) * h * m[k] +
                (THREE * t2 - TWO * t3) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
        }
    }
}

/** Zestaw krzywych: główna (na wszystkich kanałach) oraz osobne dla czerwonego, zielonego i niebieskiego. */
data class ToneCurves(
    val master: ToneCurve = ToneCurve(),
    val red: ToneCurve = ToneCurve(),
    val green: ToneCurve = ToneCurve(),
    val blue: ToneCurve = ToneCurve()
) {
    val isNeutral: Boolean get() = master.isIdentity && red.isIdentity && green.isIdentity && blue.isIdentity

    fun channel(i: Int): ToneCurve = when (i) {
        CHANNEL_MASTER -> master
        CHANNEL_RED -> red
        CHANNEL_GREEN -> green
        else -> blue
    }

    fun with(i: Int, curve: ToneCurve): ToneCurves = when (i) {
        CHANNEL_MASTER -> copy(master = curve)
        CHANNEL_RED -> copy(red = curve)
        CHANNEL_GREEN -> copy(green = curve)
        else -> copy(blue = curve)
    }

    companion object {
        const val CHANNEL_MASTER = 0
        const val CHANNEL_RED = 1
        const val CHANNEL_GREEN = 2
        const val CHANNEL_BLUE = 3
    }
}

/**
 * Krzywe zamienione na tablice dla pikseli: wartość kanału przechodzi przez krzywą główną, a potem przez krzywą
 * swojego koloru (jak w Photoshopie); odczyt z interpolacją liniową, więc 1024 próbki nie dają schodków.
 */
internal class CurveLut(curves: ToneCurves) {
    private val master = curves.master.lut(SIZE)
    private val luts = arrayOf(curves.red.lut(SIZE), curves.green.lut(SIZE), curves.blue.lut(SIZE))

    fun apply(rgb: FloatArray) {
        for (k in 0..2) rgb[k] = read(luts[k], read(master, rgb[k]))
    }

    private fun read(table: FloatArray, v: Float): Float {
        val p = min(max(v, 0f), 1f) * SIZE
        val i = min(p.toInt(), SIZE - 1)
        return table[i] + (table[i + 1] - table[i]) * (p - i)
    }

    private companion object {
        const val SIZE = 1024
    }
}
