package app.retrofilm.pipeline

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Korekty obrazu po nałożeniu filmu: kontrast, nasycenie i temperatura w zakresie −1..1,
 * ekspozycja w EV (−[EXPOSURE_MAX_EV]..+[EXPOSURE_MAX_EV]).
 * Wartości neutralne ([None]) omijają ten krok, więc wynik filmu (golden testy silnika) się nie zmienia.
 * Model opisany w [AdjustmentProcessor]; ten sam kod w podglądzie i w zapisie.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    /** Tint −1..1: zielony ↔ magenta (druga oś balansu bieli). */
    val tint: Float = 0f,
    /** Światła −1..1: minus przywraca szczegóły w jasnych partiach, plus je rozjaśnia. */
    val highlights: Float = 0f,
    /** Cienie −1..1: plus rozjaśnia ciemne partie, minus je pogłębia. */
    val shadows: Float = 0f,
    /** Blaknięcie 0..1: podniesiona czerń i przytłumiona biel (wygląd starej odbitki). */
    val fade: Float = 0f,
    /** Ziarno filmowe 0..1. */
    val grain: Float = 0f,
    /** Rozmiar ziarna 0..1 (0,5 = domyślny, więcej = grubsze ziarno). */
    val grainSize: Float = DEFAULT_GRAIN_SIZE,
    /** Winieta 0..1: przyciemnione rogi. */
    val vignette: Float = 0f,
    /** Ilość wyostrzania 0..1 (zob. [Sharpener]); 0 = wyłączone. */
    val sharpness: Float = 0f,
    /** Promień wyostrzania 0,5..3 (1 = σ 1 px przy 4000 px dłuższego boku). */
    val sharpRadius: Float = SharpenParams.DEFAULT_RADIUS,
    /** Detal wyostrzania 0..1: 0 = maska nieostra bez aureoli, 1 = dekonwolucja. */
    val sharpDetail: Float = SharpenParams.DEFAULT_DETAIL,
    /** Maskowanie wyostrzania 0..1: 0 = wszędzie, 1 = tylko mocne krawędzie. */
    val sharpMasking: Float = 0f,
    val sharpMode: SharpenMode = SharpenMode.Standard,
    /** Podgląd maski krawędzi zamiast zdjęcia (tylko do ustawiania, nie jest korektą). */
    val sharpMaskPreview: Boolean = false,
    /** Wyrazistość −1..1: lokalny kontrast w półtonach; minus zmiękcza. */
    val clarity: Float = 0f,
    /** Usuwanie zamglenia 0..1 (dark channel prior, zob. [Dehaze]). */
    val dehaze: Float = 0f,
    /** Odszumianie kolorów 0..1 (zob. [ChromaDenoise]). */
    val denoise: Float = 0f,
    /** Odszumianie jasności 0..1 (zob. [LumaDenoise]). */
    val lumaDenoise: Float = 0f,
    /** Mikser HSL dla 8 zakresów barw ([HslAdjust]). */
    val hsl: HslAdjust = HslAdjust(),
    /** Barwa tonowania cieni (stopnie 0..360) i jego siła 0..1. */
    val shadowHue: Float = 0f,
    val shadowAmount: Float = 0f,
    /** Barwa tonowania świateł (stopnie 0..360) i jego siła 0..1. */
    val highlightHue: Float = 0f,
    val highlightAmount: Float = 0f,
    /** Balans tonowania −1..1: plus rozszerza zakres świateł, minus cieni. */
    val toneBalance: Float = 0f,
    /** Krzywe tonalne (główna i RGB), zob. [ToneCurve]. */
    val curves: ToneCurves = ToneCurves(),
    /** Maski lokalne (do [LocalMask.MAX_MASKS]) z własnymi korektami, zob. [LocalMask]. */
    val masks: List<LocalMask> = emptyList()
) {
    val isNeutral: Boolean get() = this == None

    /** Czy potrzebny jest przebieg po sąsiedztwie pikseli ([DetailFilter]). */
    val hasDetail: Boolean get() = sharpness > 0f || clarity != 0f

    val sharpenParams: SharpenParams
        get() = SharpenParams(sharpness, sharpRadius, sharpDetail, sharpMasking, sharpMode)

    /** Czy potrzebny jest przebieg po całym obrazie przed korektami tonalnymi (dehaze, odszumianie). */
    val hasRestoration: Boolean get() = dehaze > 0f || denoise > 0f || lumaDenoise > 0f

    companion object {
        val None = Adjustments()
        const val DEFAULT_GRAIN_SIZE = 0.5f
        const val EXPOSURE_MAX_EV = 2f
    }
}

