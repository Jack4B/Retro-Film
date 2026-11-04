package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import app.retrofilm.core.export.ExifData
import app.retrofilm.perf.SaveTrace
import app.retrofilm.perf.SaveTrace.Companion.timed
import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt

/**
 * Źródło eksportu w pełnej rozdzielczości pliku (podgląd pracuje na kopii ≤ 4096 px). Gdy pamięć nie pozwala,
 * zdjęcie wczytuje się w największym rozmiarze, który się zmieści, a gdy mimo to zabraknie pamięci w trakcie
 * obróbki ([OutOfMemoryError]) – całość powtarza się od rozmiaru podglądu. W obu przypadkach [Loaded.reduced]
 * mówi UI, że trzeba pokazać komunikat.
 */
object ExportSource {
    /** Ile kopii ARGB zdjęcia żyje naraz w najgorszym miejscu potoku (źródło, kadr, dwa etapy, ramka). */
    internal const val COPIES = 5
    private const val BYTES_PER_PIXEL = 4L

    data class Loaded(val bitmap: Bitmap, val exif: ExifData, val reduced: Boolean)

    /** Dłuższy bok mieszczący się w [budget] bajtach dla [COPIES] kopii; [Int.MAX_VALUE] = pełny rozmiar. */
    fun sideWithin(width: Int, height: Int, budget: Long): Int {
        val allowed = budget / (BYTES_PER_PIXEL * COPIES)
        if (width.toLong() * height <= allowed) return Int.MAX_VALUE
        val long = maxOf(width, height).toDouble()
        val short = minOf(width, height).toDouble()
        return sqrt(allowed * long / short).toInt().coerceAtLeast(1)
    }

    fun availableBytes(): Long {
        val rt = Runtime.getRuntime()
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    }

    fun load(open: () -> InputStream, budget: Long = availableBytes()): Loaded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Nie można odczytać zdjęcia")
        val side = sideWithin(bounds.outWidth, bounds.outHeight, budget)
        val bmp = PhotoLoader.load(open, side)
        val reduced = maxOf(bmp.width, bmp.height) < maxOf(bounds.outWidth, bounds.outHeight)
        return Loaded(bmp, ExifSource.read(open), reduced)
    }

    /** [block] na pełnym rozmiarze; przy braku pamięci jeszcze raz na rozmiarze podglądu (z `reduced = true`). */
    suspend fun <T> withFallback(open: () -> InputStream, trace: SaveTrace? = null, block: suspend (Loaded) -> T): T {
        val full = try {
            trace.timed("decode_full") { load(open) }
        } catch (_: OutOfMemoryError) {
            trace?.event("oom_fallback", "where=decode")
            null
        }
        if (full != null) {
            try {
                return block(full)
            } catch (_: OutOfMemoryError) {
                trace?.event("oom_fallback", "where=render side=${maxOf(full.bitmap.width, full.bitmap.height)}")
                full.bitmap.recycle()
            }
        }
        val small = trace.timed("decode_reduced") { PhotoLoader.load(open, PhotoLoader.DEFAULT_MAX_SIDE) }
        return block(Loaded(small, ExifSource.read(open), reduced = true))
    }
}

/** Odczyt EXIF źródła do białej listy [ExifData] – GPS, MakerNote i numery seryjne nie są nawet czytane. */
object ExifSource {
    fun read(open: () -> InputStream): ExifData = try {
        open().use { from(ExifInterface(it)) }
    } catch (_: IOException) {
        ExifData()
    }

    fun from(e: ExifInterface): ExifData {
        fun s(tag: String) = e.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
        fun d(tag: String) = e.getAttributeDouble(tag, -1.0).takeIf { it > 0 }
        fun i(tag: String) = e.getAttributeInt(tag, -1).takeIf { it > 0 }
        return ExifData(
            make = s(ExifInterface.TAG_MAKE),
            model = s(ExifInterface.TAG_MODEL),
            lensMake = s("LensMake"),
            lensModel = s("LensModel"),
            dateTimeOriginal = s(ExifInterface.TAG_DATETIME_ORIGINAL) ?: s(ExifInterface.TAG_DATETIME),
            offsetTimeOriginal = s("OffsetTimeOriginal"),
            exposureTime = d(ExifInterface.TAG_EXPOSURE_TIME),
            fNumber = d(ExifInterface.TAG_F_NUMBER),
            iso = i(ExifInterface.TAG_ISO_SPEED_RATINGS),
            focalLength = d(ExifInterface.TAG_FOCAL_LENGTH),
            focalLength35 = i(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            artist = s(ExifInterface.TAG_ARTIST),
            copyright = s(ExifInterface.TAG_COPYRIGHT)
        )
    }
}
