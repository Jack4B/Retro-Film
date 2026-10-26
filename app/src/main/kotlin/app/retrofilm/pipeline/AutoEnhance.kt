package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Wynik analizy [AutoEnhance]: propozycje suwaków (exposure w EV, reszta −1..1 jak w [Adjustments]) i scena. */
data class AutoResult(
    val exposure: Float,
    val contrast: Float,
    val highlights: Float,
    val shadows: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float,
    val scene: AutoScene = AutoScene.General
) {
    /**
     * Wariant „przygotuj pod film”: tylko balans bieli i ekspozycja oraz delikatne zdjęcie kontrastu i nasycenia
     * (krzywa telefonu), bez ruszania świateł i cieni; film (LUT) ma dostać neutralne wejście, a nie ugrading.
     */
    fun forFilm(): AutoResult = copy(
        contrast = contrast.coerceAtMost(0f),
        highlights = 0f,
        shadows = 0f,
        saturation = saturation.coerceAtMost(0f)
    )

    /** Nakłada propozycję na [base] (pozostałe korekty zostają). */
    fun applyTo(base: Adjustments): Adjustments = base.copy(
        exposure = exposure,
        contrast = contrast,
        highlights = highlights,
        shadows = shadows,
        saturation = saturation,
        temperature = temperature,
        tint = tint
    )
}

/**
 * Automatyczne ulepszenie z analizy zmniejszonej kopii zdjęcia (bez uczenia maszynowego, deterministyczne),
 * świadome sceny ([SceneAnalyzer]: skóra/twarze, niebo, rośliny, dominująca barwa):
 * - balans bieli: oświetlenie szacowane metodą „shades of gray” (Finlayson i Trezzi 2004, p = 2) wyłącznie z pikseli
 *   nadających się do tego (bez skóry, nieba, roślin i mocno nasyconych obiektów), korygowane częściowo (50%) przez
 *   temperaturę i tint (adaptacja Bradforda). Siła maleje, gdy mało jest pikseli neutralnych albo scena ma jedną
 *   dominującą barwę (zachód słońca, las), a odcień skóry po korekcie nie może oddalić się od swojego przedziału;
 * - ekspozycja: mediana jasności do 18% szarości (60%); gdy jest skóra, ważona jej jasnością (twarz ma być ok. 58%
 *   w kodowaniu gamma), z limitem prześwietleń 99. percentyla i bez rozjaśniania jasnego nieba;
 * - kontrast z rozstępu percentyli 5–95 (słabszy w portretach), cienie z 10. percentyla, światła z 98.;
 * - nasycenie ze średniej chromy OKLab w półtonach (w portretach tylko delikatnie).
 * Wszystkie wartości są ograniczone, żeby auto nigdy nie robiło drastycznych zmian.
 */
@Suppress("MagicNumber") // progi i współczynniki heurystyki (opisane powyżej) są danymi, nie „magicznymi liczbami”
object AutoEnhance {
    private const val SAMPLE_SIDE = 256
    private const val TARGET_MEDIAN = 0.18f
    private const val TARGET_SKIN = 0.30f
    private const val EXPOSURE_STRENGTH = 0.6f
    private const val EXPOSURE_LIMIT = 1.5f
    private const val CLIP_ALLOWANCE = 1.6f
    private const val SPREAD_TARGET = 0.80f
    private const val LUM_R = 0.2126f
    private const val LUM_G = 0.7152f
    private const val LUM_B = 0.0722f
    private const val MIN_SAMPLES = 64
    private const val MONOCHROME = 0.01f
    private const val LOW_CHROMA = 0.07f
    private const val MIN_LUMA = 1e-4f
    private const val MIN_EV = 0.05f

    /** [faces] (opcjonalnie) to twarze z wykrywacza; bez nich skórę rozpoznaje się po kolorze. */
    fun suggest(px: IntArray, w: Int, h: Int, faces: List<FaceBox> = emptyList()): AutoResult {
        val sample = sample(px, w, h)
        val scene = SceneAnalyzer.analyze(sample, faces)
        val wb = AutoWhiteBalance.estimate(sample, scene)
        val m = WhiteBalance.matrix(wb.first, wb.second)
        val lumas = FloatArray(sample.count) { luma(sample.rgb, it, m) }
        val ev = exposure(lumas, scene)
        val gain = 2f.pow(ev)
        val enc = FloatArray(lumas.size) { AdjustmentProcessor.linearToSrgb((lumas[it] * gain).coerceIn(0f, 1f)) }
        enc.sort()
        fun pct(p: Float) = enc[((enc.size - 1) * p).toInt()]
        val portrait = scene.scene == AutoScene.Portrait
        val spread = pct(0.95f) - pct(0.05f)
        val contrastCap = if (portrait) 0.25f else 0.45f
        val contrast = ((SPREAD_TARGET - spread) * 1.2f).coerceIn(-0.15f, contrastCap)
        val shadows = ((0.14f - pct(0.10f)) * 4.5f).coerceIn(0f, 0.7f)
        val highlights = -((pct(0.98f) - 0.90f) * 6f).coerceIn(0f, 0.7f)
        return AutoResult(ev, contrast, highlights, shadows, saturation(scene), wb.first, wb.second, scene.scene)
    }

