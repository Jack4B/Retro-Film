package app.retrofilm.pipeline

import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Profile emulsji: kompletność katalogu, dziedziczenie wariantów, RMS @ D=1, niezależność od rozdzielczości. */
class FilmGrainProfilesTest {

    @Test
    fun everyCatalogLutMapsToAKnownProfile() {
        val ids = catalogIds("src/main/assets/luts/catalog-144.json") +
            catalogIds("../core/engine/src/main/assets/luts/catalog.json")
        assertTrue(ids.size > 200)
        val missing = ids.filter { it != "identity" && !FilmGrainProfiles.knownKey(it) }
        assertTrue("brak profilu dla: ${missing.take(12)} (n=${missing.size})", missing.isEmpty())
        assertEquals(FilmGrainProfiles.IDENTITY, FilmGrainProfiles.of("identity"))
        assertEquals(FilmGrainProfiles.IDENTITY, FilmGrainProfiles.of(""))
    }

    @Test
    fun lutPlusPlusVariantsInheritParentGrain() {
        val parent = FilmGrainProfiles.of("negative_new/kodak_portra_400")
        val pushed = FilmGrainProfiles.of("negative_new/kodak_portra_400_pp")
        assertEquals("kodak_portra_400", parent.baseId)
        assertEquals(parent.baseId, pushed.baseId)
        assertEquals(parent.gEq, pushed.gEq, 0f)
        assertEquals(parent.grainUm, pushed.grainUm, 0f)
        assertEquals(parent.sigmaEv, pushed.sigmaEv, 0f)
        val instant = FilmGrainProfiles.of("instant_consumer/polaroid_px_680_cold_pp")
        assertEquals("polaroid_px_680", instant.baseId)
        assertEquals(GrainFamily.INSTANT, instant.family)
    }

    @Test
    fun familyOrderingIsMonotonic() {
        fun g(id: String) = FilmGrainProfiles.of(id).gEq
        assertTrue(g("bw/kodak_t_max_100") < g("bw/kodak_t_max_400"))
        assertTrue(g("bw/kodak_t_max_400") < g("bw/kodak_tri_x_400"))
        assertTrue(g("bw/kodak_tri_x_400") <= g("bw/kodak_t_max_3200"))
        assertTrue(g("negative_new/kodak_portra_160") < g("negative_new/kodak_portra_400"))
        assertTrue(g("negative_new/kodak_portra_400") < g("negative_new/kodak_portra_800"))
        assertTrue(g("negative_color/kodak_ektar_100") < g("negative_new/kodak_portra_160"))
    }

    @Test
    fun sliderFullScaleMatchesDatasheetSigma() {
        val profile = FilmGrainProfiles.of("bw/kodak_tri_x_400")
        val mid = FilmGrain.stops(0.5f, 1f, profile.sigmaEv)
        assertEquals(profile.sigmaEv * FilmGrain.envelope(0.5f), mid, 1e-5f)
        assertEquals(0f, FilmGrain.stops(0.5f, 0f, profile.sigmaEv), 0f)
        assertEquals(0f, FilmGrain.stops(0.5f, 1f, FilmGrainProfiles.IDENTITY.sigmaEv), 0f)
    }

    @Test
    fun densityRmsAtD1MatchesGeqTimesEnvelope() {
        val gEq = 17f
        val grainUm = FilmGrain.APERTURE_UM
        val luma = encodeSrgb(DENSITY_ONE_LINEAR)
        val stops = FilmGrain.stops(luma, 1f, FilmGrain.sigmaEv(gEq, grainUm))
        val expected = 1000.0 * stops / FilmGrain.LOG2_10
        var sum = 0.0
        var sq = 0.0
        val n = 12_000
        for (i in 0 until n) {
            val enc = (luma * FilmGrain.gain(stops, FilmGrain.luma(i % 200, i / 200, 3f))).coerceIn(1e-5f, 1f)
            val density = -ln(linearSrgb(enc).toDouble()) / LN10
            sum += density
            sq += density * density
        }
        val rms = 1000.0 * sqrt(sq / n - (sum / n) * (sum / n))
        assertEquals("RMS gęstości @ D=1.0", expected, rms, expected * 0.2)
    }

    @Test
    fun grainCellScalesWithResolutionLikeA35mmFrame() {
        val um = 27f
        val full = FilmGrain.cellPx(4000, 3000, Adjustments.DEFAULT_GRAIN_SIZE, um)
        val preview = FilmGrain.cellPx(1280, 960, Adjustments.DEFAULT_GRAIN_SIZE, um)
        assertEquals(4000f / 1280f, full / preview, 1e-5f)
        assertEquals(um / FilmGrain.FRAME_UM * 4000f, full, 1e-4f)
    }

    @Test
    fun instantProfileIsSofterAndLargerThanColorNeg() {
        val pack = FilmGrainProfiles.of("instant_consumer/polaroid_px_680")
        val portra = FilmGrainProfiles.of("negative_new/kodak_portra_400")
        assertTrue(pack.grainUm > portra.grainUm)
        assertTrue(pack.clumpScale > portra.clumpScale)
        assertEquals(GrainFamily.INSTANT, pack.family)
        assertTrue(pack.chromaShare > portra.chromaShare)
    }

    @Test
    fun stockIdAloneDoesNotEnableGrain() {
        assertTrue(Adjustments(grainStockId = "bw/kodak_tri_x_400").isNeutral)
        assertTrue(Adjustments(grain = 0f, grainStockId = "bw/kodak_tri_x_400").finishing().isNeutral)
        assertFalse(Adjustments(grain = 0.2f, grainStockId = "bw/kodak_tri_x_400").isNeutral)
    }

    @Test
    fun fujiCnFactorIsEighteenTenths() {
        val superia = FilmGrainProfiles.of("negative_old/fuji_superia_400")
        assertEquals(4f * FilmGrainProfiles.FUJI_CN_FACTOR, superia.gEq, 1e-5f)
        assertEquals(1.8f, FilmGrainProfiles.FUJI_CN_FACTOR, 0f)
    }

    private fun catalogIds(path: String): List<String> =
        ID_LINE.findAll(File(path).readText()).map { it.groupValues[1] }.toList()

    private companion object {
        val ID_LINE = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        const val DENSITY_ONE_LINEAR = 0.1f
        val LN10 = ln(10.0)
        const val SRGB_CUT = 0.0031308f
        const val SRGB_EXPAND = 0.04045f

        fun encodeSrgb(linear: Float): Float =
            if (linear <= SRGB_CUT) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f

        fun linearSrgb(encoded: Float): Float =
            if (encoded <= SRGB_EXPAND) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }
}
