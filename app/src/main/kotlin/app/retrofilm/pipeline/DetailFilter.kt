package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.max

/**
 * Wyostrzanie ([Sharpener]) i wyrazistość (lokalny kontrast: ta sama maska z dużym promieniem
 * i wagą półtonów) na pikselach ARGB, bez Androida. Promienie liczone od dłuższego boku obrazu, więc podgląd
 * i zapis wyglądają tak samo. Zmiana jasności jest dodawana do wszystkich kanałów, więc barwy się nie przesuwają.
 */
object DetailFilter {
    private const val CLARITY_RADIUS_DIV = 25
    internal const val CLARITY_TARGET = 600
    private const val CLARITY_EPS = 0.0036f
    private const val CLARITY_GAIN = 0.9f
    private const val BOX_PASSES = 3
    private const val SAMPLE_ROWS = 64
    private const val HALF = 0.5f
    private const val BYTE_MAX = 255f
    private const val MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val ALPHA_MASK = 0xFF000000.toInt()

    /** Wygodne wywołanie z samą ilością wyostrzania i wyrazistością (ustawienia domyślne reszty parametrów). */
    fun apply(px: IntArray, width: Int, height: Int, sharpness: Float, clarity: Float) =
        apply(px, width, height, SharpenParams(sharpness), clarity, false)

    /**
     * Wyostrzanie ([Sharpener]) i wyrazistość −1..1 (minus zmiękcza); modyfikuje [px] w miejscu. Przy [maskPreview]
     * zamiast zdjęcia powstaje obraz maski krawędzi (biel = wyostrzane).
     */
    @Suppress("LongParameterList")
    fun apply(px: IntArray, width: Int, height: Int, sharp: SharpenParams, clarity: Float, maskPreview: Boolean) {
        if (sharp.amount <= 0f && clarity == 0f) return
        val luma = FloatArray(width * height)
        Parallel.range(luma.size) { from, to -> for (i in from until to) luma[i] = lumaOf(px[i]) }
        if (maskPreview && sharp.amount > 0f) {
            val m = Sharpener.maskOnly(luma, width, height, sharp)
            Parallel.range(px.size) { from, to ->
                for (i in from until to) {
                    val v = (m[i] * BYTE_MAX + HALF).toInt().coerceIn(0, MASK)
                    px[i] = (px[i] and ALPHA_MASK) or (v shl RED_SHIFT) or (v shl GREEN_SHIFT) or v
                }
            }
            return
        }
        val delta = if (sharp.amount > 0f) Sharpener.delta(luma, width, height, sharp) else FloatArray(luma.size)
        if (clarity != 0f) {
            val base = edgePreservingBase(luma, width, height)
            Parallel.range(luma.size) { from, to ->
                for (i in from until to) {
                    val mid = 1f - abs(2f * luma[i] / BYTE_MAX - 1f)
                    delta[i] += (luma[i] - base[i]) * clarity * CLARITY_GAIN * mid
                }
            }
        }
        Parallel.range(px.size) { from, to -> for (i in from until to) px[i] = shifted(px[i], delta[i]) }
    }

    /** Mapa bazowa jasności dla wyrazistości (filtr prowadzony w skali [low]); [base] daje jasność bazy w pikselu. */
    internal class ClarityMap(private val low: Lowres, private val filtered: FloatArray) {
        fun base(x: Int, y: Int): Float = low.sample(filtered, x, y) * BYTE_MAX
    }

    /** Mapa z uśrednionej jasności 0..1 ([lumaUnit]) obrazu zmniejszonego do [low]; ta sama matematyka co w [apply]. */
    internal fun clarityMap(low: Lowres, lumaUnit: FloatArray): ClarityMap {
        val radius = max(2, max(low.w, low.h) / CLARITY_RADIUS_DIV)
        return ClarityMap(low, GuidedFilter.filter(lumaUnit, lumaUnit, low.w, low.h, radius, CLARITY_EPS))
    }

    /**
     * Zmiana jasności (wyostrzanie + wyrazistość) dla pasa [luma] o szerokości [w], zaczynającego się w wierszu
     * [firstRow] obrazu o dłuższym boku [longSide]. Poprawna w wierszach oddalonych od brzegów pasa o co najmniej
     * [Sharpener.marginFor]. [map] to wynik [clarityMap] (wymagany przy [clarity] ≠ 0).
     */
    @Suppress("LongParameterList") // pas, położenie w obrazie i oba ustawienia
    internal fun planeDelta(
        luma: FloatArray,
        w: Int,
        firstRow: Int,
        longSide: Int,
        sharp: SharpenParams,
        clarity: Float,
        map: ClarityMap?
    ): FloatArray {
        val rows = luma.size / w
        val delta = if (sharp.amount > 0f) {
            Sharpener.delta(luma, w, rows, sharp, longSide = longSide)
        } else {
            FloatArray(luma.size)
        }
        if (clarity != 0f && map != null) {
            for (y in 0 until rows) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val mid = 1f - abs(2f * luma[i] / BYTE_MAX - 1f)
                    delta[i] += (luma[i] - map.base(x, firstRow + y)) * clarity * CLARITY_GAIN * mid
                }
            }
        }
        return delta
    }

    /**
     * Warstwa bazowa jasności z filtru prowadzonego w zmniejszonej skali: różnica od niej to lokalny kontrast
     * bez halo przy krawędziach (zwykłe rozmycie rysuje jasne obwódki wokół kontrastowych krawędzi).
     */
    private fun edgePreservingBase(luma: FloatArray, w: Int, h: Int): FloatArray {
        val low = Lowres.forSize(w, h, CLARITY_TARGET)
        val unit = FloatArray(luma.size)
        Parallel.range(unit.size) { from, to -> for (i in from until to) unit[i] = luma[i] / BYTE_MAX }
        val small = low.down(unit)
        val radius = max(2, max(low.w, low.h) / CLARITY_RADIUS_DIV)
        val filtered = GuidedFilter.filter(small, small, low.w, low.h, radius, CLARITY_EPS)
        val base = FloatArray(luma.size)
        Parallel.rows(h, SAMPLE_ROWS) { y0, y1 ->
            for (y in y0 until y1) for (x in 0 until w) base[y * w + x] = low.sample(filtered, x, y) * BYTE_MAX
        }
        return base
    }

    private fun lumaOf(c: Int): Float =
        LUMA_R * ((c shr RED_SHIFT) and MASK) + LUMA_G * ((c shr GREEN_SHIFT) and MASK) + LUMA_B * (c and MASK)

    private fun shifted(c: Int, d: Float): Int {
        fun ch(v: Int) = (v + d + HALF).toInt().coerceIn(0, MASK)
        val r = ch((c shr RED_SHIFT) and MASK)
        val g = ch((c shr GREEN_SHIFT) and MASK)
        val b = ch(c and MASK)
        return (c and ALPHA_MASK) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }

    /** Rozmycie pudełkowe (kilka przebiegów ≈ Gauss), liniowe w czasie, z obcinaniem brzegów. */
    internal fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        var a = src.copyOf()
        var b = FloatArray(src.size)
        repeat(BOX_PASSES) {
            blurRows(a, b, w, h, radius)
            blurCols(b, a, w, h, radius)
        }
        return a
    }

    internal fun blurRows(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val window = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / window
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    internal fun blurCols(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val window = (2 * r + 1).toFloat()
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / window
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }
}
