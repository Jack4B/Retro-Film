package app.retrofilm.core.export

import java.io.ByteArrayOutputStream

/** Operacje na segmentach JPEG (czysty Kotlin, bez Androida). */
@Suppress("MagicNumber", "LoopWithTooManyJumpStatements")
object JpegMetadata {
    internal const val MARKER = 0xFF
    private const val SOI = 0xD8
    internal const val SOS = 0xDA
    internal const val EOI = 0xD9
    internal const val APP0 = 0xE0
    internal const val APP2 = 0xE2
    private const val APP14 = 0xEE
    private const val APP15 = 0xEF
    private const val COM = 0xFE
    internal const val SOF0 = 0xC0
    private const val MAX_SEGMENT = 65533
    internal const val SOF2 = 0xC2
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    internal const val BYTE = 0xFF
    internal const val SHIFT = 8

    /**
     * Biała lista: z segmentów APPn zostają tylko APP0 (JFIF), APP2 `ICC_PROFILE` (profil koloru,
     * np. Display P3) i APP14 `Adobe`. Reszta APPn (EXIF, GPS, XMP, IPTC, MPF…) i COM są usuwane.
     */
    fun strip(jpeg: ByteArray): ByteArray {
        require(jpeg.size >= 2 && u(jpeg, 0) == MARKER && u(jpeg, 1) == SOI) { "To nie jest JPEG" }
        val out = ByteArrayOutputStream(jpeg.size)
        out.write(jpeg, 0, 2)
        var i = 2
        while (i + 1 < jpeg.size) {
            require(u(jpeg, i) == MARKER) { "Uszkodzony JPEG na pozycji $i" }
            val marker = u(jpeg, i + 1)
            if (marker == MARKER) { // wypełnienie
                i++
                continue
            }
            if (marker == SOS || marker == EOI) {
                out.write(jpeg, i, jpeg.size - i)
                return out.toByteArray()
            }
            if (marker == TEM || marker in RST0..RST7) {
                out.write(jpeg, i, 2)
                i += 2
                continue
            }
            val len = (u(jpeg, i + 2) shl SHIFT) or u(jpeg, i + 3)
            val end = i + 2 + len
            require(end <= jpeg.size) { "Segment poza końcem pliku" }
            val drop = ((marker in APP0..APP15) && !allowed(jpeg, marker, i + 4, end)) || marker == COM
            if (!drop) out.write(jpeg, i, end - i)
            i = end
        }
        return out.toByteArray()
    }

    /** Czy plik zawiera segment EXIF (APP1 z nagłówkiem `Exif\0\0`) – a więc też GPS IFD. */
    fun hasExif(jpeg: ByteArray): Boolean = segments(jpeg).any { (marker, start) ->
        marker == APP0 + 1 && start + EXIF.size <= jpeg.size &&
            EXIF.indices.all { jpeg[start + it] == EXIF[it] }
    }

    /** Czy plik zawiera segment APPn spoza białej listy lub COM. */
    fun hasMetadata(jpeg: ByteArray): Boolean = segments(jpeg).any { (marker, start) ->
        (marker in APP0..APP15 && !allowed(jpeg, marker, start, jpeg.size)) || marker == COM
    }

    /** Czy plik zawiera profil ICC (APP2 z nagłówkiem `ICC_PROFILE\0`). */
    fun hasIcc(jpeg: ByteArray): Boolean = segments(jpeg).any { (marker, start) ->
        marker == APP2 && startsWith(jpeg, start, jpeg.size, ICC)
    }

    /** Wstawia po APP0 (albo po SOI) segment EXIF i profil ICC; plik wejściowy nie powinien mieć własnych. */
    fun withMetadata(jpeg: ByteArray, icc: ByteArray?, exifTiff: ByteArray?): ByteArray {
        val at = segments(jpeg).firstOrNull()?.takeIf { it.first == APP0 }?.let { (_, s) ->
            s - 2 + ((u(jpeg, s - 2) shl SHIFT) or u(jpeg, s - 1))
        } ?: 2
        val out = ByteArrayOutputStream(jpeg.size + (icc?.size ?: 0) + (exifTiff?.size ?: 0) + 64)
        out.write(jpeg, 0, at)
        if (exifTiff != null) segment(out, APP0 + 1, EXIF + exifTiff)
        if (icc != null) {
            val chunk = MAX_SEGMENT - ICC.size - 2
            val parts = (icc.size + chunk - 1) / chunk
            for (i in 0 until parts) {
                val body = ICC + byteArrayOf((i + 1).toByte(), parts.toByte()) +
                    icc.copyOfRange(i * chunk, minOf(icc.size, (i + 1) * chunk))
                segment(out, APP2, body)
            }
        }
        out.write(jpeg, at, jpeg.size - at)
        return out.toByteArray()
    }

    internal fun segment(out: ByteArrayOutputStream, marker: Int, body: ByteArray) {
        val len = body.size + 2
        out.write(MARKER)
        out.write(marker)
        out.write(len shr SHIFT)
        out.write(len and BYTE)
        out.write(body)
    }

    private fun allowed(a: ByteArray, marker: Int, start: Int, end: Int): Boolean = when (marker) {
        APP0 -> true
        APP2 -> startsWith(a, start, end, ICC)
        APP14 -> startsWith(a, start, end, ADOBE)
        else -> false
    }

    /** Pary (marker, początek danych segmentu) aż do SOS. */
    internal fun segments(jpeg: ByteArray): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        var i = 2
        while (i + 3 < jpeg.size && u(jpeg, i) == MARKER) {
            val marker = u(jpeg, i + 1)
            if (marker == SOS || marker == EOI) break
            if (marker == MARKER) {
                i++
                continue
            }
            val len = (u(jpeg, i + 2) shl SHIFT) or u(jpeg, i + 3)
            list += marker to i + 4
            i += 2 + len
        }
        return list
    }

    internal val EXIF = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
    internal val ICC = "ICC_PROFILE".toByteArray(Charsets.US_ASCII) + 0
    private val ADOBE = "Adobe".toByteArray(Charsets.US_ASCII)
}

internal fun startsWith(a: ByteArray, start: Int, end: Int, p: ByteArray): Boolean =
    start + p.size <= minOf(end, a.size) && p.indices.all { a[start + it] == p[it] }

internal fun u(a: ByteArray, i: Int): Int = a[i].toInt() and JpegMetadata.BYTE
