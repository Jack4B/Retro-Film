package app.retrofilm.pipeline

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zmiana rozmiaru filtrem Lanczosa (a = 3), rozdzielnie: najpierw poziomo, potem pionowo. Przy zmniejszaniu
 * jądro jest rozciągnięte o współczynnik skali, więc działa jak filtr dolnoprzepustowy (bez aliasingu i bez
 * schodków typowych dla dwuliniowego), a przy powiększaniu jest ostrzejsze od bikubicznego. Wagi są normalizowane
 * do 1, brzegi przedłużane; lekki nadstrzał (dzwonienie) przycinany do 0..255.
 *
 * Filtrowanie odbywa się w świetle liniowym (sRGB → liniowe → sRGB, [linear] = true): uśrednianie jasności w gammie
 * przyciemnia drobne jasne detale i kontrastowe krawędzie (szachownica 0/255 dawała 128 zamiast fizycznego 188).
 *
 * Pamięć: wynik liczony jest pasami wierszy wyjścia ([BAND_ROWS]) równolegle ([Parallel]); każdy pas czyta tylko
 * potrzebne wiersze źródła i trzyma ich poziomo przeskalowaną kopię (float), więc nie ma pełnej kopii źródła ani
 * bufora pośredniego dw×sh.
 */
internal object Resampler {
    private const val LOBES = 3
    private const val MASK = 0xFF
    private const val ALPHA = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val HALF = 0.5f
    private const val LEVEL_MAX = 255
    private const val RGB = 3
    private const val FLOAT_BYTES = 4
    private const val INT_BYTES = 4
    private const val ENCODE_STEPS = 1 shl 14
    internal const val BAND_ROWS = 32

    /** Wagi jednego wymiaru: dla wyjścia i okno próbek [start[i], start[i] + count[i]) o wagach od [offset][i]. */
    private class Taps(val start: IntArray, val count: IntArray, val offset: IntArray, val weights: FloatArray)

    private val toLinear = FloatArray(LEVEL_MAX + 1) { srgbToLinear(it / LEVEL_MAX.toFloat()) }
    private val toSrgb = IntArray(ENCODE_STEPS + 1) {
        (linearToSrgb(it / ENCODE_STEPS.toFloat()) * LEVEL_MAX).roundToInt().coerceIn(0, LEVEL_MAX)
    }
    private val identity = FloatArray(LEVEL_MAX + 1) { it.toFloat() }

    /** Wynik jako spakowane ARGB (alfa 255) o wymiarach [dw]×[dh]. */
    fun resize(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int, linear: Boolean = true): IntArray {
        require(src.size >= sw * sh) { "Za mało pikseli" }
        if (sw == dw && sh == dh) return src.copyOf()
        return resize(sw, sh, dw, dh, linear) { y0, rows, buf -> System.arraycopy(src, y0 * sw, buf, 0, rows * sw) }
    }

    /** Jak wyżej, ale czyta [src] pasami (bez pełnej kopii pikseli w pamięci). */
    fun resize(src: Bitmap, dw: Int, dh: Int, linear: Boolean = true): IntArray {
        val sw = src.width
        return resize(sw, src.height, dw, dh, linear) { y0, rows, buf ->
            synchronized(src) { src.getPixels(buf, 0, sw, 0, y0, sw, rows) }
        }
    }

    /** Szacowana pamięć robocza jednego wątku (bajty) dla pasa – do testów i raportu. */
    fun bandBytes(sw: Int, sh: Int, dw: Int, dh: Int): Long {
        val rows = sourceRows(sh, dh)
        return rows.toLong() * (sw * INT_BYTES + dw * RGB * FLOAT_BYTES)
    }

    private fun sourceRows(sh: Int, dh: Int): Int {
        val scale = sh.toFloat() / dh
        val support = LOBES * max(1f, scale)
        return min(sh, ceil(BAND_ROWS * scale + 2 * support).toInt() + 2)
    }

