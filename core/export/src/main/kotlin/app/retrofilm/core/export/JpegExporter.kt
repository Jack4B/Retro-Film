package app.retrofilm.core.export

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Eksport JPEG bez metadanych: obraz jest kodowany od nowa z [Bitmap] (żadne EXIF/GPS źródła
 * nie są kopiowane), a wynik dodatkowo przechodzi przez [JpegMetadata.strip], więc nawet gdyby
 * koder dopisał segment APP1/XMP, nie trafi on do pliku.
 */
object JpegExporter {
    const val DEFAULT_QUALITY = 92
    private const val MAX_QUALITY = 100

    fun encode(bitmap: Bitmap, quality: Int = DEFAULT_QUALITY): ByteArray {
        val buffer = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, MAX_QUALITY), buffer)) {
            "Kodowanie JPEG nie powiodło się"
        }
        return JpegMetadata.strip(buffer.toByteArray())
    }

    fun export(bitmap: Bitmap, out: OutputStream, quality: Int = DEFAULT_QUALITY) {
        out.write(encode(bitmap, quality))
    }
}
