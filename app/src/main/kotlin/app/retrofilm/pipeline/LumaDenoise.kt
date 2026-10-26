package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.min

/**
 * Odszumianie jasności filtrem prowadzonym (He i in.) sterowanym poziomem szumu: próg wariancji eps = (σ·k)²,
 * gdzie σ to szum zmierzony metodą Immerkaera ([SharpenPlanner.noiseSigma]), a k rośnie z siłą. Tekstury i krawędzie
 * o kontraście większym niż szum zostają (filtr prowadzony nie rozmywa przez krawędzie), płaskie obszary są
 * wygładzane. Zmiana jasności jest dodawana do wszystkich kanałów, więc barwy się nie przesuwają. Liczone paskami
 * wierszy z zapasem na promień filtra (stała pamięć niezależnie od rozdzielczości).
 */
internal object LumaDenoise {
    private const val RADIUS_DIV = 1500
    private const val MIN_RADIUS = 2
    private const val STRIP_ROWS = 192
    private const val K_MIN = 0.7f
    private const val K_SPAN = 2.8f
    private const val MIN_SIGMA = 1f
    private const val MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val HALF = 0.5f
    private const val MAX = 255f
    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f

    /** [amount] 0..1 (0 = bez zmian). [sigma] < 0 = zmierz szum na obrazie. Modyfikuje [px] w miejscu. */
    @Suppress("LongParameterList") // sigma i stripRows tylko dla testów
    fun apply(px: IntArray, width: Int, height: Int, amount: Float, sigma: Float = -1f, stripRows: Int = STRIP_ROWS) {
        if (amount <= 0f) return
        val luma = SharpenPlanner.lumaOf(px)
        val noise = max(MIN_SIGMA, if (sigma >= 0f) sigma else SharpenPlanner.noiseSigma(luma, width, height))
        val k = K_MIN + K_SPAN * amount.coerceAtMost(1f)
        val eps = (noise * k) * (noise * k)
        val radius = max(MIN_RADIUS, max(width, height) / RADIUS_DIV)
        val margin = radius * 2 + 1
        Parallel.rows(height, stripRows) { y0, y1 ->
            val a = max(0, y0 - margin)
            val b = min(height, y1 + margin)
            val sub = luma.copyOfRange(a * width, b * width)
            val smooth = GuidedFilter.filter(sub, sub, width, b - a, radius, eps)
            for (y in y0 until y1) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val d = smooth[(y - a) * width + x] - luma[i]
                    px[i] = shifted(px[i], d)
                }
            }
        }
    }

    /** Parametry filtra z miary szumu całego obrazu: promień okna i próg wariancji; [margin] to zapas wierszy pasa. */
    class Plan(val radius: Int, val eps: Float) {
        val margin: Int get() = radius * 2 + 1
    }

    /** Plan odszumiania jasności dla obrazu [width]×[height] i szumu [sigma] (zmierzonego na całości); null przy 0. */
    fun plan(width: Int, height: Int, amount: Float, sigma: Float): Plan? {
        if (amount <= 0f) return null
        val noise = max(MIN_SIGMA, sigma)
        val k = K_MIN + K_SPAN * amount.coerceAtMost(1f)
        return Plan(max(MIN_RADIUS, max(width, height) / RADIUS_DIV), (noise * k) * (noise * k))
    }

    /** To samo co [apply] na pasie [count] pikseli (całe wiersze o szerokości [width]), skala 0..255. */
    fun applyPlanes(plan: Plan, planes: Planes, count: Int, width: Int) {
        val luma = FloatArray(count) { LUMA_R * planes.r[it] + LUMA_G * planes.g[it] + LUMA_B * planes.b[it] }
        val smooth = GuidedFilter.filter(luma, luma, width, count / width, plan.radius, plan.eps)
        for (i in 0 until count) {
            val d = smooth[i] - luma[i]
            planes.r[i] = (planes.r[i] + d).coerceIn(0f, MAX)
            planes.g[i] = (planes.g[i] + d).coerceIn(0f, MAX)
            planes.b[i] = (planes.b[i] + d).coerceIn(0f, MAX)
        }
    }

    private fun shifted(c: Int, d: Float): Int {
        fun ch(v: Int) = (v + d + HALF).toInt().coerceIn(0, MASK)
        return (c and ALPHA_MASK) or (ch((c shr RED_SHIFT) and MASK) shl RED_SHIFT) or
            (ch((c shr GREEN_SHIFT) and MASK) shl GREEN_SHIFT) or ch(c and MASK)
    }
}
