package app.retrofilm.core.export

import android.graphics.Bitmap
import app.retrofilm.core.frame.SignatureFitStatus
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

    /**
     * Koduje JPEG. [signatureFit] to wynik `SignatureFitCheck` dla bieżącej ramki/podpisu;
     * przy [SignatureFitStatus.TooLong] eksport jest blokowany ([SignatureTooLongException]).
     */
    fun encode(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        signatureFit: SignatureFitStatus = SignatureFitStatus.None
    ): ByteArray {
        if (signatureFit == SignatureFitStatus.TooLong) throw SignatureTooLongException()
        val buffer = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, MAX_QUALITY), buffer)) {
            "Kodowanie JPEG nie powiodło się"
        }
        return JpegMetadata.strip(buffer.toByteArray())
    }

    fun export(
        bitmap: Bitmap,
        out: OutputStream,
        quality: Int = DEFAULT_QUALITY,
        signatureFit: SignatureFitStatus = SignatureFitStatus.None
    ) {
        out.write(encode(bitmap, quality, signatureFit))
    }
}

/** Eksport zablokowany: podpis nie mieści się w ramce nawet przy progu czytelności. */
class SignatureTooLongException : IllegalStateException("Podpis za długi dla tej ramki – eksport zablokowany")
