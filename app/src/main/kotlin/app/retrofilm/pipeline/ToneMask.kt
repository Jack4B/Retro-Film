package app.retrofilm.pipeline

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

/**
 * Warstwa bazowa jasności (siatka niskiej rozdzielczości wygładzona filtrem prowadzonym, He i in.) do cieni i
 * świateł: rozkład obrazu na bazę (duże, gładkie obszary tonalne) i detal. Wpływ zależy od bazy, więc detale nie
 * spłaszczają się, a filtr zachowujący krawędzie nie przenosi jasności przez kontrastowe granice (brak aureoli
 * i „przecieku” cienia na jasny obiekt na ciemnym tle). Siatka ma ok. [GRID_LONG] komórek po dłuższym boku.
 */
class ToneMask private constructor(
    private val gridW: Int,
    private val gridH: Int,
    private val cell: Int,
    private val data: FloatArray,
    private val mid: FloatArray,
    private val midRatio: FloatArray
) {
    /** Lokalna jasność (0..1, kodowanie gamma) w punkcie ([x], [y]) obrazu, interpolacja dwuliniowa. */
    fun sample(x: Int, y: Int): Float = bilinear(data, x, y) / BYTE_MAX

    /**
     * Trzy warstwy w punkcie ([x], [y]) zapisane do [out]: baza gruba (jak [sample]), baza średnia (filtr z małym
     * promieniem, zachowuje drobniejsze krawędzie; 0..1) i ln(średnia / gruba) – detal skali średniej.
     */
    fun sampleLayers(x: Int, y: Int, out: FloatArray) {
        out[0] = bilinear(data, x, y) / BYTE_MAX
        out[1] = bilinear(mid, x, y) / BYTE_MAX
        out[2] = bilinear(midRatio, x, y)
    }

    private fun bilinear(data: FloatArray, x: Int, y: Int): Float {
        val fx = ((x + HALF) / cell - HALF).coerceIn(0f, (gridW - 1).toFloat())
        val fy = ((y + HALF) / cell - HALF).coerceIn(0f, (gridH - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, gridW - 1)
        val y1 = minOf(y0 + 1, gridH - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = data[y0 * gridW + x0] * (1 - tx) + data[y0 * gridW + x1] * tx
        val bottom = data[y1 * gridW + x0] * (1 - tx) + data[y1 * gridW + x1] * tx
        return top * (1 - ty) + bottom * ty
    }

    /** Zbiera jasność z kolejnych porcji pikseli, potem [build] rozmywa siatkę. */
    class Builder(private val width: Int, private val height: Int) {
        private val cell = max(1, max(width, height) / GRID_LONG)
        private val gridW = ceil(width / cell.toFloat()).toInt()
        private val gridH = ceil(height / cell.toFloat()).toInt()
        private val sums = FloatArray(gridW * gridH)
        private val counts = IntArray(gridW * gridH)

        /** Rozmiar komórki siatki w pikselach obrazu. */
        val cellSize: Int get() = cell

        /** Dodaje pojedynczą próbkę jasności (0..255) z piksela ([x], [y]) – do zbierania z podpróbkowania. */
        fun addSample(x: Int, y: Int, luma: Float) {
            val idx = (y / cell) * gridW + x / cell
            sums[idx] += luma
            counts[idx]++
        }

        /** Dodaje sumy z [other] (budowniczy innego wątku dla tego samego obrazu). */
        fun merge(other: Builder) {
            for (i in sums.indices) sums[i] += other.sums[i]
            for (i in counts.indices) counts[i] += other.counts[i]
        }

        fun add(px: IntArray, count: Int, rowWidth: Int, firstRow: Int) {
            for (i in 0 until count) {
                val c = px[i]
                val luma = LUMA_R * ((c shr RED_SHIFT) and MASK) + LUMA_G * ((c shr GREEN_SHIFT) and MASK) +
                    LUMA_B * (c and MASK)
                val idx = ((firstRow + i / rowWidth) / cell) * gridW + (i % rowWidth) / cell
                sums[idx] += luma
                counts[idx]++
            }
        }

        fun build(): ToneMask {
            // komórka bez próbek (podpróbkowanie) bierze wartość poprzedniej
            val avg = FloatArray(sums.size)
            for (i in avg.indices) {
                avg[i] = when {
                    counts[i] > 0 -> sums[i] / counts[i]
                    i > 0 -> avg[i - 1]
                    else -> 0f
                }
            }
            val unit = FloatArray(avg.size) { avg[it] / BYTE_MAX }
            val base = GuidedFilter.filter(unit, unit, gridW, gridH, max(2, GRID_LONG / RADIUS_DIV), EDGE_EPS)
            val fine = GuidedFilter.filter(unit, unit, gridW, gridH, max(1, GRID_LONG / MID_RADIUS_DIV), MID_EDGE_EPS)
            val coarse = FloatArray(base.size) { base[it].coerceIn(0f, 1f) }
            val medium = FloatArray(fine.size) { fine[it].coerceIn(0f, 1f) }
            val ratio = FloatArray(fine.size) {
                ln(max(medium[it], MIN_LUMA) / max(coarse[it], MIN_LUMA))
            }
            return ToneMask(
                gridW,
                gridH,
                cell,
                FloatArray(coarse.size) { coarse[it] * BYTE_MAX },
                FloatArray(medium.size) { medium[it] * BYTE_MAX },
                ratio
            )
        }
    }

    private companion object {
        const val HALF = 0.5f
        const val BYTE_MAX = 255f
        const val GRID_LONG = 512
        const val RADIUS_DIV = 25

        /** Skala średnia: promień ok. 0,6% dłuższego boku (siatka 512 → 3 komórki), czulszy próg krawędzi. */
        const val MID_RADIUS_DIV = 170
        const val MID_EDGE_EPS = 0.004f
        const val MIN_LUMA = 1e-4f

        /** Próg wariancji filtra: różnice jasności większe niż ok. 0,1 są traktowane jako krawędź. */
        const val EDGE_EPS = 0.012f
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val MASK = 0xFF
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}
