package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.pow

/**
 * Korekty obrazu po nałożeniu filmu: kontrast, nasycenie i temperatura w zakresie −1..1,
 * ekspozycja w EV (−[EXPOSURE_MAX_EV]..+[EXPOSURE_MAX_EV]).
 * Wartości neutralne ([None]) omijają ten krok, więc wynik filmu (golden testy silnika) się nie zmienia.
 * Liczone w kodowaniu sRGB (nie w świetle liniowym) – prosty, powtarzalny model; ten sam kod w podglądzie i w zapisie.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f
) {
    val isNeutral: Boolean get() = this == None

    companion object {
        val None = Adjustments()
        const val EXPOSURE_MAX_EV = 2f
    }
}

/** Czysta matematyka macierzy 4×5 (konwencja `ColorMatrix`: wiersze R, G, B, A; przesunięcia w skali 0..255). */
object AdjustmentMatrix {
    private const val COLS = 5
    private const val ROWS = 4
    private const val MID_GRAY = 127.5f
    private const val TEMPERATURE_GAIN = 0.15f
    private const val LUM_R = 0.213f
    private const val LUM_G = 0.715f
    private const val LUM_B = 0.072f

    /** Macierz złożona: najpierw ekspozycja, potem kontrast, nasycenie i temperatura. */
    fun build(adj: Adjustments): FloatArray {
        val exposure = scale(2.0.pow(adj.exposure.toDouble()).toFloat())
        val c = 1f + adj.contrast
        val contrast = scale(c, offset = MID_GRAY * (1f - c))
        val saturation = saturation(1f + adj.saturation)
        val temperature = scale(1f + TEMPERATURE_GAIN * adj.temperature, 1f, 1f - TEMPERATURE_GAIN * adj.temperature)
        return mul(temperature, mul(saturation, mul(contrast, exposure)))
    }

    fun identity(): FloatArray = scale(1f)

    private fun scale(r: Float, g: Float = r, b: Float = r, offset: Float = 0f): FloatArray = floatArrayOf(
        r, 0f, 0f, 0f, offset,
        0f, g, 0f, 0f, offset,
        0f, 0f, b, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )

    private fun saturation(s: Float): FloatArray {
        val inv = 1f - s
        return floatArrayOf(
            LUM_R * inv + s, LUM_G * inv, LUM_B * inv, 0f, 0f,
            LUM_R * inv, LUM_G * inv + s, LUM_B * inv, 0f, 0f,
            LUM_R * inv, LUM_G * inv, LUM_B * inv + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /** `a · b`: działa tak, jakby najpierw zadziałała [b], potem [a]. */
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(ROWS * COLS)
        for (i in 0 until ROWS) {
            for (j in 0 until COLS) {
                var sum = 0f
                for (k in 0 until ROWS) sum += a[i * COLS + k] * b[k * COLS + j]
                if (j == COLS - 1) sum += a[i * COLS + COLS - 1]
                out[i * COLS + j] = sum
            }
        }
        return out
    }
}

/** Nakłada [Adjustments] na bitmapę (nowa bitmapa ARGB_8888, źródło bez zmian). */
object AdjustmentsApplier {
    fun apply(src: Bitmap, adj: Adjustments): Bitmap {
        if (adj.isNeutral) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(AdjustmentMatrix.build(adj)))
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
