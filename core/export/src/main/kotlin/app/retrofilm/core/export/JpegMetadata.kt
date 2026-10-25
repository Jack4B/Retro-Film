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
    private const val APP15 = 0xEF
    private const val COM = 0xFE
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    private const val BYTE = 0xFF
    private const val SHIFT = 8

    /**
     * Usuwa wszystkie segmenty APP1..APP15 (EXIF, GPS, XMP, ICC, IPTC…) i komentarze COM.
     * Zostawia APP0 (JFIF) oraz dane obrazu bez zmian.
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
            val drop = (marker in (APP0 + 1)..APP15) || marker == COM
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

    /** Czy plik zawiera jakikolwiek segment APP1..APP15 lub COM. */
    fun hasMetadata(jpeg: ByteArray): Boolean =
        segments(jpeg).any { (marker, _) -> marker in (APP0 + 1)..APP15 || marker == COM }

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
}
