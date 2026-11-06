package app.retrofilm.pipeline

/**
 * Profil ziarna jednej emulsji (nie wariantu LUT). Wartości i mostki skal: [docs/ZIARNO-PLAN.md].
 * `gEq` jest na skali RMS slajdu/B&W; Fuji C-41: `gEq = 1,8 · RMS` (decyzja Q5).
 */
internal enum class GrainFamily {
    NONE,
    SILVER_BW,
    DYE_COLOR_NEG,
    DYE_SLIDE,
    CHROMOGENIC_BW,
    INSTANT
}

internal data class FilmGrainProfile(
    val baseId: String,
    val iso: Int,
    val family: GrainFamily,
    val gEq: Float,
    val grainUm: Float,
    val chromaShare: Float,
    val clumpScale: Float = FilmGrain.CLUMP_SCALE,
    val fineWeight: Float = FilmGrain.FINE_WEIGHT,
    val clumpWeight: Float = FilmGrain.CLUMP_WEIGHT
) {
    val sigmaEv: Float = FilmGrain.sigmaEv(gEq, grainUm)
}

@Suppress("TooManyFunctions", "MagicNumber")
internal object FilmGrainProfiles {
    const val FUJI_CN_FACTOR = 1.8f
    const val IDENTITY_ID = "identity"

    val IDENTITY = FilmGrainProfile(
        baseId = IDENTITY_ID,
        iso = 0,
        family = GrainFamily.NONE,
        gEq = 0f,
        grainUm = FilmGrain.APERTURE_UM,
        chromaShare = 0f
    )

    fun of(filmId: String): FilmGrainProfile {
        if (filmId.isEmpty() || filmId == IDENTITY_ID) return IDENTITY
        val key = tableKey(filmId)
        return TABLE[key] ?: estimate(key)
    }

    /** Ostatni człon ścieżki bez przyrostków wariantów LUT (`_pp`, `_cold`, …). */
    fun tableKey(filmId: String): String = stripVariants(filmId.substringAfterLast('/'))

    fun stripVariants(name: String): String {
        var current = name
        var changed = true
        while (changed) {
            changed = false
            for (suffix in VARIANT_SUFFIXES) {
                val token = "_$suffix"
                if (current.endsWith(token) && current.length > token.length) {
                    current = current.removeSuffix(token)
                    changed = true
                    break
                }
            }
        }
        return current
    }

    internal fun knownKey(filmId: String): Boolean = tableKey(filmId) in TABLE

    private fun silver(id: String, iso: Int, gEq: Float, grainUm: Float = FilmGrain.grainUmFromGeq(gEq)) =
        FilmGrainProfile(id, iso, GrainFamily.SILVER_BW, gEq, grainUm, chromaShare = 0f)

    private fun chromo(id: String, iso: Int, gEq: Float) =
        FilmGrainProfile(id, iso, GrainFamily.CHROMOGENIC_BW, gEq, FilmGrain.grainUmFromGeq(gEq), chromaShare = 0f)

    private fun slide(id: String, iso: Int, gEq: Float) =
        FilmGrainProfile(id, iso, GrainFamily.DYE_SLIDE, gEq, FilmGrain.grainUmFromGeq(gEq), CHROMA_SLIDE)

    private fun colorNeg(id: String, iso: Int, gEq: Float) =
        FilmGrainProfile(id, iso, GrainFamily.DYE_COLOR_NEG, gEq, FilmGrain.grainUmFromGeq(gEq), CHROMA_COLOR)

    private fun fujiCn(id: String, iso: Int, rms: Float) = colorNeg(id, iso, rms * FUJI_CN_FACTOR)

    private fun instant(id: String, iso: Int, gEq: Float, grainUm: Float, chromaShare: Float) = FilmGrainProfile(
        id,
        iso,
        GrainFamily.INSTANT,
        gEq,
        grainUm,
        chromaShare,
        clumpScale = INSTANT_CLUMP_SCALE,
        fineWeight = INSTANT_FINE,
        clumpWeight = INSTANT_CLUMP
    )

    private fun estimate(key: String): FilmGrainProfile = colorNeg(key, FALLBACK_ISO, FALLBACK_GEQ)

