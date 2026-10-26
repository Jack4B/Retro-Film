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
     * Konwertuje zdjęcie do sRGB ([ColorPolicy]), nakłada LUT (null = oryginał) i ramkę.
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
        val filmed = if (lut == null) srgb else engine.apply(srgb, lut, intensity)
        val adjusted = AdjustmentsApplier.apply(filmed, adjustments)
        return FrameRenderer.render(adjusted, frame, typeface, colorSpaceSource = srgb)
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
     * Zmniejsza wynik do [maxSide] px dłuższego boku (0 = oryginalny rozmiar): kolejne połowienia (uśrednianie 2×2,
     * bez aliasingu) i końcowe dopasowanie, a potem łagodne wyostrzanie wyjściowe, które oddaje ostrość utraconą
     * przy zmniejszaniu. Powiększać nie wolno – większy wymiar niż źródło zostaje jak jest.
     */
    fun resize(rendered: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(rendered.width, rendered.height)
        if (maxSide <= 0 || long <= maxSide) return rendered
        var bmp = rendered
        while (maxOf(bmp.width, bmp.height) >= maxSide * 2) {
            bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
        }
        val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * k).toInt().coerceAtLeast(1),
            (bmp.height * k).toInt().coerceAtLeast(1),
            true
        )
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        DetailFilter.apply(px, out.width, out.height, OUTPUT_SHARPEN, 0f, false)
        val result = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(px, 0, result.width, 0, 0, result.width, result.height)
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
