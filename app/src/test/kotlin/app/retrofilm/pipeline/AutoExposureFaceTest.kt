package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** M: ekspozycja z „Przygotuj pod film” liczona po zdjęciu krzywej telefonu i nie przepala twarzy. */
class AutoExposureFaceTest {
    private val w = 256
    private val h = 256
    private val face = FaceBox(0.3f, 0.2f, 0.7f, 0.7f)

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Ciemne tło, jasna twarz (skóra [skin] w sRGB) z przepalonym czołem – typowy JPEG z telefonu. */
    private fun brightFace(skin: Int, bg: Int = 60) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val inFace = x in 77..179 && y in 51..179
        when {
            inFace && y < 70 -> argb(255, 250, 245)
            inFace -> argb(skin, (skin * 0.8).toInt(), (skin * 0.68).toInt())
            else -> argb(bg, bg, bg)
        }
    }

    private fun lin(v: Int) = AdjustmentProcessor.srgbToLinear(v / 255f)

    @Test fun brightFaceIsNotPushedTowardsClippingEvenForABrightFilm() {
        for (skin in listOf(170, 200, 225)) {
            // film o jasnej szarości docelowej (0,30) – wcześniej dawał do +0,9 EV i twarz w 255
            val ev = AutoEnhance.suggest(brightFace(skin), w, h, listOf(face), AutoContext(targetGrey = 0.30f)).exposure
            val out = lin(skin) * 2f.pow(ev)
            assertTrue(
                "skóra $skin: ev=$ev daje kanał R $out > ${AutoEnhance.FACE_CEILING}",
                out <= AutoEnhance.FACE_CEILING + 1e-3f
            )
        }
    }

    @Test fun alreadyBlownFaceIsNeverBrightened() {
        val ev = AutoEnhance.suggest(brightFace(252), w, h, listOf(face), AutoContext(targetGrey = 0.30f)).exposure
        assertTrue("ev=$ev", ev <= 0f)
    }

    @Test fun exposureIsMeasuredAfterCurveRemovalAtItsCurrentStrength() {
        val v = 180
        val px = IntArray(w * h) { argb(v, v, v) }
        for (inv in listOf(0f, 0.5f, 1f)) {
            val ev = AutoEnhance.suggest(px, w, h, context = AutoContext(inversion = inv)).exposure
            val grey = AdjustmentProcessor.srgbToLinear(PhoneCurve.invert(v / 255f, inv)) * 2f.pow(ev)
            assertTrue("inv=$inv ev=$ev szarość=$grey", abs(grey - FilmCalibration.MID_GREY) < 0.01f)
        }
        val e0 = AutoEnhance.suggest(px, w, h, context = AutoContext(inversion = 0f)).exposure
        val e1 = AutoEnhance.suggest(px, w, h, context = AutoContext(inversion = 1f)).exposure
        assertTrue("zdjęcie krzywej przyciemnia jasne półtony, więc ekspozycja mniej je ścina: $e0 vs $e1", e1 > e0)
    }
}