/**
 * Czysta matematyka korekt na pikselach ARGB (bez Androida), wzorowana na darktable / RawTherapee:
 * 1. ekspozycja i temperatura w świetle liniowym (mnożniki kanałów, jak „exposure” i balans bieli);
 *    temperatura i tint to adaptacja chromatyczna Bradforda ([WhiteBalance]) przy stałej luminancji bieli;
 * 2. miękkie przycięcie świateł (kolano od [KNEE]) zamiast ostrego obcięcia, potem kodowanie sRGB;
 * 3. nasycenie jak „vibrance”: plus wzmacnia głównie kolory mało nasycone (skóra i wysycone kolory
 *    nie przepalają się), minus odbarwia równomiernie, luminancja Rec.709 zachowana;
 * 4. kontrast krzywą sigmoidalną wokół szarości 0,5 (czernie i biele nieruchome, bez przycięć);
 *    wartości ujemne spłaszczają obraz ku szarości.
 */
class AdjustmentProcessor(private val adj: Adjustments) {
    private val m00: Float
    private val m01: Float
    private val m02: Float
    private val m10: Float
    private val m11: Float
    private val m12: Float
    private val m20: Float
    private val m21: Float
    private val m22: Float
    private val toLinear = FloatArray(BYTE_LEVELS) { srgbToLinear(it / BYTE_MAX) }
    private val encode: FloatArray

    private val maskLut: FloatArray
    private val shadowGain = FloatArray(BYTE_LEVELS) { toneGain(adj.shadows, SHADOW_EV, shadowWeight(it / BYTE_MAX)) }
    private val highlightGain =
        FloatArray(BYTE_LEVELS) { toneGain(adj.highlights, HIGHLIGHT_EV, highlightWeight(it / BYTE_MAX)) }

    init {
        val exposure = 2.0.pow(adj.exposure.toDouble()).toFloat()
        val wb = WhiteBalance.matrix(adj.temperature, adj.tint)
        val m = FloatArray(MATRIX_SIZE) { wb[it] * exposure }
        m00 = m[IDX_00]
        m01 = m[IDX_01]
        m02 = m[IDX_02]
        m10 = m[IDX_10]
        m11 = m[IDX_11]
        m12 = m[IDX_12]
        m20 = m[IDX_20]
        m21 = m[IDX_21]
        m22 = m[IDX_22]
        // Kolano normalizuje się do największej sumy wiersza (jasność bieli); bez rozjaśniania krzywa jest tożsamością.
        val ceiling = maxOf(1f, m00 + m01 + m02, m10 + m11 + m12, m20 + m21 + m22)
        encode = FloatArray(ENCODE_SIZE + 1) { linearToSrgb(softClip(it.toFloat() / ENCODE_SCALE, ceiling)) }
        maskLut = FloatArray(BYTE_LEVELS) { encodeLinear(toLinear[it] * exposure) }
    }

    private val rgb = FloatArray(3)
    private val hasTone = adj.shadows != 0f || adj.highlights != 0f
    private val hsl: HslMixer? = if (adj.hsl.isNeutral) null else HslMixer(adj.hsl)
    private val toning: SplitToning? = if (adj.shadowAmount > 0f || adj.highlightAmount > 0f) {
        SplitToning(adj.shadowHue, adj.shadowAmount, adj.highlightHue, adj.highlightAmount, adj.toneBalance)
    } else {
        null
    }
    private val chroma: ChromaScaler? = if (adj.saturation != 0f) ChromaScaler(adj.saturation) else null
    private val contrast: LuminanceContrast? = if (adj.contrast != 0f) LuminanceContrast(adj.contrast) else null
    private val vignette: VignetteLut? = if (adj.vignette > 0f) VignetteLut(adj.vignette) else null
    private val curves: CurveLut? = if (adj.curves.isNeutral) null else CurveLut(adj.curves)
    private val hasEffects = adj.vignette > 0f || adj.grain > 0f

