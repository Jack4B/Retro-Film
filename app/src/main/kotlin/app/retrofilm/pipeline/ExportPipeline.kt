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

    /**
     * Konwertuje zdjęcie do sRGB ([ColorPolicy]), nakłada korekty, LUT (null = oryginał), wykończenie i ramkę.
     * Wynik jest zawsze w sRGB, więc `Bitmap.compress` zapisze profil zgodny z pikselami.
     */
    @Suppress("LongParameterList") // intensywność dochodzi do istniejących parametrów; grupowanie później
    suspend fun render(
        photo: Bitmap,
        lut: Lut?,
        engine: LutEngine,
        frame: FrameOptions,
        typeface: Typeface,
        intensity: Float = 1f,
        adjustments: Adjustments = Adjustments.None
    ): Bitmap {
        val srgb = ColorPolicy.toSrgb(photo)
        // kolejność jak w programach do obróbki RAW (D15): najpierw korekty, potem film (look), na końcu wykończenie
        val corrected = AdjustmentsApplier.apply(srgb, adjustments.corrections(), deband = lut != null)
        val filmed = if (lut == null) corrected else engine.apply(corrected, lut, intensity)
        val finished = AdjustmentsApplier.apply(filmed, adjustments.finishing())
        return FrameRenderer.render(finished, frame, typeface, colorSpaceSource = srgb)
    }

    /**
     * Koduje JPEG. Rzuca `SignatureTooLongException`, gdy podpis nie mieści się w ramce –
     * wywołujący pokazuje komunikat zamiast eksportować.
     */
    fun encode(
        photo: Bitmap,
        rendered: Bitmap,
        frame: FrameOptions,
        typeface: Typeface,
        quality: Int = JpegExporter.DEFAULT_QUALITY
    ): ByteArray =
        JpegExporter.encode(rendered, quality, FrameRenderer.status(frame, photo.width, photo.height, typeface))

    /**
     * Zmniejsza wynik do [maxSide] px dłuższego boku (0 = oryginalny rozmiar) filtrem Lanczosa ([Resampler], bez
     * aliasingu), a potem łagodnie wyostrza wyjściowo, żeby oddać ostrość utraconą przy zmniejszaniu. Powiększać nie
     * wolno – większy wymiar niż źródło zostaje jak jest.
     */
    fun resize(rendered: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(rendered.width, rendered.height)
        if (maxSide <= 0 || long <= maxSide) return rendered
        val k = maxSide.toFloat() / long
        val w = (rendered.width * k).toInt().coerceAtLeast(1)
        val h = (rendered.height * k).toInt().coerceAtLeast(1)
        val src = IntArray(rendered.width * rendered.height)
        rendered.getPixels(src, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        val px = Resampler.resize(src, rendered.width, rendered.height, w, h)
        DetailFilter.apply(px, w, h, OUTPUT_SHARPEN, 0f, false)
        val cs = rendered.colorSpace
        val result = if (cs != null) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        result.setPixels(px, 0, w, 0, 0, w, h)
        return result
    }

    private val OUTPUT_SHARPEN = SharpenParams(amount = 0.3f, radius = 0.8f, detail = 0.2f, masking = 0.3f)

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
