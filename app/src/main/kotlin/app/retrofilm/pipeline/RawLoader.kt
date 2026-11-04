package app.retrofilm.pipeline

import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

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
}
