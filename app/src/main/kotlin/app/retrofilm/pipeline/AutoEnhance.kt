package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.exp
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
    val scene: AutoScene = AutoScene.General,
    /** Reszta balansu bieli poza krzywą dzienną (xy), zob. [Adjustments.whiteDx]. */
    val whiteDx: Float = 0f,
    val whiteDy: Float = 0f,
    /** Odwrócenie krzywej telefonu (0..1) – tylko w wariancie [forFilm]. */
    val toneInversion: Float = 0f,
    /** Oszacowane oświetlenie (liniowe RGB, suma 1) albo null, gdy za mało próbek neutralnych. */
    val illuminant: List<Double>? = null,
    /** Pewność balansu bieli 0..1 (zgodność estymatorów). */
    val wbConfidence: Float = 0f
) {
    /**
     * Wariant „przygotuj pod film”: tylko balans bieli i ekspozycja oraz delikatne zdjęcie kontrastu i nasycenia
     * (krzywa telefonu), bez ruszania świateł i cieni; film (LUT) ma dostać neutralne wejście, a nie ugrading.
     */
    fun forFilm(inversion: Float = 0f): AutoResult = copy(
        contrast = if (inversion > 0f) 0f else contrast.coerceAtMost(0f),
        toneInversion = inversion.coerceIn(0f, 1f),
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
        tint = tint,
        whiteDx = whiteDx,
        whiteDy = whiteDy,
        toneInversion = toneInversion
    )
}

/**
 * Kontekst automatycznej korekty spoza pikseli: [targetGrey] – liniowa jasność wejścia, którą wybrany film oddaje
 * jako 18% szarości ([FilmCalibration]); [headroomEv] – zapas jasności ponad biel SDR z HDR/RAW ([HdrAnalysis]);
 * [baselineEv] – BaselineExposure z DNG (prior ekspozycji przy niskiej pewności pomiaru); [inversion] – bieżąca siła
 * „Zdejmij krzywą telefonu” (pomiar na pikselach po jej zdjęciu, tak jak zobaczy je film); [shoulder] – ramię filmu
 * ([FilmCalibration.shoulder]), powyżej którego twarz byłaby ściśnięta.
 */
data class AutoContext(
    val targetGrey: Float = FilmCalibration.MID_GREY,
    val headroomEv: Float = 0f,
    val baselineEv: Float? = null,
    val inversion: Float = 0f,
    val shoulder: Float = 1f
)

/**
 * Automatyczne ulepszenie z analizy zmniejszonej kopii zdjęcia (bez uczenia maszynowego, deterministyczne),
 * świadome sceny ([SceneAnalyzer]: skóra/twarze, niebo, rośliny, dominująca barwa):
 * - próbki to średnie bloków w świetle liniowym (nie pojedyncze piksele), z flagą przepalenia (kanał ≥ 0,98);
 * - balans bieli: mediana czterech estymatorów oświetlenia z pewnością ([AutoWhiteBalance]), korekta Bradforda także
 *   poza krzywą dzienną;
 * - ekspozycja: średnia logarytmiczna jasności bez przepalonych próbek, ważona do środka kadru (gauss), mieszana
 *   z jasnością twarzy z wykrywacza (60/40), do docelowej szarości filmu; siła 0,6 + 0,4·pewność; światła
 *   (percentyl 99,5) nie mogą przejść ponad kolano z zapasem HDR; przy niskiej pewności ciągnie do BaselineExposure;
 * - kontrast z rozstępu percentyli 5–95 (słabszy w portretach), cienie z 10. percentyla, światła z 98.;
 * - nasycenie ze średniej chromy OKLab w półtonach (w portretach tylko delikatnie).
 * Wszystkie wartości są ograniczone, żeby auto nigdy nie robiło drastycznych zmian.
 */
