package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Gainmap
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import app.retrofilm.core.export.IccProfiles
import app.retrofilm.core.export.Png16Writer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

/** Format pliku wyjściowego. JPEG = własny enkoder (4:4:4, ICC, EXIF); pozostałe – ścieżki z [FileEncoders]. */
enum class FileType(val mime: String, val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png16("image/png", "png"),
    Heic("image/heic", "heic")
}

/**
 * PNG 16 bitów: kanały z ostatniego etapu potoku float – 8 bitów bitmapy plus 2 bity ułamka ([Frac]), czyli
 * ~10 bitów rzeczywistej precyzji (bez ditheringu), zapisane jako 0..65535. Bez [frac] (ramka, zmiana rozmiaru)
 * zostaje 8 bitów rozciągniętych do 16.
 */
object Png16Export {
    private const val MAX8 = 255f
    private const val MAX16 = 65535f
    private const val MASK = 0xFF
    private const val R = 16
    private const val G = 8
    private const val BITS = 2
    private const val CODE = 3

    fun encode(bitmap: Bitmap, frac: ByteArray?, space: OutputSpace): ByteArray {
        val w = bitmap.width
        val p3 = space == OutputSpace.DisplayP3
        val icc = if (p3) IccProfiles.displayP3() else null
        val px = IntArray(w)
        return Png16Writer.encode(w, bitmap.height, icc, "Display P3") { y, out ->
            bitmap.getPixels(px, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = px[x]
                val f = frac?.get(y * w + x)?.toInt() ?: -1
                out[CH * x] = to16((c shr R) and MASK, f, 2 * BITS)
                out[CH * x + 1] = to16((c shr G) and MASK, f, BITS)
                out[CH * x + 2] = to16(c and MASK, f, 0)
            }
        }
    }

    private const val CH = 3

    private fun to16(v8: Int, f: Int, shift: Int): Int {
        val v = if (f < 0) v8.toFloat() else v8 + Frac.decode((f shr shift) and CODE)
        return (v.coerceIn(0f, MAX8) / MAX8 * MAX16).roundToInt()
    }
}

/** HEIC 8-bit przez HeifWriter (sprzętowy/programowy koder HEVC). Bez kodera – opcja ukryta w UI. */
object HeicExport {
    val isAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any {
            it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val file = File.createTempFile("export", ".heic")
        try {
            HeifWriter.Builder(file.path, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality)
                .build()
                .use {
                    it.start()
                    it.addBitmap(bitmap)
                    it.stop(TIMEOUT_MS)
                }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private const val TIMEOUT_MS = 30_000L
}

/**
 * Ultra HDR (API 34+): gain mapa pliku źródłowego przechodzi tę samą orientację EXIF i geometrię co obraz, jest
 * skalowana do wyniku i dołączana do bitmapy; [Bitmap.compress] zapisuje JPEG/R (zawsze 4:2:0, bez ramki).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object UltraHdrExport {
    private const val SAMPLE = 8

    /** Gain mapa źródła po orientacji EXIF albo null (brak / plik SDR). Obraz bazowy dekodowany mocno pomniejszony. */
    fun gainmapOf(open: () -> InputStream): Gainmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = SAMPLE }
        val base = open().use { BitmapFactory.decodeStream(it, null, opts) }
        val gain = base?.gainmap
        base?.recycle()
        if (gain == null) return null
        val oriented = PhotoLoader.applyOrientation(gain.gainmapContents, PhotoLoader.readOrientation(open))
        return copy(gain, oriented)
    }

    /** JPEG/R: [out] (wynik potoku) + [gain] po [geometry], w rozdzielczości 1/4 wyniku (jak typowy JPEG/R). */
    fun encode(out: Bitmap, gain: Gainmap, geometry: Geometry, quality: Int): ByteArray {
        val cropped = GeometryApplier.apply(gain.gainmapContents, geometry)
        val target = Bitmap.createScaledBitmap(
            cropped,
            (out.width / SAMPLE_RATIO).roundToInt().coerceAtLeast(1),
            (out.height / SAMPLE_RATIO).roundToInt().coerceAtLeast(1),
            true
        )
        val withGain = out.copy(Bitmap.Config.ARGB_8888, true)
        withGain.gainmap = copy(gain, target)
        val bytes = ByteArrayOutputStream().also { withGain.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        withGain.recycle()
        return bytes.toByteArray()
    }

    private const val SAMPLE_RATIO = 4f

    private fun copy(src: Gainmap, contents: Bitmap) = Gainmap(contents).apply {
        val min = src.ratioMin
        val max = src.ratioMax
        val gamma = src.gamma
        val es = src.epsilonSdr
        val eh = src.epsilonHdr
        setRatioMin(min[0], min[1], min[2])
        setRatioMax(max[0], max[1], max[2])
        setGamma(gamma[0], gamma[1], gamma[2])
        setEpsilonSdr(es[0], es[1], es[2])
        setEpsilonHdr(eh[0], eh[1], eh[2])
        displayRatioForFullHdr = src.displayRatioForFullHdr
        minDisplayRatioForHdrTransition = src.minDisplayRatioForHdrTransition
    }
}
