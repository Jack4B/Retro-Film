package app.retrofilm.pipeline

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Największy kadr o zadanych proporcjach (obrócony o kąt prostowania), który mieści się w wypukłym czworokącie
 * (zdjęcie po korekcie perspektywy). Okno mieści się w czworokącie, gdy mieszczą się jego cztery narożniki.
 * Maksymalna szerokość to wklęsła funkcja środka okna, więc wspinaczka z malejącym krokiem znajduje optimum.
 */
@Suppress("MagicNumber") // 4 narożniki i połowy wymiarów
internal class CropFit(private val quad: DoubleArray, private val ratio: Double, angle: Double) {
    private val c = cos(angle)
    private val s = sin(angle)
    private val sign = if (area() >= 0.0) 1.0 else -1.0

    /** Zakres przesunięcia środka okna w każdą stronę (px), przy którym okno nadal się mieści. */
    class Ranges(val left: Double, val right: Double, val up: Double, val down: Double)

    /** Środek i szerokość największego okna. */
    class Best(val centerX: Double, val centerY: Double, val width: Double)

    fun best(): Best {
        var cx = quad.filterIndexed { i, _ -> i % 2 == 0 }.average()
        var cy = quad.filterIndexed { i, _ -> i % 2 == 1 }.average()
        var width = widthAt(cx, cy)
        var step = extent() / START_DIV
        val tolerance = extent() * TOLERANCE
        var iteration = 0
        while (step > tolerance && iteration++ < ITERATIONS) {
            var moved = false
            for (k in 0 until NEIGHBOURS) {
                val nx = cx + step * DX[k]
                val ny = cy + step * DY[k]
                val w = widthAt(nx, ny)
                if (w > width) {
                    width = w
                    cx = nx
                    cy = ny
                    moved = true
                }
            }
            if (!moved) step /= 2.0
        }
        return Best(cx, cy, width)
    }

    fun ranges(cx: Double, cy: Double, width: Double): Ranges = Ranges(
        shift(cx, cy, width, -1.0, 0.0),
        shift(cx, cy, width, 1.0, 0.0),
        shift(cx, cy, width, 0.0, -1.0),
        shift(cx, cy, width, 0.0, 1.0)
    )

    /** Największa szerokość okna o środku ([cx], [cy]); 0, gdy środek leży poza czworokątem. */
    fun widthAt(cx: Double, cy: Double): Double {
        if (!fits(cx, cy, 0.0)) return 0.0
        var lo = 0.0
        var hi = extent() * 2.0
        repeat(BISECT) {
            val mid = (lo + hi) / 2.0
            if (fits(cx, cy, mid)) lo = mid else hi = mid
        }
        return lo
    }

    fun fits(cx: Double, cy: Double, width: Double): Boolean {
        val hw = width / 2.0
        val hh = width / ratio / 2.0
        for (i in 0 until 4) {
            val dx = if (i == 1 || i == 2) hw else -hw
            val dy = if (i >= 2) hh else -hh
            if (!inside(cx + c * dx + s * dy, cy - s * dx + c * dy)) return false
        }
        return true
    }

    private fun shift(cx: Double, cy: Double, width: Double, dx: Double, dy: Double): Double {
        var lo = 0.0
        var hi = extent()
        repeat(BISECT) {
            val mid = (lo + hi) / 2.0
            if (fits(cx + dx * mid, cy + dy * mid, width)) lo = mid else hi = mid
        }
        return lo
    }

    private fun inside(x: Double, y: Double): Boolean {
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (quad[2 * j] - quad[2 * i]) * (y - quad[2 * i + 1]) -
                (quad[2 * j + 1] - quad[2 * i + 1]) * (x - quad[2 * i])
            if (cross * sign < -EPS) return false
        }
        return true
    }

    private fun area(): Double {
        var a = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            a += quad[2 * i] * quad[2 * j + 1] - quad[2 * j] * quad[2 * i + 1]
        }
        return a
    }

    private fun extent(): Double {
        var m = 0.0
        for (v in quad) m = max(m, kotlin.math.abs(v))
        return m
    }

    private companion object {
        const val ITERATIONS = 400
        const val TOLERANCE = 1e-4
        const val BISECT = 40
        const val START_DIV = 4.0
        const val NEIGHBOURS = 8
        const val EPS = 1e-6
        val DX = doubleArrayOf(1.0, -1.0, 0.0, 0.0, 1.0, 1.0, -1.0, -1.0)
        val DY = doubleArrayOf(0.0, 0.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0)
    }
}
