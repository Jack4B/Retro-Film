package app.retrofilm.pipeline

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutEngine
import app.retrofilm.core.export.JpegExporter
import java.io.IOException

/** Pełna ścieżka: zdjęcie (już z orientacją) → film (LUT) → ramka + podpis → JPEG bez metadanych. */
object ExportPipeline {
    const val ALBUM = "RetroFilm"
    const val MIME = "image/jpeg"

    /** Nakłada LUT (null = oryginał) i ramkę; wynik zachowuje przestrzeń barw [photo]. */
    suspend fun render(photo: Bitmap, lut: Lut?, engine: LutEngine, frame: FrameOptions, typeface: Typeface): Bitmap {
        val filmed = if (lut == null) photo else engine.apply(photo, lut, 1f)
        return FrameRenderer.render(filmed, frame, typeface, colorSpaceSource = photo)
    }

    /**
     * Koduje JPEG. Rzuca `SignatureTooLongException`, gdy podpis nie mieści się w ramce –
     * wywołujący pokazuje komunikat zamiast eksportować.
     */
    fun encode(photo: Bitmap, rendered: Bitmap, frame: FrameOptions, typeface: Typeface): ByteArray =
        JpegExporter.encode(
            rendered,
            JpegExporter.DEFAULT_QUALITY,
            FrameRenderer.status(frame, photo.width, photo.height, typeface)
        )

    fun fileName(timeMillis: Long): String = "RetroFilm_$timeMillis.jpg"

    /** Zapis przez MediaStore do Pictures/RetroFilm (API 29+, bez uprawnień do pamięci). */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToMediaStore(resolver: ContentResolver, jpeg: ByteArray, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore odrzucił zapis")
        try {
            saveToUri(resolver, jpeg, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** API 26–28: zapis do dokumentu wybranego przez użytkownika (SAF), też bez uprawnień. */
    fun saveToUri(resolver: ContentResolver, jpeg: ByteArray, uri: Uri) {
        resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: throw IOException("Brak strumienia zapisu")
    }
}