    /**
     * Przetwarza [count] pikseli ARGB w miejscu (alfa bez zmian). [width], [firstRow] i [totalRows]
     * opisują położenie porcji w obrazie (winieta i ziarno zależą od współrzędnych).
     */
    fun process(px: IntArray, count: Int = px.size, span: PixelSpan = PixelSpan(count), mask: ToneMask? = null) {
        val width = span.width
        val firstRow = span.firstRow
        val totalRows = span.totalRows
        val cell = maxOf(
            GRAIN_CELL_MIN,
            maxOf(width, totalRows).toFloat() / GRAIN_CELL_DIVISOR * (GRAIN_SIZE_MIN + adj.grainSize * GRAIN_SIZE_SPAN)
        )
        for (i in 0 until count) {
            val c = px[i]
            val lr = toLinear[(c shr RED_SHIFT) and BYTE_MASK]
            val lg = toLinear[(c shr GREEN_SHIFT) and BYTE_MASK]
            val lb = toLinear[c and BYTE_MASK]
            rgb[0] = encodeLinear(m00 * lr + m01 * lg + m02 * lb)
            rgb[1] = encodeLinear(m10 * lr + m11 * lg + m12 * lb)
            rgb[2] = encodeLinear(m20 * lr + m21 * lg + m22 * lb)
            chroma?.apply(rgb)
            hsl?.apply(rgb)
            if (hasTone) toneRecovery(mask, i % width, firstRow + i / width)
            contrast?.apply(rgb)
            curves?.apply(rgb)
            if (adj.fade > 0f) fade()
            toning?.apply(rgb, luma())
            if (hasEffects) effects(i % width, firstRow + i / width, width, totalRows, cell)
            val d = Dither.at(i % width, firstRow + i / width)
            px[i] = (c and ALPHA_MASK) or (to8(rgb[0] + d) shl RED_SHIFT) or (to8(rgb[1] + d) shl GREEN_SHIFT) or
                to8(rgb[2] + d)
        }
    }

    private fun luma() = LUM_R * rgb[0] + LUM_G * rgb[1] + LUM_B * rgb[2]

    /**
     * Cienie i światła jako ekspozycja warstwy bazowej: rozkład na bazę (jasność wygładzona filtrem prowadzonym,
     * [ToneMask]) i detal; mnożnik 2^(siła · zakres EV · waga(baza)) przeniesiony z liniowego na kodowanie gamma
     * przesuwa tony, a ten sam mnożnik na wszystkich kanałach zachowuje barwę i lokalny kontrast detalu.
     * Piksel jaśniejszy od bazy (iskra światła) nie jest traktowany jak cień, a ciemniejszy od bazy (detal w jasnym
     * obszarze) nie jest traktowany jak światło.
     */
    private fun toneRecovery(mask: ToneMask?, x: Int, row: Int) {
        val own = luma()
        val base = if (mask != null) maskLut[maskIndex(mask.sample(x, row))] else own
        val shadowY = (base + maxOf(base, own)) * HALF
        val highlightY = (base + minOf(base, own)) * HALF
        var m = shadowGain[maskIndex(shadowY)] * highlightGain[maskIndex(highlightY)]
        // Przy przyciemnianiu bazy (światła) detal ponad nią rośnie tak, by jego bezwzględny kontrast się zachował
        // (jak w local Laplacian / Durand–Dorsey): chmury na przyciemnionym niebie zostają wyraźne zamiast spłaszczyć
        // się do szarości. Przy rozjaśnianiu cieni detalu nie wzmacniamy (rósłby sam szum).
        val baseGain = shadowGain[maskIndex(base)] * highlightGain[maskIndex(base)]
        if (baseGain < 1f && base > TONE_MIN_BASE) {
            val boost = minOf(1f / baseGain - 1f, TONE_BOOST_CAP) * TONE_DETAIL
            val ratio = (own / base).coerceIn(TONE_RATIO_MIN, TONE_RATIO_MAX)
            m *= (1f + boost * (ratio - 1f)).coerceIn(TONE_BOOST_MIN, TONE_BOOST_MAX)
        }
        // Chromę skalujemy słabiej niż jasność: przy przyciemnianiu prawie nie szarzeje, przy rozjaśnianiu rośnie
        // umiarkowanie.
        val keep = if (m < 1f) TONE_KEEP_DARKEN else TONE_KEEP_LIFT
        val s = 1f + (1f - keep) * (m - 1f)
        for (k in 0..2) rgb[k] = (own * m + (rgb[k] - own) * s).coerceIn(0f, 1f)
    }