    private val TABLE: Map<String, FilmGrainProfile> = listOf(
        silver("agfa_apx_25", 25, 7f),
        silver("agfa_apx_100", 100, 9f),
        silver("fuji_neopan_acros_100", 100, 7f),
        silver("fuji_neopan_1600", 1600, 16f),
        silver("ilford_pan_f_plus_50", 50, 8f),
        silver("ilford_delta_100", 100, 8f),
        silver("ilford_fp_4_plus_125", 125, 10f),
        silver("ilford_delta_400", 400, 10f),
        silver("ilford_hp_5_plus_400", 400, 16f),
        silver("ilford_hp_5", 400, 16f),
        silver("ilford_delta_3200", 3200, 17f),
        chromo("ilford_xp_2", 400, 11f),
        chromo("kodak_bw_400_cn", 400, 11f),
        silver("kodak_t_max_100", 100, 8f),
        silver("kodak_t_max_400", 400, 10f),
        silver("kodak_t_max_3200", 3200, 18f),
        silver("kodak_tmax_3200", 3200, 18f),
        silver("kodak_tri_x_400", 400, 17f),
        silver("kodak_hie_hs_infra", 400, 25f, grainUm = 55f),
        silver("rollei_ortho_25", 25, 7f),
        silver("rollei_retro_80s", 80, 9f),
        silver("rollei_retro_100_tonal", 100, 10f),
        silver("rollei_ir_400", 400, 16f),
        slide("agfa_precisa_100", 100, 10f),
        slide("fuji_astia_100f", 100, 8f),
        slide("fuji_astia_100_generic", 100, 8f),
        slide("fuji_provia_100f", 100, 8f),
        slide("fuji_provia_100_generic", 100, 8f),
        slide("fuji_provia_400x", 400, 11f),
        slide("fuji_provia_400f", 400, 13f),
        slide("fuji_sensia_100", 100, 8f),
        slide("fuji_velvia_50", 50, 9f),
        slide("fuji_velvia_100_generic", 100, 8f),
        fujiCn("fuji_superia_200_xpro", 200, 4f),
        slide("kodak_e_100_gx_ektachrome_100", 100, 8f),
        slide("kodak_ektachrome_100_vs", 100, 11f),
        slide("kodak_ektachrome_100_vs_generic", 100, 11f),
        slide("kodak_elite_chrome_200", 200, 12f),
        slide("kodak_elite_chrome_400", 400, 14f),
        slide("kodak_elite_extracolor_100", 100, 11f),
        slide("kodak_kodachrome_25", 25, 11f),
        slide("kodak_kodachrome_64", 64, 12f),
        slide("kodak_kodachrome_64_generic", 64, 12f),
        slide("kodak_kodachrome_200", 200, 19f),
        slide("lomography_x_pro_slide_200", 200, 12f),
        fujiCn("fuji_superia_reala_100", 100, 4f),
        fujiCn("fuji_superia_100", 100, 4f),
        fujiCn("fuji_superia_200", 200, 4f),
        fujiCn("fuji_superia_400", 400, 4f),
        fujiCn("fuji_superia_800", 800, 5f),
        fujiCn("fuji_superia_x_tra_800", 800, 5f),
        fujiCn("fuji_superia_1600", 1600, 7f),
        fujiCn("fuji_superia_hg_1600", 1600, 7f),
        fujiCn("fuji_160c", 160, 3f),
        fujiCn("fuji_400h", 400, 4f),
        fujiCn("fuji_800z", 800, 5f),
        colorNeg("kodak_ektar_100", 100, 4.6f),
        colorNeg("kodak_portra_160", 160, 6.0f),
        colorNeg("kodak_portra_160_nc", 160, 6.5f),
        colorNeg("kodak_portra_160_vc", 160, 6.7f),
        colorNeg("kodak_portra_400", 400, 7.1f),
        colorNeg("kodak_portra_400_nc", 400, 7.4f),
        colorNeg("kodak_portra_400_uc", 400, 7.4f),
        colorNeg("kodak_portra_400_vc", 400, 7.7f),
        colorNeg("kodak_portra_800", 800, 8.4f),
        colorNeg("kodak_elite_color_200", 200, 7.7f),
        colorNeg("kodak_elite_color_400", 400, 8.0f),
        colorNeg("kodak_elite_100_xpro", 100, 6.0f),
        colorNeg("agfa_ultra_color_100", 100, 6.0f),
        colorNeg("agfa_vista_200", 200, 6.5f),
        colorNeg("lomography_redscale_100", 100, 6.0f),
        instant("fuji_fp_100c", 100, 12f, 100f, CHROMA_INSTANT),
        instant("fuji_fp_3000b", 3000, 16f, 125f, 0f),
        instant("polaroid_px_100uv", 100, 12f, 100f, CHROMA_INSTANT),
        instant("polaroid_px_70", 150, 13f, 105f, CHROMA_INSTANT),
        instant("polaroid_px_680", 640, 14f, 110f, CHROMA_INSTANT),
        instant("polaroid_time_zero", 150, 14f, 110f, CHROMA_INSTANT),
        instant("polaroid_669", 80, 13f, 105f, CHROMA_INSTANT),
        instant("polaroid_690", 100, 13f, 105f, CHROMA_INSTANT),
        instant("polaroid_polachrome", 40, 13f, 100f, CHROMA_INSTANT),
        instant("polaroid_664", 100, 14f, 110f, 0f),
        instant("polaroid_667", 3000, 16f, 120f, 0f),
        instant("polaroid_672", 400, 14f, 110f, 0f),
        instant("polaroid_665", 80, 14f, 110f, 0f)
    ).associateBy { it.baseId }

    private val VARIANT_SUFFIXES = listOf(
        "mmm", "mm", "m", "ppp", "pp", "p", "alt", "hc", "cool", "warm", "cold", "negative", "expired", "early"
    )
    private const val FALLBACK_ISO = 400
    private const val FALLBACK_GEQ = 7.1f
    private const val CHROMA_SLIDE = 0.35f
    private const val CHROMA_COLOR = 0.4f
    private const val CHROMA_INSTANT = 0.55f
    private const val INSTANT_CLUMP_SCALE = 4.0f
    private const val INSTANT_FINE = 0.714f
    private const val INSTANT_CLUMP = 0.7f
}
