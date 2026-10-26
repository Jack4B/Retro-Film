package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.min

/**
 * Usuwanie zamglenia metodą „dark channel prior” (He, Sun, Tang 2009) z udoskonaleniem mapy transmisji
 * filtrem prowadzonym ([GuidedFilter]): w obszarach bez mgły co najmniej jeden kanał koloru jest w pobliżu
 * czarny, więc jego jasność wskazuje grubość mgły. Model: I = J·t + A·(1−t); odzyskujemy J = (I−A)/t + A.
 * Mapa t liczona w zmniejszonej skali; sam obraz jest przetwarzany w pełnej rozdzielczości.
 */
@Suppress("TooManyFunctions")
internal object Dehaze {
    private const val TARGET = 640
    private const val CHANNELS = 3
    private const val PATCH_DIV = 90
    private const val GUIDE_DIV = 40
    private const val OMEGA = 0.92f
    private const val T_MIN = 0.28f
    private const val EPS = 0.0012f
    private const val TOP_FRACTION = 0.001f
    private const val A_MIN = 150f
    private const val BYTE_MAX = 255f
    private const val MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val HALF = 0.5f
    private const val SKY_FROM = 0.9f
    private const val SKY_TO = 1.0f
    private const val SKY_MIX_A = 3f
    private const val SKY_MIX_B = 2f
    private const val COMPENSATE = 0.5f
    private const val MAX_GAIN = 1.5f

    /** [amount] 0..1 (0 = bez zmian). Modyfikuje [px] w miejscu. */
    fun apply(px: IntArray, width: Int, height: Int, amount: Float) {
        if (amount <= 0f) return
        val low = Lowres.forSize(width, height, TARGET)
        val chan = Array(CHANNELS) { c -> low.down(FloatArray(px.size) { channel(px[it], c) }) }
        val airlight = airlight(chan, low)
        val t = transmission(chan, airlight, low, amount.coerceAtMost(1f))
        protectSky(chan, airlight, t)
        val before = meanLuma(px)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val tt = max(T_MIN, low.sample(t, x, y))
                val c = px[i]
                px[i] = (c and ALPHA_MASK) or
                    (restore(channel(c, 0), airlight[0], tt) shl RED_SHIFT) or
                    (restore(channel(c, 1), airlight[1], tt) shl GREEN_SHIFT) or
                    restore(channel(c, 2), airlight[2], tt)
            }
        }
        compensate(px, before)
    }

    /**
     * Niebo i inne obszary prawie równe świetle atmosferycznemu mają ciemny kanał wysoki i wyglądałyby jak gęsta mgła
     * (przesadna korekta, szara plama, szum). Tam transmisja jest cofana ku 1.
     */
    private fun protectSky(chan: Array<FloatArray>, a: FloatArray, t: FloatArray) {
        for (i in t.indices) {
            val ratio = min(chan[0][i] / a[0], min(chan[1][i] / a[1], chan[2][i] / a[2]))
            val w = ((ratio - SKY_FROM) / (SKY_TO - SKY_FROM)).coerceIn(0f, 1f)
            t[i] += (1f - t[i]) * w * w * (SKY_MIX_A - SKY_MIX_B * w)
        }
    }

    private fun meanLuma(px: IntArray): Float {
        var sum = 0.0
        for (c in px) sum += LUMA_R * channel(c, 0) + LUMA_G * channel(c, 1) + LUMA_B * channel(c, 2)
        return (sum / px.size).toFloat()
    }

    /** Usuwanie mgły ciemni obraz; przywraca połowę utraconej średniej jasności (do +25%). */
    private fun compensate(px: IntArray, before: Float) {
        val after = meanLuma(px)
        if (after < 1f) return
        val gain = 1f + COMPENSATE * ((before / after).coerceIn(1f, MAX_GAIN) - 1f)
        if (gain <= 1f) return
        for (i in px.indices) {
            val c = px[i]
            px[i] = (c and ALPHA_MASK) or
                (scale(channel(c, 0), gain) shl RED_SHIFT) or
                (scale(channel(c, 1), gain) shl GREEN_SHIFT) or
                scale(channel(c, 2), gain)
        }
    }

    private fun scale(v: Float, g: Float): Int = (v * g + HALF).toInt().coerceIn(0, MASK)

    private fun channel(c: Int, k: Int): Float = ((c shr (RED_SHIFT - GREEN_SHIFT * k)) and MASK).toFloat()

    private fun restore(v: Float, a: Float, t: Float): Int = ((v - a) / t + a + HALF).toInt().coerceIn(0, MASK)

    /** Światło atmosferyczne A: średni kolor pikseli z górnych 0,1% ciemnego kanału, nie ciemniejszy niż [A_MIN]. */
    private fun airlight(chan: Array<FloatArray>, low: Lowres): FloatArray {
        val n = low.w * low.h
        val dark = darkChannel(chan, low, FloatArray(CHANNELS) { BYTE_MAX })
        val take = max(1, (n * TOP_FRACTION).toInt())
        val order = (0 until n).sortedByDescending { dark[it] }.take(take)
        return FloatArray(CHANNELS) { k -> max(A_MIN, order.map { chan[k][it] }.average().toFloat()) }
    }

    /** Ciemny kanał: minimum po kanałach (znormalizowanych przez [a]) i po łatce (2r+1)². */
    private fun darkChannel(chan: Array<FloatArray>, low: Lowres, a: FloatArray): FloatArray {
        val n = low.w * low.h
        val perPixel = FloatArray(n) { min(chan[0][it] / a[0], min(chan[1][it] / a[1], chan[2][it] / a[2])) }
        return minFilter(perPixel, low.w, low.h, max(1, max(low.w, low.h) / PATCH_DIV))
    }

    private fun transmission(chan: Array<FloatArray>, a: FloatArray, low: Lowres, amount: Float): FloatArray {
        val dark = darkChannel(chan, low, a)
        val raw = FloatArray(dark.size) { 1f - OMEGA * amount * dark[it] }
        val guide = FloatArray(dark.size) {
            (LUMA_R * chan[0][it] + LUMA_G * chan[1][it] + LUMA_B * chan[2][it]) / BYTE_MAX
        }
        val r = max(2, max(low.w, low.h) / GUIDE_DIV)
        return GuidedFilter.filter(guide, raw, low.w, low.h, r, EPS)
    }

    private fun minFilter(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = Float.MAX_VALUE
                for (k in max(0, x - r)..min(w - 1, x + r)) m = min(m, src[y * w + k])
                tmp[y * w + x] = m
            }
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = Float.MAX_VALUE
                for (k in max(0, y - r)..min(h - 1, y + r)) m = min(m, tmp[k * w + x])
                out[y * w + x] = m
            }
        }
        return out
    }
}
