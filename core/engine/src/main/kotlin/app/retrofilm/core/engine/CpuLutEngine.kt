package app.retrofilm.core.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Backend CPU – zawsze dostępny, referencja dla testów GPU. */
class CpuLutEngine(private val interpolation: Interpolation = Interpolation.TRILINEAR) : LutEngine {
    override val name: String = if (interpolation == Interpolation.TRILINEAR) "cpu" else "cpu-tetra"

    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = withContext(Dispatchers.Default) {
        val pixels = BitmapPixels.read(src)
        BitmapPixels.write(CpuLut.applyToPixels(pixels, lut, intensity, interpolation), src.width, src.height)
    }
}

internal object BitmapPixels {
    fun read(src: Bitmap): IntArray {
        val argb = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(argb.width * argb.height)
        argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
        return pixels
    }

    fun write(pixels: IntArray, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
