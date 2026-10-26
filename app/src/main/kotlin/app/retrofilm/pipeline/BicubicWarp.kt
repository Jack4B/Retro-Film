package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.floor

/**
 * Obrót z interpolacją bikubiczną Catmull–Rom (16 próbek na piksel, ostrzejsza od dwuliniowej i bez jej
 * rozmycia; lekki nadstrzał przycinany do 0..255). Odwzorowanie odwrotne wyjście → źródło z macierzy rzutowej
 * [forward] (źródło → wyjście; afinicznej albo z perspektywą), brzegi źródła przedłużane. Używane przy prostowaniu
 * i korekcie perspektywy, bo obrót o wielokrotność 90° i przesunięcie o całkowite piksele są dokładne bez interpolacji.
 */
internal object BicubicWarp {
    private const val HALF = 0.5f
    private const val MASK = 0xFF
    private const val ALPHA = 0xFF shl 24
    private const val MAX_LEVEL = 255f

    fun warp(src: Bitmap, forward: Matrix, outW: Int, outH: Int, make: (Int, Int) -> Bitmap): Bitmap {
        val inv = Matrix()
        check(forward.invert(inv)) { "Macierz nieodwracalna" }
        val v = FloatArray(MATRIX_SIZE)
        inv.getValues(v)
        val sw = src.width
        val sh = src.height
        val pixels = IntArray(sw * sh)
        src.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        val out = IntArray(outW * outH)
        val wx = FloatArray(TAPS)
        val wy = FloatArray(TAPS)
        for (y in 0 until outH) {
            val py = y + HALF
            for (x in 0 until outW) {
                val px = x + HALF
                val d = v[Matrix.MPERSP_0] * px + v[Matrix.MPERSP_1] * py + v[Matrix.MPERSP_2]
                val sx = (v[Matrix.MSCALE_X] * px + v[Matrix.MSKEW_X] * py + v[Matrix.MTRANS_X]) / d - HALF
                val sy = (v[Matrix.MSKEW_Y] * px + v[Matrix.MSCALE_Y] * py + v[Matrix.MTRANS_Y]) / d - HALF
                out[y * outW + x] = sample(pixels, sw, sh, sx, sy, wx, wy)
            }
        }
        val bmp = make(outW, outH)
        bmp.setPixels(out, 0, outW, 0, 0, outW, outH)
        return bmp
    }

    @Suppress("LongParameterList")
    private fun sample(p: IntArray, w: Int, h: Int, sx: Float, sy: Float, wx: FloatArray, wy: FloatArray): Int {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        weights(sx - x0, wx)
        weights(sy - y0, wy)
        var r = 0f
        var g = 0f
        var b = 0f
        for (j in 0 until TAPS) {
            val row = (y0 - 1 + j).coerceIn(0, h - 1) * w
            var rr = 0f
            var gg = 0f
            var bb = 0f
            for (i in 0 until TAPS) {
                val c = p[row + (x0 - 1 + i).coerceIn(0, w - 1)]
                val k = wx[i]
                rr += k * ((c shr RED_SHIFT) and MASK)
                gg += k * ((c shr GREEN_SHIFT) and MASK)
                bb += k * (c and MASK)
            }
            r += wy[j] * rr
            g += wy[j] * gg
            b += wy[j] * bb
        }
        return ALPHA or (level(r) shl RED_SHIFT) or (level(g) shl GREEN_SHIFT) or level(b)
    }

    private fun level(v: Float): Int = (v + HALF).toInt().coerceIn(0, MAX_LEVEL.toInt())

    /** Wagi Catmull–Rom dla ułamka [t] ∈ [0,1): suma = 1. */
    @Suppress("MagicNumber") // współczynniki wielomianu Catmulla–Roma
    internal fun weights(t: Float, out: FloatArray) {
        val t2 = t * t
        val t3 = t2 * t
        out[0] = -0.5f * t3 + t2 - 0.5f * t
        out[1] = 1.5f * t3 - 2.5f * t2 + 1f
        out[2] = -1.5f * t3 + 2f * t2 + 0.5f * t
        out[3] = 0.5f * t3 - 0.5f * t2
    }

    private const val TAPS = 4
    private const val MATRIX_SIZE = 9
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
