package app.retrofilm.core.export

import java.io.ByteArrayOutputStream

/** Odczyt parametrów zapisanego JPEG-a (próbkowanie, ICC, EXIF) – dla testów jakości i weryfikacji. */
object JpegInspector {
    private const val SOF_COMPONENTS = 5
    private const val SOF_FIRST = 6
    private const val SOF_STRIDE = 3
    private const val LEN = 2

    /** Czynniki próbkowania (H<<4|V) składowych z SOF0/SOF1/SOF2 – 0x11 dla wszystkich to 4:4:4. */
    fun samplingFactors(jpeg: ByteArray): List<Int> {
        val sof = JpegMetadata.segments(jpeg).firstOrNull { it.first in JpegMetadata.SOF0..JpegMetadata.SOF2 }
            ?: return emptyList()
        val start = sof.second
        val n = u(jpeg, start + SOF_COMPONENTS)
        return List(n) { u(jpeg, start + SOF_FIRST + it * SOF_STRIDE + 1) }
    }

    /** Profil ICC złożony ze wszystkich segmentów APP2 `ICC_PROFILE` (w kolejności numerów). */
    fun icc(jpeg: ByteArray): ByteArray? {
        val tag = JpegMetadata.ICC
        val parts = JpegMetadata.segments(jpeg).filter { (m, s) ->
            m == JpegMetadata.APP2 && startsWith(jpeg, s, jpeg.size, tag)
        }
        if (parts.isEmpty()) return null
        val out = ByteArrayOutputStream()
        parts.sortedBy { (_, s) -> u(jpeg, s + tag.size) }.forEach { (_, s) ->
            out.write(jpeg, s + tag.size + LEN, length(jpeg, s) - LEN - tag.size - LEN)
        }
        return out.toByteArray()
    }

    /** Blok TIFF z segmentu EXIF (bez nagłówka `Exif\0\0`) albo null. */
    fun exifTiff(jpeg: ByteArray): ByteArray? {
        val tag = JpegMetadata.EXIF
        val (_, s) = JpegMetadata.segments(jpeg).firstOrNull { (m, s) ->
            m == JpegMetadata.APP0 + 1 && startsWith(jpeg, s, jpeg.size, tag)
        } ?: return null
        return jpeg.copyOfRange(s + tag.size, s + length(jpeg, s) - LEN)
    }

    private fun length(jpeg: ByteArray, s: Int) = (u(jpeg, s - LEN) shl JpegMetadata.SHIFT) or u(jpeg, s - 1)
}