    private fun maskIndex(y: Float): Int = (y * BYTE_MAX + HALF).toInt().coerceIn(0, BYTE_LEVELS - 1)

    private fun fade() {
        val lift = FADE_LIFT * adj.fade
        val top = FADE_TOP * adj.fade
        for (k in 0..2) rgb[k] = lift + rgb[k] * (1f - lift - top)
        // wyblakły pigment traci też chromę
        val y = luma()
        val keep = 1f - FADE_DESAT * adj.fade
        for (k in 0..2) rgb[k] = y + (rgb[k] - y) * keep
    }

    private fun effects(x: Int, y: Int, width: Int, rows: Int, cell: Float) {
        if (adj.vignette > 0f) {
            val nx = (x + HALF) / width * 2f - 1f
            val ny = (y + HALF) / rows * 2f - 1f
            val f = vignette?.gain((nx * nx + ny * ny) / 2f) ?: 1f
            for (k in 0..2) rgb[k] *= f
        }
        if (adj.grain > 0f) {
            val stops = FilmGrain.stops(luma().coerceIn(0f, 1f), adj.grain)
            for (k in 0..2) rgb[k] *= FilmGrain.gain(stops, FilmGrain.channel(x, y, cell, k))
        }
    }

    private fun encodeLinear(v: Float): Float {
        val idx = (v * ENCODE_SCALE + HALF).toInt().coerceIn(0, ENCODE_SIZE)
        return encode[idx]
    }

    private fun to8(v: Float): Int = (v * BYTE_MAX + HALF).toInt().coerceIn(0, BYTE_LEVELS - 1)

