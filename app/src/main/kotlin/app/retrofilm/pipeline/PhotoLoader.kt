package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
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

    /** Podgląd nigdy nie potrzebuje więcej (PREVIEW_SIDE_DETAIL); pełna rozdzielczość tylko w eksporcie. */
    const val PREVIEW_MAX_SIDE = 2560

    /** Zdekodowana (pomniejszona) bitmapa i wymiary oryginału po orientacji EXIF. */
    class Loaded(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /** [open] musi za każdym razem zwracać nowy strumień od początku pliku. */
    fun load(open: () -> InputStream, maxSide: Int = DEFAULT_MAX_SIDE): Bitmap = open(open, maxSide).bitmap

    /** Jak [load], ale zwraca też wymiary oryginału (do informacji o eksporcie i dopasowania podpisu). */
    fun open(open: () -> InputStream, maxSide: Int = DEFAULT_MAX_SIDE): Loaded {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isTiffRaw(open)) return RawLoader.open(open, maxSide)
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
        val (sw, sh) = ExifOrientation.outputSize(bounds.outWidth, bounds.outHeight, orientation)
        return Loaded(applyOrientation(decoded, orientation), sw, sh)
    }

    /** DNG i inne RAW-y w kontenerze TIFF (nagłówek „II*\u0000” albo „MM\u0000*”). */
    fun isTiffRaw(open: () -> InputStream): Boolean = try {
        val h = open().use { s -> ByteArray(TIFF_HEADER).also { s.read(it) } }
        h.contentEquals(TIFF_LE) || h.contentEquals(TIFF_BE)
    } catch (_: IOException) {
        false
    }

    private const val TIFF_HEADER = 4
    private val TIFF_LE = byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), '*'.code.toByte(), 0)
    private val TIFF_BE = byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, '*'.code.toByte())

    /** Miniatura osadzona w EXIF (JPEG/DNG) albo null; pokazywana od razu. */
    fun thumbnail(open: () -> InputStream): Bitmap? = try {
        open().use { stream ->
            val exif = ExifInterface(stream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifOrientation.NORMAL)
            exif.thumbnailBitmap?.let { applyOrientation(it, orientation) }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
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
