package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Half
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DNG/RAW przez dekoder platformy (ImageDecoder, Skia RAW/dng_sdk): demozaikowanie, balans bieli z pliku
 * i bazowa krzywa DNG – płaski, neutralny punkt startu dla filmu. Dekodowanie od razu do [maxSide]
 * (setTargetSize), orientację nakłada sam dekoder, więc nie obracamy drugi raz.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal object RawLoader {
    fun open(open: () -> InputStream, maxSide: Int): PhotoLoader.Loaded {
        val bytes = open().use { it.readBytes() }
        var srcW = 0
        var srcH = 0
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                srcW = info.size.width
                srcH = info.size.height
                val k = maxSide.toFloat() / maxOf(srcW, srcH)
                if (k < 1f) decoder.setTargetSize(maxOf(1, (srcW * k).toInt()), maxOf(1, (srcH * k).toInt()))
            }
        } catch (e: ImageDecoder.DecodeException) {
            throw IOException("Nie można zdekodować RAW", e)
        }
        return PhotoLoader.Loaded(bitmap, srcW, srcH)
    }

    /**
     * J: analiza zapasu HDR z DNG – dekod pomniejszony do [side] w RGBA_F16 i liniowym rozszerzonym sRGB (wartości
     * > 1 zachowane, BaselineExposure nakłada dng_sdk), luminancja Rec.709 → [HdrAnalysis.rawHeadroom].
     */
    fun analyseHeadroom(bytes: ByteArray, side: Int = ANALYSIS_SIDE): Float {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB))
            val k = side.toFloat() / maxOf(info.size.width, info.size.height)
            if (k < 1f) {
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * k).toInt()),
                    maxOf(1, (info.size.height * k).toInt())
                )
            }
        }
        val luma = try {
            lumaOf(bitmap)
        } finally {
            bitmap.recycle()
        }
        return HdrAnalysis.rawHeadroom(luma)
    }

    private fun lumaOf(bitmap: Bitmap): FloatArray {
        if (bitmap.config != Bitmap.Config.RGBA_F16) return FloatArray(0) // 8 bit: obcięte, brak zapasu
        val buf = ByteBuffer.allocate(bitmap.byteCount).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val sb = buf.asShortBuffer()
        val n = bitmap.width * bitmap.height
        return FloatArray(n) {
            val r = Half.toFloat(sb.get(RGBA * it))
            val g = Half.toFloat(sb.get(RGBA * it + 1))
            val b = Half.toFloat(sb.get(RGBA * it + 2))
            LUMA_R * r + LUMA_G * g + LUMA_B * b
        }
    }

    private const val ANALYSIS_SIDE = 512
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
}

private const val RGBA = 4
