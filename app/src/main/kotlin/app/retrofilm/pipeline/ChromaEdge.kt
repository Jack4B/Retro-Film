package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.min

/**
 * Odtworzenie ostrych krawędzi koloru po kompresji JPEG z podpróbkowaniem chromy 4:2:0: aparat zapisuje kolor w
 * połowie rozdzielczości, a dekoder rozciąga go gładko, więc kolor „wylewa się” poza krawędź obiektu (czerwony
 * dach na niebie, zielone liście na jasnym tle). Różnice barwne Cb i Cr są odtwarzane z jednolitej jasnościowo
 * strony krawędzi: dla każdego piksela porównujemy wariancję jasności w oknach (2r+1)² przesuniętych o r w czterech
 * kierunkach ukośnych (jak w filtrze Kuwahary) i, gdy któreś jest wyraźnie bardziej jednolite niż okno wokół
 * piksela, bierzemy z niego średnią chromę. Filtr prowadzony tu nie wystarcza: jest wygładzaniem i rozmywa
 * krawędź tak samo jak dekoder. Jasność zostaje bez zmian, płaskie obszary i krawędzie bez kontrastu jasności
 * (okno wokół piksela wygrywa) nie zmieniają się wcale.
 */
internal object ChromaEdge {
    private const val RADIUS_DIV = 2500
    private const val MIN_RADIUS = 2
    private const val VAR_FLOOR = 9f
    private const val SELECT = 0.5f
    private const val MAX = 255f
    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f

    /** Parametry z siły [amount] 0..1; [margin] to zapas wierszy pasa na okna. */
    class Plan(val radius: Int, val amount: Float) {
        val margin: Int get() = radius * 3 + 1
    }

    fun plan(width: Int, height: Int, amount: Float): Plan? {
        if (amount <= 0f) return null
        return Plan(max(MIN_RADIUS, max(width, height) / RADIUS_DIV), amount.coerceAtMost(1f))
    }

    /** Odtwarza Cb i Cr pasa [count] pikseli (całe wiersze o szerokości [width], skala 0..255), jasność bez zmian. */
    fun applyPlanes(plan: Plan, planes: Planes, count: Int, width: Int) {
        val height = count / width
        val r = plan.radius
        val luma = FloatArray(count) { LUMA_R * planes.r[it] + LUMA_G * planes.g[it] + LUMA_B * planes.b[it] }
        val cb = FloatArray(count) { planes.b[it] - luma[it] }
        val cr = FloatArray(count) { planes.r[it] - luma[it] }
        val meanY = GuidedFilter.boxMean(luma, width, height, r)
        val meanYY = GuidedFilter.boxMean(FloatArray(count) { luma[it] * luma[it] }, width, height, r)
        val variance = FloatArray(count) { max(meanYY[it] - meanY[it] * meanY[it], VAR_FLOOR) }
        val meanCb = GuidedFilter.boxMean(cb, width, height, r)
        val meanCr = GuidedFilter.boxMean(cr, width, height, r)
        val a = plan.amount
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val j = bestWindow(variance, x, y, width, height, r)
                val newCb = cb[i] + a * (meanCb[j] - cb[i])
                val newCr = cr[i] + a * (meanCr[j] - cr[i])
                val red = luma[i] + newCr
                val blue = luma[i] + newCb
                planes.r[i] = red.coerceIn(0f, MAX)
                planes.b[i] = blue.coerceIn(0f, MAX)
                planes.g[i] = ((luma[i] - LUMA_R * red - LUMA_B * blue) / LUMA_G).coerceIn(0f, MAX)
            }
        }
    }

    /** Środek okna (wokół piksela albo przesuniętego o ±r po przekątnych) o najmniejszej wariancji jasności. */
    @Suppress("LongParameterList") // gorąca pętla po pikselach: jedna ramka danych byłaby alokacją na piksel
    private fun bestWindow(variance: FloatArray, x: Int, y: Int, width: Int, height: Int, r: Int): Int {
        val center = y * width + x
        var best = center
        var bestVar = variance[center] * SELECT
        for (dy in -r..r step 2 * r) {
            for (dx in -r..r step 2 * r) {
                val j = min(max(y + dy, 0), height - 1) * width + min(max(x + dx, 0), width - 1)
                if (variance[j] < bestVar) {
                    best = j
                    bestVar = variance[j]
                }
            }
        }
        return best
    }
}
