package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerspectiveTest {
    private fun normalized(m: DoubleArray) = DoubleArray(9) { m[it] / m[8] }

    private fun times(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        (0 until 3).sumOf { k -> a[(i / 3) * 3 + k] * b[k * 3 + i % 3] }
    }

    @Test fun zeroSlidersGiveIdentity() {
        val m = normalized(Perspective.matrix(4000.0, 3000.0, 0f, 0f))
        val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        for (i in 0 until 9) assertEquals(id[i], m[i], 1e-12)
    }

    @Test fun oppositeSingleAxisAnglesCancel() {
        for ((v, h) in listOf(0.6f to 0f, 0f to -0.4f)) {
            val prod = normalized(
                times(Perspective.matrix(1200.0, 800.0, v, h), Perspective.matrix(1200.0, 800.0, -v, -h))
            )
            val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
            for (i in 0 until 9) assertEquals("element $i", id[i], prod[i], 1e-9)
        }
    }

    @Test fun verticalTiltNarrowsOneEdgeLikeAKeystone() {
        val q = Perspective.quad(1000.0, 800.0, Perspective.matrix(1000.0, 800.0, 0.7f, 0f))
        val topWidth = q[2] - q[0]
        val bottomWidth = q[4] - q[6]
        assertTrue("trapez: górna i dolna krawędź różnej długości", Math.abs(topWidth - bottomWidth) > 40.0)
        assertEquals("symetria względem pionu", q[0] + q[2], 0.0, 1e-6)
    }

    @Test fun bestWindowFitsInsideQuadAndKeepsRatio() {
        val w = 3000.0
        val h = 2000.0
        val quad = Perspective.quad(w, h, Perspective.matrix(w, h, 0.5f, 0.2f))
        val ratio = w / h
        val fit = CropFit(quad, ratio, 0.05)
        val best = fit.best()
        assertTrue(best.width > 500.0 && best.width < w)
        assertTrue("mieści się", fit.fits(best.centerX, best.centerY, best.width))
        assertTrue("jest największe", !fit.fits(best.centerX, best.centerY, best.width * 1.02))
    }

    @Test fun cropWithPerspectiveIsSmallerAndNeverLeavesTheImage() {
        val g = Geometry(perspV = 0.6f, straighten = 3f)
        val (cw, ch) = GeometryMath.outputSize(4000, 3000, g)
        assertTrue(cw < 4000 && ch < 3000)
        assertEquals("proporcje oryginału", 4000f / 3000f, cw.toFloat() / ch, 0.01f)
        val crop = GeometryMath.crop(4000, 3000, g)
        val m = Perspective.matrix(4000.0, 3000.0, 0.6f, 0f)
        val fit = CropFit(Perspective.quad(4000.0, 3000.0, m), 4.0 / 3.0, 3.0 * Math.PI / 180.0)
        assertTrue(fit.fits(crop.offsetX.toDouble(), crop.offsetY.toDouble(), crop.width.toDouble() - 0.5))
    }

    @Test fun panMovesWindowButStaysInside() {
        val base = Geometry(perspV = 0.5f, zoom = 2f)
        val left = GeometryMath.crop(2000, 1500, base.copy(panX = -1f))
        val right = GeometryMath.crop(2000, 1500, base.copy(panX = 1f))
        assertTrue("przesunięcie działa", right.offsetX - left.offsetX > 50f)
        val m = Perspective.matrix(2000.0, 1500.0, 0.5f, 0f)
        val fit = CropFit(Perspective.quad(2000.0, 1500.0, m), 2000.0 / 1500.0, 0.0)
        for (c in listOf(left, right)) {
            assertTrue(fit.fits(c.offsetX.toDouble(), c.offsetY.toDouble(), c.width.toDouble() - 0.5))
        }
    }

    @Test fun neutralPerspectiveKeepsOldCropMath() {
        val a = GeometryMath.crop(4000, 3000, Geometry(straighten = 5f))
        val b = GeometryMath.crop(4000, 3000, Geometry(straighten = 5f, perspV = 0f))
        assertEquals(a.width, b.width, 0f)
        assertTrue(Geometry(perspH = 0.1f).hasPerspective)
        assertTrue(!Geometry(perspH = 0.1f).isNeutral)
    }
}