    companion object {
        const val KNEE = 0.8f
        private const val SHADOW_EV = 2.0f
        private const val HIGHLIGHT_EV = 1.2f
        private const val GAMMA_INV = 0.4545f
        private const val SHADOW_FROM = 0.15f
        private const val SHADOW_TO = 0.55f
        private const val HIGHLIGHT_FROM = 0.40f
        private const val HIGHLIGHT_TO = 0.90f
        private const val BLACK_GUARD = 0.06f
        private const val TONE_DETAIL = 2.2f
        private const val TONE_BOOST_CAP = 4f
        private const val TONE_KEEP_DARKEN = 0.9f
        private const val TONE_KEEP_LIFT = 0.6f
        private const val TONE_MIN_BASE = 0.02f
        private const val TONE_RATIO_MIN = 0.25f
        private const val TONE_RATIO_MAX = 4f
        private const val TONE_BOOST_MIN = 0.5f
        private const val TONE_BOOST_MAX = 1.8f
        private const val MATRIX_SIZE = 9
        private const val IDX_00 = 0
        private const val IDX_01 = 1
        private const val IDX_02 = 2
        private const val IDX_10 = 3
        private const val IDX_11 = 4
        private const val IDX_12 = 5
        private const val IDX_20 = 6
        private const val IDX_21 = 7
        private const val IDX_22 = 8
        private const val FADE_LIFT = 0.12f
        private const val FADE_TOP = 0.06f
        private const val FADE_DESAT = 0.2f
        private const val GRAIN_CELL_MIN = 0.7f
        private const val GRAIN_CELL_DIVISOR = 1200f
        private const val GRAIN_SIZE_MIN = 0.6f
        private const val GRAIN_SIZE_SPAN = 2f
        private const val HASH_X = 374761393
        private const val HASH_Y = 668265263
        private const val HASH_SALT = 1442695041
        private const val HASH_MIX = 1274126177
        private const val HASH_SHIFT_A = 13
        private const val HASH_SHIFT_B = 16
        private const val HASH_MASK = 0xFFFF
        private const val HASH_HALF = 32767.5f
        private const val BYTE_LEVELS = 256
        private const val BYTE_MAX = 255f
        private const val BYTE_MASK = 0xFF
        private const val ALPHA_MASK = 0xFF000000.toInt()
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ENCODE_SCALE = 4096f
        private const val ENCODE_MAX_LINEAR = 4.5f
        private const val ENCODE_SIZE = (ENCODE_MAX_LINEAR * ENCODE_SCALE).toInt()
        private const val HALF = 0.5f
        private const val LUM_R = 0.2126f
        private const val LUM_G = 0.7152f
        private const val LUM_B = 0.0722f
        private const val SIGMOID_GAIN = 8f
        private const val FLATTEN = 0.8f
        private const val SRGB_LINEAR_CUT = 0.04045f
        private const val SRGB_LINEAR_CUT_ENC = 0.0031308f
        private const val SRGB_SLOPE = 12.92f
        private const val SRGB_A = 0.055f
        private const val SRGB_GAMMA = 2.4

        /** Deterministyczny szum −1..1 dla komórki ([x], [y]) i kanału [salt] (ten sam obraz = to samo ziarno). */
        fun noise(x: Int, y: Int, salt: Int): Float {
            var h = x * HASH_X + y * HASH_Y + salt * HASH_SALT
            h = (h xor (h ushr HASH_SHIFT_A)) * HASH_MIX
            h = h xor (h ushr HASH_SHIFT_B)
            return (h and HASH_MASK) / HASH_HALF - 1f
        }

        /**
         * Waga cieni: 0 w samej czerni (punkt czerni i szum nie są podbijane), pełna w ciemnych tonach,
         * gładko maleje do 0 przy jasności [SHADOW_TO] (okolice szarości 18%).
         */
        fun shadowWeight(y: Float): Float =
            smoothstep(0f, BLACK_GUARD, y) * (1f - smoothstep(SHADOW_FROM, SHADOW_TO, y))

        /** Waga świateł: 0 poniżej [HIGHLIGHT_FROM], gładko rośnie do 1 przy [HIGHLIGHT_TO]. */
        fun highlightWeight(y: Float): Float = smoothstep(HIGHLIGHT_FROM, HIGHLIGHT_TO, y)

        @Suppress("MagicNumber")
        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Mnożnik w kodowaniu gamma odpowiadający [amount]·[evRange]·[weight] EV w świetle liniowym. */
        fun toneGain(amount: Float, evRange: Float, weight: Float): Float =
            2.0.pow((amount * evRange * weight * GAMMA_INV).toDouble()).toFloat()

        fun srgbToLinear(v: Float): Float = if (v <=
            SRGB_LINEAR_CUT
        ) {
            v / SRGB_SLOPE
        } else {
            ((v + SRGB_A) / (1 + SRGB_A)).toDouble().pow(SRGB_GAMMA).toFloat()
        }

        fun linearToSrgb(v: Float): Float = if (v <= SRGB_LINEAR_CUT_ENC) {
            v * SRGB_SLOPE
        } else {
            ((1 + SRGB_A) * v.toDouble().pow(1 / SRGB_GAMMA) - SRGB_A).toFloat()
        }

        /**
         * Miękkie kolano świateł: tożsamość do [KNEE], potem płynne dojście do 1,0 w punkcie [ceiling]
         * (największy mnożnik korekt). Gdy [ceiling] <= 1, nic nie jest rozjaśniane i krzywa jest tożsamością.
         */
        fun softClip(v: Float, ceiling: Float): Float {
            if (ceiling <= 1f || v <= KNEE) return v.coerceAtMost(1f)
            val width = 1f - KNEE
            val shape = (1f - exp(-(v - KNEE) / width)) / (1f - exp(-(ceiling - KNEE) / width))
            return KNEE + width * shape.coerceAtMost(1f)
        }

        /** Krzywa kontrastu na wartości 0..1: sigmoida (c > 0) albo spłaszczenie (c < 0); 0, 0,5 i 1 stałe. */
        fun contrastCurve(x: Float, c: Float): Float = when {
            c == 0f -> x
            c < 0f -> (x * (1f + FLATTEN * c) - HALF * FLATTEN * c).coerceIn(0f, 1f)
            else -> sigmoid(x, SIGMOID_GAIN * c)
        }

        private fun sigmoid(x: Float, k: Float): Float {
            fun s(t: Float) = 1f / (1f + exp(-k * (t - HALF)))
            val lo = s(0f)
            return ((s(x) - lo) / (s(1f) - lo)).coerceIn(0f, 1f)
        }
    }
}

/** Położenie porcji pikseli w obrazie: szerokość wiersza, pierwszy wiersz i liczba wierszy całego obrazu. */
data class PixelSpan(val width: Int, val firstRow: Int = 0, val totalRows: Int = 1)