@Suppress("MagicNumber") // progi i współczynniki heurystyki (opisane powyżej) są danymi, nie „magicznymi liczbami”
object AutoEnhance {
    private const val SAMPLE_SIDE = 128
    private const val TARGET_SKIN = 0.30f
    private const val EXPOSURE_LIMIT = 2f
    private const val KNEE_HEADROOM_EV = 0.68f
    private const val SPREAD_TARGET = 0.80f
    private const val LUM_R = 0.2126f
    private const val LUM_G = 0.7152f
    private const val LUM_B = 0.0722f
    private const val MIN_SAMPLES = 64
    private const val MONOCHROME = 0.01f
    private const val LOW_CHROMA = 0.07f
    private const val MIN_LUMA = 1e-4f
    private const val MIN_EV = 0.05f
    private const val CLIP_LEVEL = 0.98f
    private const val CENTER_SIGMA = 0.35f
    private const val FACE_WEIGHT = 0.6f

    /** Najjaśniejszy kanał skóry (liniowo, ≈ sRGB 0,81) po ekspozycji z auto – twarz nie idzie w przepalenie. */
    const val FACE_CEILING = 0.62f

    /** [faces] (opcjonalnie) to twarze z wykrywacza; bez nich skórę rozpoznaje się po kolorze. */
    fun suggest(
        px: IntArray,
        w: Int,
        h: Int,
        faces: List<FaceBox> = emptyList(),
        context: AutoContext = AutoContext()
    ): AutoResult {
        val sample = sample(px, w, h, context.inversion)
        val scene = SceneAnalyzer.analyze(sample, faces)
        val wb = AutoWhiteBalance.estimate(sample, scene)
        val m = WhiteBalance.matrix(wb.temperature, wb.tint, wb.dx, wb.dy)
        val lumas = FloatArray(sample.count) { luma(sample.rgb, it, m) }
        val ev = exposure(lumas, sample, scene, context).let {
            minOf(
                it,
                FaceExposureCap.ev(sample, faces, context) ?: it
            )
        }
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
        return AutoResult(
            ev, contrast, highlights, shadows, saturation(scene), wb.temperature, wb.tint, scene.scene,
            wb.dx, wb.dy, illuminant = wb.illuminant?.let { e -> e.map { it / e.sum() } },
            wbConfidence = wb.confidence
        )
    }

    private fun luma(rgb: FloatArray, i: Int, m: FloatArray): Float {
        val r = rgb[3 * i]
        val g = rgb[3 * i + 1]
        val b = rgb[3 * i + 2]
        return LUM_R * (m[0] * r + m[1] * g + m[2] * b) + LUM_G * (m[3] * r + m[4] * g + m[5] * b) +
            LUM_B * (m[6] * r + m[7] * g + m[8] * b)
    }

    /**
     * Próbki liniowego RGB (trójki) jako średnie bloków siatki o dłuższym boku ok. [SAMPLE_SIDE] (bez aliasingu
     * drobnych detali; ten sam wynik dla podglądu i pełnej rozdzielczości), z flagą przepalenia bloku.
     */
    internal fun sample(px: IntArray, w: Int, h: Int, inversion: Float = 0f): Sample {
        val table = if (inversion <= 0f) {
            LINEAR
        } else {
            FloatArray(256) { AdjustmentProcessor.srgbToLinear(PhoneCurve.invert(it / 255f, inversion)) }
        }
        val step = maxOf(1, (maxOf(w, h) + SAMPLE_SIDE - 1) / SAMPLE_SIDE)
        val gw = (w + step - 1) / step
        val gh = (h + step - 1) / step
        val out = FloatArray(3 * gw * gh)
        val clipped = BooleanArray(gw * gh)
        val clip = (CLIP_LEVEL * 255f).toInt()
        // wiersze siatki równolegle (I: auto_suggest ≤ 30 ms na telefonie); każdy wiersz pisze rozłączny fragment
        Parallel.forEach(gh) { gy ->
            for (gx in 0 until gw) {
                val i = gy * gw + gx
                clipped[i] = blockMean(px, w, h, gx * step, gy * step, step, clip, table, out, 3 * i)
            }
        }
        return Sample(out, gw, gh, clipped)
    }

