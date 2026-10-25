package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import java.nio.IntBuffer

/**
 * Wczytanie zdjęcia: dekodowanie z próbkowaniem do [maxSide], potem orientacja EXIF nakładana
 * na piksele ([ExifOrientation]). Przestrzeń barw zdekodowanej bitmapy (np. Display P3 z ICC)
 * jest zachowywana – piksele kopiowane są surowo, bez konwersji.
 */
object PhotoLoader {
    const val DEFAULT_MAX_SIDE = 4096

    /** [open] musi za każdym razem zwracać nowy strumień od początku pliku. */
    fun load(open: () -> InputStream, maxSide: Int = DEFAULT_MAX_SIDE): Bitmap {
        val orientation = readOrientation(open)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Nie można odczytać zdjęcia")
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Nie można zdekodować zdjęcia")
        return applyOrientation(decoded, orientation)
    }

    fun readOrientation(open: () -> InputStream): Int = try {
        open().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifOrientation.NORMAL)
        }
    } catch (_: IOException) {
        ExifOrientation.NORMAL
    }

    /** Największa potęga 2, przy której dłuższy bok nie spada poniżej [maxSide]. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        if (maxOf(width, height) / sample > maxSide) sample *= 2
        return sample
    }

    /** Nowa bitmapa z pikselami ustawionymi wg orientacji; ta sama przestrzeń barw co [src]. */
    fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifOrientation.NORMAL || orientation !in 1..ExifOrientation.ROTATE_270) return src
        val argb = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val raw = IntArray(argb.width * argb.height)
        argb.copyPixelsToBuffer(IntBuffer.wrap(raw))
        val rotated = ExifOrientation.apply(raw, argb.width, argb.height, orientation)
        val (w, h) = ExifOrientation.outputSize(argb.width, argb.height, orientation)
        val colorSpace = argb.colorSpace
        val out = if (colorSpace != null) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, argb.hasAlpha(), colorSpace)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        out.copyPixelsFromBuffer(IntBuffer.wrap(rotated))
        return out
    }
}
