package app.retrofilm.pipeline

import android.graphics.Bitmap
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
    /** Winieta 0..1: przyciemnione rogi. */
    val vignette: Float = 0f,
    /** Ostrość 0..1: maska unsharp na jasności (zob. [DetailFilter]). */
    val sharpness: Float = 0f,
    /** Wyrazistość −1..1: lokalny kontrast w półtonach; minus zmiękcza. */
    val clarity: Float = 0f,
    /** Usuwanie zamglenia 0..1 (dark channel prior, zob. [Dehaze]). */
    val dehaze: Float = 0f,
    /** Odszumianie kolorów 0..1 (zob. [ChromaDenoise]). */
    val denoise: Float = 0f,
    /** Mikser HSL dla 8 zakresów barw ([HslAdjust]). */
    val hsl: HslAdjust = HslAdjust(),
    /** Barwa tonowania cieni (stopnie 0..360) i jego siła 0..1. */
    val shadowHue: Float = 0f,
    val shadowAmount: Float = 0f,
    /** Barwa tonowania świateł (stopnie 0..360) i jego siła 0..1. */
    val highlightHue: Float = 0f,
    val highlightAmount: Float = 0f,
    /** Balans tonowania −1..1: plus rozszerza zakres świateł, minus cieni. */
    val toneBalance: Float = 0f
) {
    val isNeutral: Boolean get() = this == None

    /** Czy potrzebny jest przebieg po sąsiedztwie pikseli ([DetailFilter]). */
    val hasDetail: Boolean get() = sharpness > 0f || clarity != 0f

    /** Czy potrzebny jest przebieg po całym obrazie przed korektami tonalnymi (dehaze, odszumianie). */
    val hasRestoration: Boolean get() = dehaze > 0f || denoise > 0f

    companion object {
        val None = Adjustments()
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
    private val contrastLut =
        FloatArray(CONTRAST_SIZE + 1) { contrastCurve(it.toFloat() / CONTRAST_SIZE, adj.contrast) }

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
    private val hasEffects = adj.vignette > 0f || adj.grain > 0f

    /**
     * Przetwarza [count] pikseli ARGB w miejscu (alfa bez zmian). [width], [firstRow] i [totalRows]
     * opisują położenie porcji w obrazie (winieta i ziarno zależą od współrzędnych).
     */
    fun process(px: IntArray, count: Int = px.size, span: PixelSpan = PixelSpan(count), mask: ToneMask? = null) {
        val width = span.width
        val firstRow = span.firstRow
        val totalRows = span.totalRows
        val cell = maxOf(1, maxOf(width, totalRows) / GRAIN_CELL_DIVISOR)
        for (i in 0 until count) {
            val c = px[i]
            val lr = toLinear[(c shr RED_SHIFT) and BYTE_MASK]
            val lg = toLinear[(c shr GREEN_SHIFT) and BYTE_MASK]
            val lb = toLinear[c and BYTE_MASK]
            rgb[0] = encodeLinear(m00 * lr + m01 * lg + m02 * lb)
            rgb[1] = encodeLinear(m10 * lr + m11 * lg + m12 * lb)
            rgb[2] = encodeLinear(m20 * lr + m21 * lg + m22 * lb)
            if (adj.saturation != 0f) vibrance()
            hsl?.apply(rgb)
            if (hasTone) toneRecovery(mask, i % width, firstRow + i / width)
            if (adj.contrast !=
                0f
            ) {
                for (k in 0..2) rgb[k] = contrastLut[(rgb[k].coerceIn(0f, 1f) * CONTRAST_SIZE + HALF).toInt()]
            }
            if (adj.fade > 0f) fade()
            toning?.apply(rgb, luma())
            if (hasEffects) effects(i % width, firstRow + i / width, width, totalRows, cell)
            val d = Dither.at(i % width, firstRow + i / width)
            px[i] = (c and ALPHA_MASK) or (to8(rgb[0] + d) shl RED_SHIFT) or (to8(rgb[1] + d) shl GREEN_SHIFT) or
                to8(rgb[2] + d)
        }
    }

    private fun luma() = LUM_R * rgb[0] + LUM_G * rgb[1] + LUM_B * rgb[2]

    private fun vibrance() {
        val mx = maxOf(rgb[0], rgb[1], rgb[2])
        val chroma = if (mx > EPS) (mx - minOf(rgb[0], rgb[1], rgb[2])) / mx else 0f
        val f = if (adj.saturation < 0f) 1f + adj.saturation else 1f + adj.saturation * (1f - chroma) * skinGuard()
        val y = luma()
        for (k in 0..2) rgb[k] = (y + (rgb[k] - y) * f).coerceIn(0f, 1f)
    }

    /** 1 poza odcieniami skóry, w pomarańczowo-różowym zakresie mniej o [SKIN_GUARD]. */
    private fun skinGuard(): Float {
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]
        if (r < g || g < b || r - b < EPS) return 1f
        val hue = (g - b) / (r - b)
        val d = (hue - SKIN_HUE) / SKIN_WIDTH
        return 1f - SKIN_GUARD * maxOf(0f, 1f - d * d)
    }

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
        val m = shadowGain[maskIndex(shadowY)] * highlightGain[maskIndex(highlightY)]
        for (k in 0..2) rgb[k] = (rgb[k] * m).coerceIn(0f, 1f)
    }

    private fun maskIndex(y: Float): Int = (y * BYTE_MAX + HALF).toInt().coerceIn(0, BYTE_LEVELS - 1)

    private fun fade() {
        val lift = FADE_LIFT * adj.fade
        val top = FADE_TOP * adj.fade
        for (k in 0..2) rgb[k] = lift + rgb[k] * (1f - lift - top)
    }

    private fun effects(x: Int, y: Int, width: Int, rows: Int, cell: Int) {
        if (adj.vignette > 0f) {
            val nx = (x + HALF) / width * 2f - 1f
            val ny = (y + HALF) / rows * 2f - 1f
            val r = kotlin.math.sqrt((nx * nx + ny * ny) / 2f)
            val t = ((r - VIGNETTE_START) / (1f - VIGNETTE_START)).coerceIn(0f, 1f)
            val f = 1f - adj.vignette * VIGNETTE_MAX * t * t * (VIGNETTE_A - VIGNETTE_B * t)
            for (k in 0..2) rgb[k] *= f
        }
        if (adj.grain > 0f) {
            val amp = adj.grain * GRAIN_MAX * FilmGrain.midtoneWeight(luma().coerceIn(0f, 1f))
            val shared = FilmGrain.luma(x, y, cell)
            for (k in 0..2) {
                rgb[k] += (shared * GRAIN_SHARED + FilmGrain.chroma(x, y, cell, k) * (1f - GRAIN_SHARED)) * amp
            }
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
        private const val VIGNETTE_MAX = 0.65f
        private const val GRAIN_MAX = 0.12f
        private const val SKIN_HUE = 0.5f
        private const val SKIN_WIDTH = 0.4f
        private const val SKIN_GUARD = 0.55f
        private const val VIGNETTE_START = 0.35f
        private const val VIGNETTE_A = 3f
        private const val VIGNETTE_B = 2f
        private const val GRAIN_SHARED = 0.8f
        private const val GRAIN_CELL_DIVISOR = 1200
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
        private const val CONTRAST_SIZE = 1023
        private const val HALF = 0.5f
        private const val EPS = 1e-6f
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
        Dehaze.apply(all, w, h, adj.dehaze)
        val work = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        work.setPixels(all, 0, w, 0, 0, w, h)
        return work
    }

    private fun applyTo(src: Bitmap, adj: Adjustments): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val processor = AdjustmentProcessor(adj)
        val mask = if (adj.shadows != 0f || adj.highlights != 0f) buildMask(src) else null
        val buf = IntArray(w * ROWS_PER_CHUNK)
        var y = 0
        while (y < h) {
            val rows = minOf(ROWS_PER_CHUNK, h - y)
            src.getPixels(buf, 0, w, 0, y, w, rows)
            processor.process(buf, w * rows, PixelSpan(w, y, h), mask)
            out.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
        if (adj.hasDetail) {
            val all = IntArray(w * h)
            out.getPixels(all, 0, w, 0, 0, w, h)
            DetailFilter.apply(all, w, h, adj.sharpness, adj.clarity)
            out.setPixels(all, 0, w, 0, 0, w, h)
        }
        return out
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
