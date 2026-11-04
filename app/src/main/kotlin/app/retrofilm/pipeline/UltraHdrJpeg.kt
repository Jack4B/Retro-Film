package app.retrofilm.pipeline

import java.io.ByteArrayOutputStream

/**
 * Ultra HDR / Motion Photo w JPEG: główny obraz SDR, za nim (MPF, XMP Container) gain mapa i np. wideo.
 * [sdrOnly] zostawia sam obraz bazowy: bez segmentu MPF, bez XMP z gain mapą i bez danych za EOI,
 * więc dekoder nie szuka ani nie dekoduje gain mapy (szybsze otwarcie, czysty SDR w edycji i eksporcie).
 */
object UltraHdrJpeg {
    private const val FF = 0xFF
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    private const val TEM = 0x01
    private const val HEADER = 4
    private const val BYTE = 8
    private val MPF = "MPF\u0000".toByteArray(Charsets.ISO_8859_1)
    private val HDRGM = "hdrgm".toByteArray(Charsets.ISO_8859_1)
    private val XMP = "http://ns.adobe.com/xap/1.0/".toByteArray(Charsets.ISO_8859_1)

    /** Czy JPEG deklaruje gain mapę (XMP hdrgm albo segment MPF z kolejnym obrazem). */
    fun hasGainMap(bytes: ByteArray): Boolean = isJpeg(bytes) &&
        segments(bytes).any { (m, s, e) ->
            (m == APP2 && startsWith(bytes, s, MPF)) ||
                (m == APP1 && contains(bytes, s, e, HDRGM))
        }

    /** Sam obraz bazowy SDR; dla innych plików zwraca [bytes] bez zmian. */
    fun sdrOnly(bytes: ByteArray): ByteArray {
        val header = segments(bytes)
        val sos = header.lastOrNull()?.takeIf { it.first == SOS }?.second?.minus(HEADER) ?: -1
        if (sos < 0 || !hasGainMap(bytes)) return bytes
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 2)
        for ((m, s, e) in header.dropLast(1)) {
            val drop = (m == APP2 && startsWith(bytes, s, MPF)) ||
                (m == APP1 && startsWith(bytes, s, XMP) && contains(bytes, s, e, HDRGM))
            if (!drop) out.write(bytes, s - HEADER, e - s + HEADER)
        }
        val end = endOfImage(bytes, sos)
        out.write(bytes, sos, end - sos)
        return out.toByteArray()
    }

    private fun isJpeg(b: ByteArray) = b.size > HEADER && (b[0].toInt() and FF) == FF && (b[1].toInt() and FF) == SOI

    /** Segmenty nagłówka do SOS włącznie: (marker, początek danych, koniec danych). */
    private fun segments(b: ByteArray): List<Triple<Int, Int, Int>> {
        val list = ArrayList<Triple<Int, Int, Int>>()
        var i = 2
        while (i + HEADER <= b.size && (b[i].toInt() and FF) == FF) {
            val m = b[i + 1].toInt() and FF
            val len = u16(b, i + 2)
            val end = minOf(b.size, i + 2 + len)
            list += Triple(m, i + HEADER, end)
            if (m == SOS) break
            i = end
        }
        return list
    }

    /** Pozycja tuż za EOI głównego obrazu (pierwszy FFD9 w danych skanu; FF00 i RSTn pomijane). */
    private fun endOfImage(b: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < b.size) {
            if ((b[i].toInt() and FF) == FF) {
                val m = b[i + 1].toInt() and FF
                if (m == EOI) return i + 2
                if (hasLength(m)) {
                    // segment między skanami (np. DHT w progresywnym): przeskocz po długości
                    val len = u16(b, i + 2)
                    i += 2 + len
                    continue
                }
            }
            i++
        }
        return b.size
    }

    /** Marker z polem długości (nie FF00, RSTn, TEM ani wypełnienie FF). */
    private fun hasLength(m: Int) = m !in RST0..RST7 && m !in setOf(0, TEM, FF)

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and FF) shl BYTE) or (b[at + 1].toInt() and FF)

    private fun startsWith(b: ByteArray, at: Int, p: ByteArray) =
        at + p.size <= b.size && p.indices.all { b[at + it] == p[it] }

    private fun contains(b: ByteArray, from: Int, to: Int, p: ByteArray): Boolean =
        (from..to - p.size).any { startsWith(b, it, p) }
}
