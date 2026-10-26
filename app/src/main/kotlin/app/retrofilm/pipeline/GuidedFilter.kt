package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.min

/**
 * Filtr prowadzony (guided filter, He, Sun, Tang 2010/2013): wygładzanie z zachowaniem krawędzi w czasie
 * liniowym względem liczby pikseli, bez halo typowego dla zwykłego rozmycia. Działa na obrazie w zmniejszonej
 * skali ([Lowres]), a wynik jest interpolowany dwuliniowo, więc zużycie pamięci i czasu jest niewielkie.
 */
internal object GuidedFilter {
    /** Średnia w oknie (2r+1)² – jeden przebieg pudełkowy. */
    fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        DetailFilter.blurRows(src, tmp, w, h, r)
        DetailFilter.blurCols(tmp, out, w, h, r)
        return out
    }

    /** Wygładza [p] z krawędziami z [guide]; [eps] to próg wariancji (większy = silniejsze wygładzenie). */
    @Suppress("LongParameterList")
    fun filter(guide: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = guide.size
        val meanI = boxMean(guide, w, h, r)
        val meanP = if (p === guide) meanI else boxMean(p, w, h, r)
        val corrI = boxMean(FloatArray(n) { guide[it] * guide[it] }, w, h, r)
        val corrIp = if (p === guide) corrI else boxMean(FloatArray(n) { guide[it] * p[it] }, w, h, r)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val varI = corrI[i] - meanI[i] * meanI[i]
            val cov = corrIp[i] - meanI[i] * meanP[i]
            a[i] = cov / (varI + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxMean(a, w, h, r)
        val meanB = boxMean(b, w, h, r)
        return FloatArray(n) { meanA[it] * guide[it] + meanB[it] }
    }
}

/** Zmniejszona kopia obrazu (uśrednianie bloków [factor]×[factor]) z interpolacją dwuliniową w drugą stronę. */
internal class Lowres(val width: Int, val height: Int, val factor: Int) {
    val w = max(1, (width + factor - 1) / factor)
    val h = max(1, (height + factor - 1) / factor)

    /** Uśrednia kanał o rozmiarze [width]×[height] do rozmiaru [w]×[h]. */
    fun down(src: FloatArray): FloatArray {
        val out = FloatArray(w * h)
        val cnt = IntArray(w * h)
        for (y in 0 until height) {
            val row = (y / factor) * w
            for (x in 0 until width) {
                out[row + x / factor] += src[y * width + x]
                cnt[row + x / factor]++
            }
        }
        for (i in out.indices) out[i] /= cnt[i]
        return out
    }

    /**
     * Uśrednia kanały R, G, B (0..255) pikseli ARGB [px] ([width]×[height]) do rozmiaru [w]×[h], bez pełnowymiarowych
     * tablic pośrednich. Wynik taki sam jak [down] kanału po kanale (ta sama kolejność sumowania w komórce); komórki
     * w wierszu są niezależne, więc wiersze liczą się równolegle.
     */
    fun downRgb(px: IntArray): Array<FloatArray> {
        val out = Array(RGB_CHANNELS) { FloatArray(w * h) }
        Parallel.forEach(h) { by ->
            for (bx in 0 until w) cellMean(px, bx, by, out)
        }
        return out
    }

    private fun cellMean(px: IntArray, bx: Int, by: Int, out: Array<FloatArray>) {
        val y0 = by * factor
        val y1 = min(height, y0 + factor)
        val x0 = bx * factor
        val x1 = min(width, x0 + factor)
        var r = 0f
        var g = 0f
        var b = 0f
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = px[y * width + x]
                r += ((c shr RED_SHIFT) and BYTE_MASK).toFloat()
                g += ((c shr GREEN_SHIFT) and BYTE_MASK).toFloat()
                b += (c and BYTE_MASK).toFloat()
            }
        }
        val n = ((y1 - y0) * (x1 - x0)).toFloat()
        out[0][by * w + bx] = r / n
        out[1][by * w + bx] = g / n
        out[2][by * w + bx] = b / n
    }

    /** Wartość [low] (rozmiar [w]×[h]) w punkcie pełnej rozdzielczości ([x], [y]). */
    fun sample(low: FloatArray, x: Int, y: Int): Float {
        val fx = ((x + HALF) / factor - HALF).coerceIn(0f, (w - 1).toFloat())
        val fy = ((y + HALF) / factor - HALF).coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = low[y0 * w + x0] * (1 - tx) + low[y0 * w + x1] * tx
        val bottom = low[y1 * w + x0] * (1 - tx) + low[y1 * w + x1] * tx
        return top * (1 - ty) + bottom * ty
    }

    companion object {
        private const val HALF = 0.5f
        private const val RGB_CHANNELS = 3
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BYTE_MASK = 0xFF

        /** Współczynnik zmniejszenia tak, by dłuższy bok miał ok. [target] pikseli. */
        fun forSize(width: Int, height: Int, target: Int): Lowres = Lowres(
            width,
            height,
            max(
                1,
                max(width, height) / target
            )
        )
    }
}
