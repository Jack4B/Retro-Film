package app.retrofilm.core.export

import android.graphics.Bitmap
import android.graphics.ColorSpace
import app.retrofilm.core.frame.SignatureFitStatus
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Podpróbkowanie chromy w pliku JPEG. */
enum class ChromaSubsampling { Full444, Half420 }

/**
 * Ustawienia kodowania. [exif] to blok TIFF z [ExifWriter] (biała lista, bez GPS) albo null = bez EXIF.
 * Profil ICC jest zawsze dołączany i wynika z przestrzeni barw bitmapy (sRGB albo Display P3); [wideGamut] = piksele
 * są wartościami Display P3 w bitmapie z etykietą sRGB (eksport P3) – plik dostaje profil P3.
 */
data class JpegOptions(
    val quality: Int = JpegExporter.DEFAULT_QUALITY,
    val subsampling: ChromaSubsampling = ChromaSubsampling.Full444,
    val exif: ByteArray? = null,
    val wideGamut: Boolean = false
) {
    override fun equals(other: Any?): Boolean = other is JpegOptions && other.quality == quality &&
        other.subsampling == subsampling && other.exif.contentEquals(exif) && other.wideGamut == wideGamut

    override fun hashCode(): Int =
        ((quality * 31 + subsampling.hashCode()) * 31 + exif.contentHashCode()) * 31 + wideGamut.hashCode()
}

/**
 * Eksport JPEG: obraz kodowany od nowa z [Bitmap] (metadane źródła nigdy nie są kopiowane surowo). Domyślnie
 * własny koder 4:4:4 ([JpegEncoder]); 4:2:0 przez `Bitmap.compress` z usunięciem jego segmentów. W obu
 * przypadkach plik dostaje profil ICC i – jeśli podano – EXIF z białej listy.
 */
object JpegExporter {
    const val DEFAULT_QUALITY = 95
    private const val MAX_QUALITY = 100

    /**
     * Koduje JPEG. [signatureFit] to wynik `SignatureFitCheck` dla bieżącej ramki/podpisu;
     * przy [SignatureFitStatus.TooLong] eksport jest blokowany ([SignatureTooLongException]).
     */
    fun encode(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        signatureFit: SignatureFitStatus = SignatureFitStatus.None
    ): ByteArray = encode(bitmap, JpegOptions(quality), signatureFit)

    fun encode(bitmap: Bitmap, options: JpegOptions, signatureFit: SignatureFitStatus): ByteArray {
        if (signatureFit == SignatureFitStatus.TooLong) throw SignatureTooLongException()
        val q = options.quality.coerceIn(1, MAX_QUALITY)
        val icc = if (options.wideGamut) IccProfiles.displayP3() else iccFor(bitmap)
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return when (options.subsampling) {
            ChromaSubsampling.Full444 -> JpegEncoder.encode(src.width, src.height, q, icc, options.exif) { y0, n, out ->
                src.getPixels(out, 0, src.width, 0, y0, src.width, n)
            }

            ChromaSubsampling.Half420 -> {
                val buffer = ByteArrayOutputStream()
                check(src.compress(Bitmap.CompressFormat.JPEG, q, buffer)) { "Kodowanie JPEG nie powiodło się" }
                JpegMetadata.withMetadata(JpegMetadata.strip(buffer.toByteArray()), icc, options.exif)
            }
        }
    }

    /** sRGB albo Display P3 – według przestrzeni barw pikseli (getPixels oddaje surowe wartości w tej przestrzeni). */
    fun iccFor(bitmap: Bitmap): ByteArray = if (bitmap.colorSpace ==
        ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
    ) {
        IccProfiles.displayP3()
    } else {
        IccProfiles.srgb()
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
