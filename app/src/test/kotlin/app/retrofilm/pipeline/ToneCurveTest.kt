package app.retrofilm.pipeline

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToneCurveTest {
    private fun curve(vararg xy: Float) = ToneCurve(xy.toList().chunked(2).map { CurvePoint(it[0], it[1]) })

    private val sCurve = curve(0f, 0f, 0.25f, 0.18f, 0.75f, 0.82f, 1f, 1f)

    @Test fun identityCurveReturnsInput() {
        val c = ToneCurve()
        assertTrue(c.isIdentity)
        for (i in 0..20) assertEquals(i / 20f, c.eval(i / 20f), 1e-5f)
        val lut = c.lut(256)
        for (i in 0..256) assertEquals(i / 256f, lut[i], 1e-5f)
    }

    @Test fun curvePassesThroughItsPoints() {
        for (p in sCurve.points) assertEquals(p.y, sCurve.eval(p.x), 1e-5f)
    }

    @Test fun monotoneDataGivesMonotoneCurveWithoutOvershoot() {
        val steep = curve(0f, 0f, 0.5f, 0.95f, 1f, 1f)
        for (c in listOf(sCurve, steep, curve(0f, 0.2f, 0.4f, 0.2f, 0.6f, 0.8f, 1f, 0.8f))) {
            val lut = c.lut(512)
            for (i in 1 until lut.size) assertTrue("rośnie lub stała", lut[i] >= lut[i - 1] - 1e-6f)
            assertTrue(lut.all { it in 0f..1f })
        }
        val s = steep.lut(512)
        assertTrue("brak nadstrzału powyżej 1", s.max() <= 1f)
    }

    @Test fun sCurveDarkensShadowsAndBrightensHighlights() {
        assertTrue(sCurve.eval(0.25f) < 0.25f)
        assertTrue(sCurve.eval(0.75f) > 0.75f)
        assertEquals(0.5f, sCurve.eval(0.5f), 0.03f)
    }

    @Test fun movingKeepsOrderAndEndpointX() {
        val c = sCurve.moved(1, 0.9f, 0.3f)
        assertTrue("nie wychodzi poza sąsiada", c.points[1].x < c.points[2].x)
        val e = sCurve.moved(0, 0.4f, 0.1f)
        assertEquals(0f, e.points[0].x, 0f)
        assertEquals(0.1f, e.points[0].y, 1e-6f)
        assertSame("zły indeks bez zmian", sCurve, sCurve.moved(9, 0.5f, 0.5f))
    }

    @Test fun insertAndRemovePoints() {
        val (added, idx) = ToneCurve().inserted(0.5f, 0.6f)
        assertEquals(3, added.points.size)
        assertEquals(1, idx)
        assertEquals(0.6f, added.eval(0.5f), 1e-5f)
        assertEquals(2, added.removed(1).points.size)
        assertSame("końców nie usuwamy", added, added.removed(0))
        val (same, near) = added.inserted(0.51f, 0.9f)
        assertEquals("zbyt blisko istniejącego", 3, same.points.size)
        assertEquals(1, near)
        var c = ToneCurve()
        for (i in 1..30) c = c.inserted(i / 31f, 0.5f).first
        assertTrue(c.points.size <= ToneCurve.MAX_POINTS)
    }

    @Test fun withSetsValueAtPositionWithoutDuplicatingPoints() {
        val a = ToneCurve().with(0.5f, 0.7f)
        assertEquals(0.7f, a.eval(0.5f), 1e-5f)
        val b = a.with(0.52f, 0.4f)
        assertEquals("przesuwa punkt blisko zamiast dodawać", a.points.size, b.points.size)
        assertEquals(0.4f, b.points[1].y, 1e-6f)
    }

    @Test fun masterCurveContrastsPixelsAndIdentityStaysNeutral() {
        assertTrue(Adjustments(curves = ToneCurves()).isNeutral)
        val adj = Adjustments(curves = ToneCurves(master = sCurve))
        assertFalse(adj.isNeutral)
        val dark = AdjustmentsApplier.applyToPixel(Color.rgb(64, 64, 64), adj)
        val bright = AdjustmentsApplier.applyToPixel(Color.rgb(192, 192, 192), adj)
        assertTrue(Color.red(dark) < 64 - 4)
        assertTrue(Color.red(bright) > 192 + 4)
        val black = AdjustmentsApplier.applyToPixel(Color.rgb(0, 0, 0), adj)
        val white = AdjustmentsApplier.applyToPixel(Color.rgb(255, 255, 255), adj)
        assertEquals(0, Color.red(black))
        assertEquals(255, Color.red(white))
    }

    @Test fun channelCurveChangesOnlyItsChannel() {
        val lift = curve(0f, 0f, 0.5f, 0.7f, 1f, 1f)
        val out = AdjustmentsApplier.applyToPixel(
            Color.rgb(128, 128, 128),
            Adjustments(curves = ToneCurves(red = lift))
        )
        assertTrue(Color.red(out) > 160)
        assertEquals(128f, Color.green(out).toFloat(), 2f)
        assertEquals(128f, Color.blue(out).toFloat(), 2f)
    }
}