    private fun luma(rgb: FloatArray, i: Int, m: FloatArray): Float {
        val r = rgb[3 * i]
        val g = rgb[3 * i + 1]
        val b = rgb[3 * i + 2]
        return LUM_R * (m[0] * r + m[1] * g + m[2] * b) + LUM_G * (m[3] * r + m[4] * g + m[5] * b) +
            LUM_B * (m[6] * r + m[7] * g + m[8] * b)
    }

    /** Próbki liniowego RGB (trójki) z siatki o dłuższym boku ok. [SAMPLE_SIDE]. */
    private fun sample(px: IntArray, w: Int, h: Int): Sample {
        val step = maxOf(1, maxOf(w, h) / SAMPLE_SIDE)
        val gw = (w + step - 1) / step
        val gh = (h + step - 1) / step
        val out = FloatArray(3 * gw * gh)
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                val c = px[(gy * step) * w + gx * step]
                val i = 3 * (gy * gw + gx)
                out[i] = AdjustmentProcessor.srgbToLinear(((c shr 16) and 0xFF) / 255f)
                out[i + 1] = AdjustmentProcessor.srgbToLinear(((c shr 8) and 0xFF) / 255f)
                out[i + 2] = AdjustmentProcessor.srgbToLinear((c and 0xFF) / 255f)
            }
        }
        return Sample(out, gw, gh)
    }

    /**
     * Ekspozycja w EV: mediana do 18% szarości w 60%, a przy skórze mieszana z korektą do jasności twarzy (70% z
     * twarzami z wykrywacza, 40% przy samym kolorze); ograniczona prześwietleniami i jasnym niebem.
     */
    private fun exposure(lumas: FloatArray, scene: SceneAnalysis): Float {
        val sorted = lumas.sortedArray()
        val median = sorted[sorted.size / 2].coerceAtLeast(MIN_LUMA)
        val p99 = sorted[((sorted.size - 1) * 0.99f).toInt()].coerceAtLeast(MIN_LUMA)
        var ev = (ln(TARGET_MEDIAN / median) / ln(2f))
        val skin = lumas.filterIndexed { i, _ -> scene.skin[i] }.sorted()
        if (skin.size >= MIN_SAMPLES / 4) {
            val evSkin = ln(TARGET_SKIN / skin[skin.size / 2].coerceAtLeast(MIN_LUMA)) / ln(2f)
            val weight = if (scene.hasFaces) 0.7f else 0.4f
            ev = weight * evSkin + (1f - weight) * ev
        }
        ev *= EXPOSURE_STRENGTH
        val headroom = ln(CLIP_ALLOWANCE / p99) / ln(2f)
        if (ev > headroom) ev = headroom
        if (scene.skyRatio > 0.15f && ev > 0.3f && skyBrightness(lumas, scene) > 0.7f) ev = 0.3f
        return ev.coerceIn(-EXPOSURE_LIMIT, EXPOSURE_LIMIT).let { if (abs(it) < MIN_EV) 0f else it }
    }

    private fun skyBrightness(lumas: FloatArray, scene: SceneAnalysis): Float {
        val sky = lumas.filterIndexed { i, _ -> scene.sky[i] }.sorted()
        return if (sky.isEmpty()) 0f else AdjustmentProcessor.linearToSrgb(sky[sky.size / 2].coerceIn(0f, 1f))
    }

    /** Nasycenie ze średniej chromy OKLab pikseli o średniej jasności; w portretach najwyżej +0,15. */
    private fun saturation(scene: SceneAnalysis): Float {
        var sum = 0.0
        var n = 0
        for (i in scene.colors.chroma.indices) {
            if (scene.colors.lightness[i] in 0.25f..0.85f) {
                sum += scene.colors.chroma[i]
                n++
            }
        }
        if (n < MIN_SAMPLES) return 0f
        val chroma = (sum / n).toFloat()
        val value = when {
            chroma < MONOCHROME -> 0f
            chroma < LOW_CHROMA -> ((0.09f - chroma) * 6f).coerceIn(0f, 0.35f)
            else -> -((chroma - 0.16f) * 3f).coerceIn(0f, 0.2f)
        }
        return if (scene.scene == AutoScene.Portrait) value.coerceAtMost(0.15f) else value
    }
}
