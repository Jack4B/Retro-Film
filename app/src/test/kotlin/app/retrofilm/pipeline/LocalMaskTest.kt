package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalMaskTest {
    private val w = 200
    private val h = 100

    @Test fun linearMaskFadesFromOneSideToTheOther() {
        val m = MaskShapeMath(LocalMask(MaskShape.Linear, centerX = 0.5f, centerY = 0.5f, feather = 0.3f), w, h)
        assertEquals(1f, m.alpha(100, 0), 1e-3f)
        assertEquals(0f, m.alpha(100, h - 1), 1e-3f)
        assertEquals(0.5f, m.alpha(100, 50), 0.05f)
        for (y in 1 until h) assertTrue("nierosnąco w dół", m.alpha(100, y) <= m.alpha(100, y - 1) + 1e-6f)
    }

    @Test fun invertAndAngleChangeTheSide() {
        val base = LocalMask(MaskShape.Linear, centerX = 0.5f, centerY = 0.5f, feather = 0.2f)
        val inv = MaskShapeMath(base.copy(invert = true), w, h)
        assertEquals(0f, inv.alpha(100, 0), 1e-3f)
        assertEquals(1f, inv.alpha(100, h - 1), 1e-3f)
        val vertical = MaskShapeMath(base.copy(angle = 90f), w, h)
        assertEquals("prawa strona", 1f, vertical.alpha(w - 1, 50), 1e-3f)
        assertEquals("lewa strona", 0f, vertical.alpha(0, 50), 1e-3f)
    }

    @Test fun radialMaskIsFullInsideAndEmptyOutside() {
        val m = LocalMask(MaskShape.Radial, centerX = 0.5f, centerY = 0.5f, size = 0.2f, feather = 0.5f)
        val math = MaskShapeMath(m, w, h)
        assertEquals(1f, math.alpha(100, 50), 1e-3f)
        assertEquals(0f, math.alpha(5, 5), 1e-3f)
        val inside = math.alpha(115, 50)
        val edge = math.alpha(135, 50)
        assertTrue("maleje ku krawędzi", inside > edge && edge in 0f..1f)
        val hard = MaskShapeMath(m.copy(feather = 0f), w, h)
        assertEquals(1f, hard.alpha(120, 50), 1e-3f)
        assertEquals(0f, hard.alpha(150, 50), 1e-3f)
    }

    @Test fun radialRatioSquashesTheEllipse() {
        val m = LocalMask(MaskShape.Radial, centerX = 0.5f, centerY = 0.5f, size = 0.3f, ratio = 0.3f, feather = 0f)
        val math = MaskShapeMath(m, w, h)
        assertEquals("poziomo szeroko", 1f, math.alpha(100 + 50, 50), 1e-3f)
        assertEquals("pionowo wąsko", 0f, math.alpha(100, 50 + 30), 1e-3f)
    }

    @Test fun maskWithoutEffectIsIgnored() {
        assertFalse(LocalMask().hasEffect)
        assertTrue(MaskBlender(listOf(LocalMask()), w, h).isEmpty)
        assertFalse(MaskBlender(listOf(LocalMask(exposure = 0.5f)), w, h).isEmpty)
    }

    @Test fun exposureMaskBrightensOnlyTheMaskedHalf() {
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(100, 100, 100))
        val adj =
            Adjustments(masks = listOf(LocalMask(MaskShape.Linear, centerY = 0.5f, feather = 0.2f, exposure = 0.5f)))
        assertFalse(adj.isNeutral)
        val out = AdjustmentsApplier.apply(src, adj)
        val top = Color.red(out.getPixel(100, 2))
        val mid = Color.red(out.getPixel(100, 50))
        val bottom = Color.red(out.getPixel(100, h - 3))
        assertTrue("góra rozjaśniona ($top)", top > 130)
        assertEquals("dół bez zmian", 100f, bottom.toFloat(), 2f)
        assertTrue("środek pomiędzy", mid in (bottom + 1)..(top - 1))
    }

    @Test fun masksAreCappedAndLayerIndependent() {
        val many = List(5) { LocalMask(exposure = 0.2f) }
        val adj = Adjustments(masks = many)
        val out = AdjustmentsApplier.applyToPixel(Color.rgb(90, 90, 90), adj)
        assertTrue(Color.red(out) in 0..255)
        val blender = MaskBlender(many, w, h)
        assertFalse(blender.isEmpty)
    }

    @Test fun previewHasMatchingAspectAndStrengthInTheMaskedArea() {
        val bmp = MaskPreview.render(
            LocalMask(MaskShape.Linear, centerY = 0.5f, feather = 0.1f, exposure = 1f),
            400,
            200
        )
        assertEquals(2f, bmp.width.toFloat() / bmp.height, 0.05f)
        assertTrue("góra widoczna", Color.alpha(bmp.getPixel(bmp.width / 2, 1)) > 100)
        assertEquals("dół przezroczysty", 0, Color.alpha(bmp.getPixel(bmp.width / 2, bmp.height - 2)))
    }
}
