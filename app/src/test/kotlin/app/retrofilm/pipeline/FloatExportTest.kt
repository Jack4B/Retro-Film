package app.retrofilm.pipeline

import android.graphics.Bitmap
import app.retrofilm.core.engine.Lut
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Eksport float: LUT w potoku, jedna kwantyzacja, ~10 bitów między etapami, wyjście Display P3. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatExportTest {
    private fun gradient(w: Int, h: Int) = Bitmap.createBitmap(
        IntArray(w * h) { i ->
            val v = (i % w) * 255 / (w - 1)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        },
        w,
        h,
        Bitmap.Config.ARGB_8888
    )

    private fun px(b: Bitmap): IntArray = IntArray(b.width * b.height).also {
        b.getPixels(it, 0, b.width, 0, 0, b.width, b.height)
    }

    @Test
    fun fracRoundTripWithinEighthLevel() {
        for (k in 0..100) {
            val f = -0.5f + k / 100f * 0.999f
            assertTrue(abs(Frac.decode(Frac.encode(f)) - f) <= 0.126f)
        }
    }

    @Test
    fun identityLutKeepsImage() {
        val src = gradient(256, 8)
        val out = FloatExport.look(src, Lut.identity(17), 1f, Adjustments(), OutputSpace.Srgb)
        val a = px(src)
        val b = px(out)
        for (i in a.indices) assertTrue(abs((a[i] and 0xFF) - (b[i] and 0xFF)) <= 1)
    }

    @Test
    fun steepLutWithFinishingHasNoBandingSteps() {
        // mocny kontrast w LUT + wykończenie: pośrednia 8-bitowa kwantyzacja dawałaby skoki > 2 poziomy
        val lut = Lut.generate(33) { r, g, b -> Triple(r.pow(2.2f), g.pow(2.2f), b.pow(2.2f)) }
        val out = FloatExport.look(gradient(1024, 16), lut, 1f, Adjustments(vignette = 0.01f), OutputSpace.Srgb)
        val p = px(out)
        val row = IntArray(1024) { x -> (0 until 16).sumOf { y -> p[y * 1024 + x] and 0xFF } }
        var maxStep = 0
        for (x in 1 until 1024) maxStep = maxOf(maxStep, abs(row[x] - row[x - 1]) / 16)
        assertTrue("skok $maxStep", maxStep <= 2)
    }

    @Test
    fun p3PathKeepsInGamutColorsThroughIdentityLut() {
        val stage = LutStage(Lut.identity(33), 1f, wide = true)
        val planes = Planes.of(1)
        planes.r[0] = 200f
        planes.g[0] = 120f
        planes.b[0] = 60f
        stage.apply(planes, 0, 1)
        assertEquals(200f, planes.r[0], 1.5f)
        assertEquals(120f, planes.g[0], 1.5f)
        assertEquals(60f, planes.b[0], 1.5f)
    }

    @Test
    fun p3PathKeepsOutOfSrgbSaturation() {
        // czysta czerwień P3 leży poza sRGB; tożsamościowy LUT nie może jej przyciąć do czerwieni sRGB
        val stage = LutStage(Lut.identity(33), 1f, wide = true)
        val planes = Planes.of(1)
        planes.r[0] = 255f
        stage.apply(planes, 0, 1)
        assertEquals(255f, planes.r[0], 1.5f)
        assertEquals(0f, planes.g[0], 1.5f)
        assertEquals(0f, planes.b[0], 1.5f)
    }

    @Test
    fun workingP3IsLabelledSrgbWithP3Values() {
        val src = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
        val work = FloatExport.working(src, OutputSpace.DisplayP3)
        val c = work.getPixel(1, 1)
        // sRGB czerwień w P3 ≈ (234, 51, 35)
        assertEquals(234, c shr 16 and 0xFF, 3)
        assertTrue((c shr 8 and 0xFF) in 40..60)
    }

    private fun assertEquals(e: Int, a: Int, d: Int) = assertTrue("$a vs $e", abs(a - e) <= d)
}
