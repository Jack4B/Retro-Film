package app.retrofilm.pipeline

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebandTest {
    private val width = 400
    private val rows = 40
    private val radius = 16

    private fun planesOf(value: (x: Int, y: Int) -> Float): Planes {
        val p = Planes.of(width * rows)
        for (y in 0 until rows) {
            for (x in 0 until width) {
                val v = value(x, y)
                p.r[y * width + x] = v
                p.g[y * width + x] = v
                p.b[y * width + x] = v
            }
        }
        return p
    }

    private fun meanError(p: Planes, ideal: (Int) -> Float): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until rows) {
            for (x in 2 * radius until width - 2 * radius) {
                sum += abs(p.g[y * width + x] - ideal(x))
                n++
            }
        }
        return sum / n
    }

    @Test fun smoothRampStaircaseGetsCloserToIdeal() {
        val ideal = { x: Int -> 100f + 0.03f * x }
        val p = planesOf { x, _ -> Math.round(ideal(x)).toFloat() }
        val before = meanError(p, ideal)
        Deband.applyPlanes(p, width * rows, width, radius)
        val after = meanError(p, ideal)
        assertTrue("błąd przed $before, po $after", after < 0.6 * before)
    }

    @Test fun hardEdgeAndTextureAreUntouched() {
        val edge = planesOf { x, _ -> if (x < width / 2) 60f else 180f }
        val edgeCopy = edge.g.copyOf()
        Deband.applyPlanes(edge, width * rows, width, radius)
        for (i in edgeCopy.indices) assertEquals(edgeCopy[i], edge.g[i], 0.01f)

        val texture = planesOf { x, y -> if ((x + y) % 2 == 0) 100f else 110f }
        val textureCopy = texture.g.copyOf()
        Deband.applyPlanes(texture, width * rows, width, radius)
        for (i in textureCopy.indices) assertEquals(textureCopy[i], texture.g[i], 0.01f)
    }

    @Test fun changeIsLimited() {
        val p = planesOf { x, _ -> if ((x / 8) % 2 == 0) 100f else 102f }
        val copy = p.g.copyOf()
        Deband.applyPlanes(p, width * rows, width, radius)
        for (i in copy.indices) assertTrue(abs(p.g[i] - copy[i]) <= 3.0001f)
    }

    @Test fun radiusScalesWithImageSize() {
        assertEquals(8, Deband.radiusFor(300))
        assertEquals(33, Deband.radiusFor(4000))
    }
}
