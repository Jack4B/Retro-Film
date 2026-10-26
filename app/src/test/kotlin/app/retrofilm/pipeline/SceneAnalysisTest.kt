package app.retrofilm.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneAnalysisTest {
    private val gw = 100
    private val gh = 80

    private fun lin(v: Int) = AdjustmentProcessor.srgbToLinear(v / 255f)

    /** Siatka próbek: [paint] zwraca kolor sRGB (r, g, b) dla komórki (x, y). */
    private fun sample(paint: (Int, Int) -> Triple<Int, Int, Int>): Sample {
        val rgb = FloatArray(3 * gw * gh)
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                val (r, g, b) = paint(x, y)
                val i = 3 * (y * gw + x)
                rgb[i] = lin(r)
                rgb[i + 1] = lin(g)
                rgb[i + 2] = lin(b)
            }
        }
        return Sample(rgb, gw, gh)
    }

    private val gray = Triple(128, 128, 128)
    private val skin = Triple(224, 172, 140)

    @Test fun skinPatchMakesAPortraitWithoutFaces() {
        val a = SceneAnalyzer.analyze(sample { x, y -> if (x in 30..69 && y in 20..59) skin else gray })
        assertEquals(AutoScene.Portrait, a.scene)
        assertEquals(0.2f, a.skinRatio, 0.03f)
        assertFalse(a.hasFaces)
    }

    @Test fun faceBoxLimitsSkinToItsInnerPart() {
        val face = FaceBox(0.3f, 0.25f, 0.7f, 0.75f)
        val a = SceneAnalyzer.analyze(sample { x, y -> if (x in 30..69 && y in 20..59) skin else gray }, listOf(face))
        assertTrue(a.hasFaces)
        assertEquals(AutoScene.Portrait, a.scene)
        // wnętrze ramki: 60% × 70% ramki 0,4×0,5 = 0,084 obrazu
        assertEquals(0.084f, a.skinRatio, 0.02f)
    }

    @Test fun skyInTheUpperPartIsRecognisedAndPlacedOutsideUsablePixels() {
        val a = SceneAnalyzer.analyze(sample { _, y -> if (y < 40) Triple(120, 170, 230) else Triple(110, 100, 90) })
        assertEquals(0.5f, a.skyRatio, 0.05f)
        assertEquals(AutoScene.Landscape, a.scene)
        assertTrue(a.usableRatio < 0.6f)
    }

    @Test fun skyColourInTheLowerPartIsNotSky() {
        val a = SceneAnalyzer.analyze(sample { _, y -> if (y >= 50) Triple(120, 170, 230) else Triple(110, 100, 90) })
        assertEquals(0f, a.skyRatio, 0.01f)
    }

    @Test fun foliageMakesALandscape() {
        val a = SceneAnalyzer.analyze(sample { x, _ -> if (x < 50) Triple(60, 140, 50) else gray })
        assertEquals(AutoScene.Landscape, a.scene)
    }

    @Test fun singleColourSceneIsDominant() {
        val a = SceneAnalyzer.analyze(sample { x, y -> if (x < 5 && y < 5) gray else Triple(225, 120, 50) })
        assertTrue("dominanta ${a.dominance}", a.dominance > 0.8f)
        assertEquals(AutoScene.Dominant, a.scene)
    }

    @Test fun balancedColoursHaveLowDominance() {
        val palette =
            listOf(
                Triple(200, 40, 40),
                Triple(40, 160, 60),
                Triple(50, 80, 210),
                Triple(220, 200, 50),
                Triple(150, 60, 170),
                Triple(40, 170, 180)
            )
        val a = SceneAnalyzer.analyze(sample { x, y -> palette[(x / 10 + y / 10) % palette.size] })
        assertTrue("dominanta ${a.dominance}", a.dominance < 0.5f)
    }

    @Test fun anEntirelySkinColouredImageIsNotAPortraitWithoutFaces() {
        val a = SceneAnalyzer.analyze(sample { _, _ -> skin })
        assertEquals(0f, a.skinRatio, 0f)
        assertFalse(a.scene == AutoScene.Portrait)
    }

    @Test fun skinPixelsAreExcludedFromWhiteBalanceOnlyWhenFacesAreKnown() {
        val img = sample { x, y -> if (x in 30..69 && y in 20..59) skin else gray }
        val noFaces = SceneAnalyzer.analyze(img)
        assertEquals("bez twarzy skóra zostaje w próbce oświetlenia", 1f, noFaces.usableRatio, 0.01f)
        val withFaces = SceneAnalyzer.analyze(img, listOf(FaceBox(0.3f, 0.25f, 0.7f, 0.75f)))
        assertTrue(withFaces.usableRatio < 1f)
    }
}