    private fun resize(
        sw: Int,
        sh: Int,
        dw: Int,
        dh: Int,
        linear: Boolean,
        read: (y0: Int, rows: Int, buf: IntArray) -> Unit
    ): IntArray {
        require(sw > 0 && sh > 0 && dw > 0 && dh > 0) { "Wymiary muszą być dodatnie" }
        val across = taps(sw, dw)
        val down = taps(sh, dh)
        val decode = if (linear) toLinear else identity
        val out = IntArray(dw * dh)
        val bands = (dh + BAND_ROWS - 1) / BAND_ROWS
        Parallel.forEach(bands) { band ->
            val oy0 = band * BAND_ROWS
            val oy1 = min(dh, oy0 + BAND_ROWS)
            // zakres wierszy źródła potrzebny pasowi (z przedłużeniem brzegów)
            var lo = sh
            var hi = -1
            for (oy in oy0 until oy1) {
                lo = min(lo, down.start[oy].coerceIn(0, sh - 1))
                hi = max(hi, (down.start[oy] + down.count[oy] - 1).coerceIn(0, sh - 1))
            }
            val rows = hi - lo + 1
            val px = IntArray(rows * sw)
            read(lo, rows, px)
            val wide = FloatArray(rows * dw * RGB)
            for (r in 0 until rows) horizontal(px, r * sw, sw, wide, r * dw * RGB, dw, across, decode)
            for (oy in oy0 until oy1) vertical(wide, lo, rows, dw, out, oy, down, linear)
        }
        return out
    }

    private fun horizontal(
        px: IntArray,
        base: Int,
        sw: Int,
        dst: FloatArray,
        dstBase: Int,
        dw: Int,
        taps: Taps,
        decode: FloatArray
    ) {
        val last = sw - 1
        for (i in 0 until dw) {
            var r = 0f
            var g = 0f
            var b = 0f
            val first = taps.start[i]
            val wBase = taps.offset[i]
            for (k in 0 until taps.count[i]) {
                val c = px[base + (first + k).coerceIn(0, last)]
                val w = taps.weights[wBase + k]
                r += w * decode[(c shr RED_SHIFT) and MASK]
                g += w * decode[(c shr GREEN_SHIFT) and MASK]
                b += w * decode[c and MASK]
            }
            val d = dstBase + i * RGB
            dst[d] = r
            dst[d + 1] = g
            dst[d + 2] = b
        }
    }

    @Suppress("LongParameterList")
    private fun vertical(
        wide: FloatArray,
        lo: Int,
        rows: Int,
        dw: Int,
        out: IntArray,
        oy: Int,
        taps: Taps,
        linear: Boolean
    ) {
        val first = taps.start[oy]
        val wBase = taps.offset[oy]
        val n = taps.count[oy]
        val stride = dw * RGB
        for (x in 0 until dw) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in 0 until n) {
                val row = ((first + k).coerceIn(lo, lo + rows - 1) - lo)
                val s = row * stride + x * RGB
                val w = taps.weights[wBase + k]
                r += w * wide[s]
                g += w * wide[s + 1]
                b += w * wide[s + 2]
            }
            out[oy * dw + x] = ALPHA or (encode(r, linear) shl RED_SHIFT) or (encode(g, linear) shl GREEN_SHIFT) or
                encode(b, linear)
        }
    }

    private fun encode(v: Float, linear: Boolean): Int = if (linear) {
        toSrgb[(v * ENCODE_STEPS + HALF).toInt().coerceIn(0, ENCODE_STEPS)]
    } else {
        (v + HALF).toInt().coerceIn(0, LEVEL_MAX)
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
        val perOutput = ceil(2f * support).toInt() + 1
        var weights = FloatArray(outSize * perOutput)
        var size = 0
        for (i in 0 until outSize) {
            val center = (i + HALF) * scale - HALF
            val first = floor(center - support).toInt() + 1
            val last = floor(center + support).toInt()
            start[i] = first
            count[i] = last - first + 1
            offset[i] = size
            if (size + count[i] > weights.size) weights = weights.copyOf(max(weights.size * 2, size + count[i]))
            var sum = 0f
            for (j in first..last) {
                val w = kernel((j - center) / stretch)
                weights[size++] = w
                sum += w
            }
            for (k in offset[i] until size) weights[k] = weights[k] / sum
        }
        return Taps(start, count, offset, weights)
    }

    private const val SMALL = 1e-6f
    private const val SRGB_KNEE = 0.04045f
    private const val LINEAR_KNEE = 0.0031308f
    private const val SLOPE = 12.92f
    private const val OFFSET = 0.055f
    private const val GAMMA = 2.4f

    private fun srgbToLinear(v: Float): Float =
        if (v <= SRGB_KNEE) v / SLOPE else ((v + OFFSET) / (1 + OFFSET)).toDouble().pow(GAMMA.toDouble()).toFloat()

    private fun linearToSrgb(v: Float): Float =
        if (v <= LINEAR_KNEE) v * SLOPE else (1 + OFFSET) * v.toDouble().pow(1.0 / GAMMA).toFloat() - OFFSET
}
