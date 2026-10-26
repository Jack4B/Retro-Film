package app.retrofilm.pipeline

import kotlin.math.max

/**
 * Odszumianie kolorów: szum matrycy w telefonie to głównie szum chrominancji (kolorowe plamki). Obraz rozkładany
 * jest na jasność Y i różnice barwy Cb/Cr (BT.601); Cb/Cr są wygładzane filtrem prowadzonym ([GuidedFilter])
 * z jasnością jako przewodnikiem, więc kolory nie „wyciekają” przez krawędzie, a jasność (szczegóły) zostaje
 * nietknięta.
 */
internal object ChromaDenoise {
    internal const val TARGET = 900
    private const val RADIUS_DIV = 150
    private const val EPS_MAX = 0.02f
    private const val EPS_MIN = 0.0008f
    private const val MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val BYTE_MAX = 255f
    private const val HALF = 0.5f
    private const val KR = 0.299f
    private const val KG = 0.587f
    private const val KB = 0.114f
    private const val ROWS = 64

    /** [amount] 0..1 (0 = bez zmian). Modyfikuje [px] w miejscu. */
    fun apply(px: IntArray, width: Int, height: Int, amount: Float) {
        if (amount <= 0f) return
        val k = amount.coerceAtMost(1f)
        val n = px.size
        val y = FloatArray(n)
        val cb = FloatArray(n)
        val cr = FloatArray(n)
        Parallel.range(n) { from, to ->
            for (i in from until to) {
                val r = ((px[i] shr RED_SHIFT) and MASK).toFloat()
                val g = ((px[i] shr GREEN_SHIFT) and MASK).toFloat()
                val b = (px[i] and MASK).toFloat()
                y[i] = KR * r + KG * g + KB * b
                cb[i] = b - y[i]
                cr[i] = r - y[i]
            }
        }
        val low = Lowres.forSize(width, height, TARGET)
        val guide = low.down(scaled(n) { y[it] / BYTE_MAX })
        val radius = max(1, max(low.w, low.h) / RADIUS_DIV)
        val eps = EPS_MIN + (EPS_MAX - EPS_MIN) * k
        val smoothCb = GuidedFilter.filter(
            guide,
            low.down(scaled(n) { (cb[it] + BYTE_MAX) / (2 * BYTE_MAX) }),
            low.w,
            low.h,
            radius,
            eps
        )
        val smoothCr = GuidedFilter.filter(
            guide,
            low.down(scaled(n) { (cr[it] + BYTE_MAX) / (2 * BYTE_MAX) }),
            low.w,
            low.h,
            radius,
            eps
        )
        Parallel.rows(height, ROWS) { j0, j1 ->
            for (j in j0 until j1) {
                for (i in 0 until width) {
                    val idx = j * width + i
                    val ncb = cb[idx] + (low.sample(smoothCb, i, j) * (2 * BYTE_MAX) - BYTE_MAX - cb[idx]) * k
                    val ncr = cr[idx] + (low.sample(smoothCr, i, j) * (2 * BYTE_MAX) - BYTE_MAX - cr[idx]) * k
                    val r = y[idx] + ncr
                    val b = y[idx] + ncb
                    val g = (y[idx] - KR * r - KB * b) / KG
                    px[idx] = (px[idx] and ALPHA_MASK) or (to8(r) shl RED_SHIFT) or (to8(g) shl GREEN_SHIFT) or to8(b)
                }
            }
        }
    }

    private inline fun scaled(n: Int, crossinline f: (Int) -> Float): FloatArray {
        val out = FloatArray(n)
        Parallel.range(n) { from, to -> for (i in from until to) out[i] = f(i) }
        return out
    }

    private fun to8(v: Float): Int = (v + HALF).toInt().coerceIn(0, MASK)

    /** Wygładzone (w [low]) różnice barwy: wynik analizy całego obrazu, stosowany pasami przez [applyPlanes]. */
    class Plan(val low: Lowres, val smoothCb: FloatArray, val smoothCr: FloatArray, val k: Float)

    /** Dokłada piksel ([x], [y]) o kanałach [r], [g], [b] do sum jasności i różnic barwy (jak w [apply]). */
    @Suppress("LongParameterList") // gorąca pętla: próbka z trzema kanałami i położeniem bez alokacji
    fun accumulate(acc: Lowres.Accumulator, x: Int, y: Int, r: Float, g: Float, b: Float) {
        val luma = KR * r + KG * g + KB * b
        acc.add(x, y, luma / BYTE_MAX, (b - luma + BYTE_MAX) / (2 * BYTE_MAX), (r - luma + BYTE_MAX) / (2 * BYTE_MAX))
    }

    /** Plan z uśrednionych sum [acc] obrazu zmniejszonego do [low]; null przy [amount] = 0. */
    fun plan(acc: Lowres.Accumulator, low: Lowres, amount: Float): Plan? {
        if (amount <= 0f) return null
        val k = amount.coerceAtMost(1f)
        val guide = acc.mean(0)
        val radius = max(1, max(low.w, low.h) / RADIUS_DIV)
        val eps = EPS_MIN + (EPS_MAX - EPS_MIN) * k
        val smoothCb = GuidedFilter.filter(guide, acc.mean(1), low.w, low.h, radius, eps)
        val smoothCr = GuidedFilter.filter(guide, acc.mean(2), low.w, low.h, radius, eps)
        return Plan(low, smoothCb, smoothCr, k)
    }

    /**
     * To samo co [apply] na pasie pikseli w skali 0..255 bez zaokrągleń: [count] pikseli, rzędy o szerokości
     * [PixelSpan.width] od wiersza [PixelSpan.firstRow]; [xStep] > 1 dla podpróbkowanych wierszy (x = indeks · krok).
     */
    fun applyPlanes(plan: Plan, planes: Planes, count: Int, span: PixelSpan, xStep: Int = 1) {
        var x = 0
        var row = span.firstRow
        for (i in 0 until count) {
            val r = planes.r[i]
            val b = planes.b[i]
            val luma = KR * r + KG * planes.g[i] + KB * b
            val cb = b - luma
            val cr = r - luma
            val px = x * xStep
            val ncb = cb + (plan.low.sample(plan.smoothCb, px, row) * (2 * BYTE_MAX) - BYTE_MAX - cb) * plan.k
            val ncr = cr + (plan.low.sample(plan.smoothCr, px, row) * (2 * BYTE_MAX) - BYTE_MAX - cr) * plan.k
            val nr = luma + ncr
            val nb = luma + ncb
            planes.r[i] = nr.coerceIn(0f, BYTE_MAX)
            planes.g[i] = ((luma - KR * nr - KB * nb) / KG).coerceIn(0f, BYTE_MAX)
            planes.b[i] = nb.coerceIn(0f, BYTE_MAX)
            if (++x == span.width) {
                x = 0
                row++
            }
        }
    }
}
