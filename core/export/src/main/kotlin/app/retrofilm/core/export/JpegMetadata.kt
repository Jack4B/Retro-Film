package app.retrofilm.core.export

import java.io.ByteArrayOutputStream

/** Operacje na segmentach JPEG (czysty Kotlin, bez Androida). */
@Suppress("MagicNumber", "LoopWithTooManyJumpStatements")
object JpegMetadata {
    private const val MARKER = 0xFF
    private const val SOI = 0xD8
    private const val SOS = 0xDA
    private const val EOI = 0xD9
    private const val APP0 = 0xE0
    private const val APP2 = 0xE2
    private const val APP14 = 0xEE
    private const val APP15 = 0xEF
    private const val COM = 0xFE
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    private const val BYTE = 0xFF
    private const val SHIFT = 8

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

    private fun allowed(a: ByteArray, marker: Int, start: Int, end: Int): Boolean = when (marker) {
        APP0 -> true
        APP2 -> startsWith(a, start, end, ICC)
        APP14 -> startsWith(a, start, end, ADOBE)
        else -> false
    }

    private fun startsWith(a: ByteArray, start: Int, end: Int, p: ByteArray): Boolean =
        start + p.size <= minOf(end, a.size) && p.indices.all { a[start + it] == p[it] }

    /** Pary (marker, początek danych segmentu) aż do SOS. */
    private fun segments(jpeg: ByteArray): List<Pair<Int, Int>> {
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

    private fun u(a: ByteArray, i: Int): Int = a[i].toInt() and BYTE

    private val EXIF = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
    private val ICC = "ICC_PROFILE".toByteArray(Charsets.US_ASCII) + 0
    private val ADOBE = "Adobe".toByteArray(Charsets.US_ASCII)
}
