package app.retrofilm.pipeline

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Podział korekt na „korekty” (przed filmem) i „wykończenie” (po filmie), D15. */
class AdjustmentsSplitTest {
    private val corrections = setOf(
        "exposure", "contrast", "saturation", "temperature", "tint", "highlights", "shadows", "clarity", "dehaze",
        "whites", "blacks", "texture", "denoise", "lumaDenoise", "hsl", "masks"
    )
    private val finishing = setOf(
        "fade", "grain", "grainSize", "vignette", "sharpness", "sharpRadius", "sharpDetail", "sharpMasking",
        "sharpMode", "sharpMaskPreview", "shadowHue", "shadowAmount", "highlightHue", "highlightAmount",
        "toneBalance", "curves"
    )

    @Test fun everyFieldBelongsToExactlyOneGroup() {
        val fields = Adjustments::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        val both = corrections intersect finishing
        assertTrue("pola w obu grupach: $both", both.isEmpty())
        assertEquals("nowe pole Adjustments przypisz do korekt albo wykończenia", fields, corrections + finishing)
    }

    @Test fun noneStaysNone() {
        assertEquals(Adjustments.None, Adjustments.None.corrections())
        assertEquals(Adjustments.None, Adjustments.None.finishing())
    }

    @Test fun finishingOnlyEditLeavesCorrectionsNeutral() {
        val adj = Adjustments(grain = 0.3f, grainSize = 0.8f, vignette = 0.2f, sharpness = 0.4f, fade = 0.1f)
        assertTrue(adj.corrections().isNeutral)
        assertEquals(adj, adj.finishing())
    }

    @Test fun correctionsOnlyEditLeavesFinishingNeutral() {
        val adj = Adjustments(exposure = 0.4f, contrast = 0.2f, saturation = 0.1f, shadows = 0.5f, dehaze = 0.3f)
        assertTrue(adj.finishing().isNeutral)
        assertEquals(adj, adj.corrections())
    }

    @Test fun mixedEditSplitsWithoutLosingAnything() {
        val adj = Adjustments(exposure = 0.4f, clarity = 0.3f, grain = 0.3f, sharpness = 0.5f, shadowAmount = 0.2f)
        val c = adj.corrections()
        val f = adj.finishing()
        assertEquals(0.4f, c.exposure, 0f)
        assertEquals(0.3f, c.clarity, 0f)
        assertEquals(0f, c.grain, 0f)
        assertEquals(0f, c.sharpness, 0f)
        assertEquals(0.3f, f.grain, 0f)
        assertEquals(0.5f, f.sharpness, 0f)
        assertEquals(0.2f, f.shadowAmount, 0f)
        assertEquals(0f, f.exposure, 0f)
    }
}