    /** Średnia liniowa bloku [step]×[step] od (x0, y0) zapisana do [out] od [at]; true, gdy blok ma przepalenie. */
    @Suppress("LongParameterList") // gorąca pętla próbkowania: bez alokacji obiektu bloku
    private fun blockMean(
        px: IntArray,
        w: Int,
        h: Int,
        x0: Int,
        y0: Int,
        step: Int,
        clip: Int,
        table: FloatArray,
        out: FloatArray,
        at: Int
    ): Boolean {
        var r = 0f
        var g = 0f
        var b = 0f
        var n = 0
        var hot = false
        for (y in y0 until minOf(h, y0 + step)) {
            for (x in x0 until minOf(w, x0 + step)) {
                val c = px[y * w + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                hot = hot || cr >= clip || cg >= clip || cb >= clip
                r += table[cr]
                g += table[cg]
                b += table[cb]
                n++
            }
        }
        out[at] = r / n
        out[at + 1] = g / n
        out[at + 2] = b / n
        return hot
    }

    private val LINEAR = FloatArray(256) { AdjustmentProcessor.srgbToLinear(it / 255f) }

    /**
     * Ekspozycja w EV: średnia logarytmiczna jasności (bez przepalonych, ważona do środka), przy skórze mieszana
     * z korektą do jasności twarzy; siła rośnie z pewnością pomiaru; ograniczona kolanem z zapasem HDR i niebem.
     */
    private fun exposure(lumas: FloatArray, s: Sample, scene: SceneAnalysis, ctx: AutoContext): Float {
        val kept = lumas.filterIndexed { i, _ -> !s.clipped[i] }.toMutableList()
        if (kept.size < MIN_SAMPLES / 4) return 0f
        var ev = log2(ctx.targetGrey / key(lumas, s))
        // Jasność skóry tylko przy twarzach z wykrywacza: sam kolor „skóry” to też drewno, piasek czy pomarańcza.
        if (scene.hasFaces) faceEv(lumas, s, scene, ctx)?.let { ev = FACE_WEIGHT * it + (1f - FACE_WEIGHT) * ev }
        // Pewność pomiaru: rozjaśnianie zaszumionych, bardzo ciemnych zdjęć jest niepewne; przyciemnianie nie.
        val dark = kept.count { it < 0.002f } / kept.size.toFloat()
        val confidence = if (ev <= 0f) 1f else (1f - 2f * dark).coerceIn(0f, 1f)
        ev *= 0.6f + 0.4f * confidence
        ctx.baselineEv?.let { ev = confidence * ev + (1f - confidence) * it }
        kept.sort()
        val p995 = kept[((kept.size - 1) * 0.995f).toInt()].coerceAtLeast(MIN_LUMA)
        val ceiling = 2f.pow(minOf(ctx.headroomEv.coerceAtLeast(0f), KNEE_HEADROOM_EV))
        val headroom = log2(ceiling / p995)
        if (ev > headroom) ev = maxOf(headroom, 0f).coerceAtMost(ev)
        if (scene.skyRatio > 0.15f && ev > 0.3f && skyBrightness(lumas, scene) > 0.7f) ev = 0.3f
        return ev.coerceIn(-EXPOSURE_LIMIT, EXPOSURE_LIMIT).let { if (abs(it) < MIN_EV) 0f else it }
    }

    /**
     * Klucz sceny (J): średnia logarytmiczna ważona do środka kadru (gaussowsko) z cenzurą przepalonych bloków.
     * Przepalony blok to wiadomo tylko „≥ obserwowanej”, więc nie jest ani odrzucany (zaniża klucz), ani brany
     * wprost: jego log-jasność zastępuje oczekiwana wartość ogona powyżej progu, oszacowana z jasnych
     * nieprzepalonych bloków (estymator typu Tobit, jednostronny). Bez przepalonych = dawna średnia.
     */
    internal fun key(lumas: FloatArray, s: Sample): Float {
        val logs = DoubleArray(lumas.size) { ln(lumas[it].coerceAtLeast(MIN_LUMA).toDouble()) }
        val bonus = CensoredTail.bonus(logs, s.clipped)
        var sumW = 0.0
        var sumL = 0.0
        for (i in lumas.indices) {
            val dx = ((i % s.gw) + 0.5f) / s.gw - 0.5f
            val dy = ((i / s.gw) + 0.5f) / s.gh - 0.5f
            val wgt = exp(-(dx * dx + dy * dy) / (2f * CENTER_SIGMA * CENTER_SIGMA)).toDouble()
            sumW += wgt
            sumL += wgt * (logs[i] + if (s.clipped[i]) bonus else 0.0)
        }
        return kotlin.math.exp(sumL / sumW).toFloat()
    }

    /** Korekta EV mediany skóry do docelowej jasności twarzy (skalowanej szarością filmu) albo null. */
    private fun faceEv(lumas: FloatArray, s: Sample, scene: SceneAnalysis, ctx: AutoContext): Float? {
        val skin = lumas.filterIndexed { i, _ -> scene.skin[i] && !s.clipped[i] }.sorted()
        if (skin.size < MIN_SAMPLES / 4) return null
        val skinTarget = TARGET_SKIN * ctx.targetGrey / FilmCalibration.MID_GREY
        return log2(skinTarget / skin[skin.size / 2].coerceAtLeast(MIN_LUMA))
    }

    private fun log2(v: Float) = ln(v) / ln(2f)

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

/**
 * Średnia nadwyżka log-jasności ponad próg w górnym ogonie nieprzepalonych (od [TAIL_FROM] percentyla):
 * jeśli rozkład jest wykładniczy w ogonie, oczekiwana nadwyżka nad progiem cenzury jest stała (bez pamięci).
 */
internal object CensoredTail {
    private const val TAIL_FROM = 0.9f
    private const val MIN_TAIL = 20
    private const val MAX_BONUS = 0.1 // ln: ≤ 0,14 EV; większe przestrzeliwuje ColorChecker +1 EV (0,2 → 0,24 EV)
    fun bonus(logs: DoubleArray, clipped: BooleanArray): Double {
        val free = logs.filterIndexed { i, _ -> !clipped[i] }.sorted()
        if (clipped.none { it } || free.size < MIN_TAIL) return 0.0
        val from = free[((free.size - 1) * TAIL_FROM).toInt()]
        return (free.filter { it >= from }.average() - from).coerceIn(0.0, MAX_BONUS)
    }
}

/** Górna granica ekspozycji z twarzy (M), wydzielona z [AutoEnhance]. */
@Suppress("MagicNumber") // indeksy kanałów RGB i środek bloku
internal object FaceExposureCap {
    private const val FACE_PERCENTILE = 0.75f
    private const val MIN_BLOCKS = 16
    private const val MIN_VALUE = 1e-4f

    /**
     * Górna granica EV z twarz z wykrywacza (M): 75. percentyl najjaśniejszego kanału bloków w ramkach twarzy
     * (przepalone liczą się jako 1, więc przepalona twarz nie jest rozjaśniana) nie może przejść [FACE_CEILING]
     * ani ramienia filmu. Kanał, nie luminancja: w skórze pierwszy przepala się czerwony.
     */
    fun ev(s: Sample, faces: List<FaceBox>, ctx: AutoContext): Float? {
        val vals = ArrayList<Float>()
        for (i in s.clipped.indices) {
            val x = ((i % s.gw) + 0.5f) / s.gw
            val y = ((i / s.gw) + 0.5f) / s.gh
            if (faces.none { x in it.left..it.right && y in it.top..it.bottom }) continue
            vals += if (s.clipped[i]) 1f else maxOf(s.rgb[3 * i], s.rgb[3 * i + 1], s.rgb[3 * i + 2])
        }
        if (vals.size < MIN_BLOCKS) return null
        vals.sort()
        val bright = vals[((vals.size - 1) * FACE_PERCENTILE).toInt()].coerceAtLeast(MIN_VALUE)
        return ln(minOf(AutoEnhance.FACE_CEILING, ctx.shoulder) / bright) / ln(2f)
    }
}