/** Nakłada [Adjustments] na bitmapę (nowa bitmapa ARGB_8888, źródło bez zmian). */
object AdjustmentsApplier {
    private const val ROWS_PER_CHUNK = 64

    fun apply(src: Bitmap, adj: Adjustments): Bitmap {
        if (adj.isNeutral) return src
        return applyTo(if (adj.hasRestoration) restore(src, adj) else src, adj)
    }

    /** Dehaze i odszumianie na kopii źródła, przed korektami tonalnymi (żeby nie usuwały ziarna z efektów). */
    private fun restore(src: Bitmap, adj: Adjustments): Bitmap {
        val w = src.width
        val h = src.height
        val all = IntArray(w * h)
        src.getPixels(all, 0, w, 0, 0, w, h)
        ChromaDenoise.apply(all, w, h, adj.denoise)
        LumaDenoise.apply(all, w, h, adj.lumaDenoise)
        Dehaze.apply(all, w, h, adj.dehaze)
        val work = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        work.setPixels(all, 0, w, 0, 0, w, h)
        return work
    }

    private fun applyTo(src: Bitmap, adj: Adjustments): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val blender = MaskBlender(adj.masks, w, h).takeUnless { it.isEmpty }
        val mask = if (adj.shadows != 0f || adj.highlights != 0f) buildMask(src) else null
        // kawałki po ROWS_PER_CHUNK wierszy są niezależne; każdy wątek ma własny procesor i bufor (stan roboczy)
        Parallel.forEach(
            (h + ROWS_PER_CHUNK - 1) / ROWS_PER_CHUNK,
            { AdjustmentProcessor(adj) to IntArray(w * ROWS_PER_CHUNK) }
        ) { (processor, buf), chunk ->
            val y = chunk * ROWS_PER_CHUNK
            val rows = minOf(ROWS_PER_CHUNK, h - y)
            synchronized(src) { src.getPixels(buf, 0, w, 0, y, w, rows) }
            processor.process(buf, w * rows, PixelSpan(w, y, h), mask)
            synchronized(out) { out.setPixels(buf, 0, w, 0, y, w, rows) }
        }
        if (blender != null) blendMasks(out, blender, adj.masks.any { it.hasEffect && it.usesTone })
        if (adj.hasDetail) {
            val all = IntArray(w * h)
            out.getPixels(all, 0, w, 0, 0, w, h)
            DetailFilter.apply(all, w, h, adj.sharpenParams, adj.clarity, adj.sharpMaskPreview)
            out.setPixels(all, 0, w, 0, 0, w, h)
        }
        return out
    }

    /**
     * Maski lokalne na już skorygowanym globalnie obrazie [out]: drugie przejście porcjami. Cienie i światła w
     * maskach korzystają z maski tonalnej policzonej z tego samego (skorygowanego) obrazu, więc działają na warstwie
     * bazowej jak korekty globalne i nie tworzą aureoli, a nie punktowo.
     */
    private fun blendMasks(out: Bitmap, blender: MaskBlender, usesTone: Boolean) {
        val w = out.width
        val tone = if (usesTone) buildMask(out) else null
        val buf = IntArray(w * ROWS_PER_CHUNK)
        var y = 0
        while (y < out.height) {
            val rows = minOf(ROWS_PER_CHUNK, out.height - y)
            out.getPixels(buf, 0, w, 0, y, w, rows)
            blender.blend(buf, w * rows, y, tone)
            out.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
    }

    /** Lokalna jasność obrazu źródłowego dla cieni i świateł (jedno dodatkowe przejście porcjami). */
    private fun buildMask(src: Bitmap): ToneMask {
        val w = src.width
        val builder = ToneMask.Builder(w, src.height)
        val buf = IntArray(w * ROWS_PER_CHUNK)
        var y = 0
        while (y < src.height) {
            val rows = minOf(ROWS_PER_CHUNK, src.height - y)
            src.getPixels(buf, 0, w, 0, y, w, rows)
            builder.add(buf, w * rows, w, y)
            y += rows
        }
        return builder.build()
    }

    /** Wygodny skrót dla testów i podglądów małych kafelków. */
    fun applyToPixel(argb: Int, adj: Adjustments): Int {
        val px = intArrayOf(argb)
        AdjustmentProcessor(adj).process(px)
        return px[0]
    }
}
