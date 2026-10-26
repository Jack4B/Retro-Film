package app.retrofilm.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Zmiana rozmiaru filtrem Lanczosa (a = 3), rozdzielnie: najpierw poziomo, potem pionowo. Przy zmniejszaniu
 * jądro jest rozciągnięte o współczynnik skali, więc działa jak filtr dolnoprzepustowy (bez aliasingu i bez
 * schodków typowych dla dwuliniowego), a przy powiększaniu jest ostrzejsze od bikubicznego. Wagi są normalizowane
 * do 1, brzegi przedłużane; lekki nadstrzał (dzwonienie) przycinany do 0..255.
 */
internal object Resampler {
    private const val LOBES = 3
    private const val MASK = 0xFF
    private const val ALPHA = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val HALF = 0.5f
    private const val LEVEL_MAX = 255

    /** Wagi jednego wymiaru: dla wyjścia i okno próbek [start[i], start[i] + count[i]) o wagach od [offset][i]. */
    private class Taps(val start: IntArray, val count: IntArray, val offset: IntArray, val weights: FloatArray)

    /** Linia pikseli w tablicy: indeks pierwszego, krok między sąsiednimi i liczba pikseli. */
    private class Lane(val base: Int, val step: Int, val size: Int)

    /** Wynik jako spakowane ARGB (alfa 255) o wymiarach [dw]×[dh]. */
    fun resize(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        require(sw > 0 && sh > 0 && dw > 0 && dh > 0) { "Wymiary muszą być dodatnie" }
        if (sw == dw && sh == dh) return src.copyOf()
        val across = taps(sw, dw)
        val down = taps(sh, dh)
        val wide = IntArray(dw * sh)
        for (y in 0 until sh) pass(src, Lane(y * sw, 1, sw), wide, Lane(y * dw, 1, dw), across)
        val out = IntArray(dw * dh)
        for (x in 0 until dw) pass(wide, Lane(x, dw, sh), out, Lane(x, dw, dh), down)
        return out
    }

    /** Waga Lanczosa 3: sinc(x)·sinc(x/3) w |x| < 3. */
    fun kernel(x: Float): Float {
        val a = abs(x)
        val px = PI.toFloat() * a
        return when {
            a >= LOBES -> 0f
            a < SMALL -> 1f
            else -> LOBES * sin(px) * sin(px / LOBES) / (px * px)
        }
    }

    private fun taps(inSize: Int, outSize: Int): Taps {
        val scale = inSize.toFloat() / outSize
        val stretch = max(1f, scale)
        val support = LOBES * stretch
        val start = IntArray(outSize)
        val count = IntArray(outSize)
        val offset = IntArray(outSize)
        val all = ArrayList<Float>(outSize * (ceil(2f * support).toInt() + 1))
        for (i in 0 until outSize) {
            val center = (i + HALF) * scale - HALF
            val first = floor(center - support).toInt() + 1
            val last = floor(center + support).toInt()
            start[i] = first
            count[i] = last - first + 1
            offset[i] = all.size
            var sum = 0f
            for (j in first..last) {
                val w = kernel((j - center) / stretch)
                all.add(w)
                sum += w
            }
            for (k in offset[i] until all.size) all[k] = all[k] / sum
        }
        return Taps(start, count, offset, all.toFloatArray())
    }

    private fun pass(src: IntArray, from: Lane, dst: IntArray, to: Lane, taps: Taps) {
        val last = from.size - 1
        for (i in 0 until to.size) {
            var r = 0f
            var g = 0f
            var b = 0f
            val first = taps.start[i]
            val base = taps.offset[i]
            for (k in 0 until taps.count[i]) {
                val c = src[from.base + (first + k).coerceIn(0, last) * from.step]
                val w = taps.weights[base + k]
                r += w * ((c shr RED_SHIFT) and MASK)
                g += w * ((c shr GREEN_SHIFT) and MASK)
                b += w * (c and MASK)
            }
            dst[to.base + i * to.step] = ALPHA or (level(r) shl RED_SHIFT) or (level(g) shl GREEN_SHIFT) or level(b)
        }
    }

    private const val SMALL = 1e-6f

    private fun level(v: Float): Int = (v + HALF).toInt().coerceIn(0, LEVEL_MAX)
}
