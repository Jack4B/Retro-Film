package app.retrofilm.core.export

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * PNG 16 bitów na kanał (RGB, bez alfy), bez zależności od platformy: wiersze z filtrem Up (dobry kompromis
 * rozmiar/czas dla gładkich zdjęć), zlib w strumieniu – pamięć to dwa wiersze, nie cały obraz. [icc] trafia do
 * iCCP (np. Display P3); bez niego zapisujemy chunk sRGB. Wartości kanałów 0..65535 w kodowaniu przestrzeni wyjściowej.
 */
object Png16Writer {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.b, 'N'.b, 'G'.b, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val BIT_DEPTH = 16
    private const val COLOR_RGB = 2
    private const val FILTER_UP = 2
    private const val CHANNELS = 3
    private const val BYTES = 2
    private const val SHIFT = 8
    private const val MASK = 0xFF
    private const val MAX16 = 0xFFFF

    /** [row] wypełnia `w * 3` wartości kanałów dla wiersza `y` (R, G, B; 0..65535). */
    fun encode(
        w: Int,
        h: Int,
        icc: ByteArray?,
        iccName: String = "ICC",
        row: (y: Int, out: IntArray) -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunk(out, "IHDR") {
            it.writeInt(w)
            it.writeInt(h)
            it.write(byteArrayOf(BIT_DEPTH.toByte(), COLOR_RGB.toByte(), 0, 0, 0))
        }
        if (icc != null) {
            chunk(out, "iCCP") {
                it.write(iccName.toByteArray(Charsets.ISO_8859_1))
                it.write(byteArrayOf(0, 0))
                it.write(deflate(icc))
            }
        } else {
            chunk(out, "sRGB") { it.write(0) }
        }
        chunk(out, "IDAT") { it.write(idat(w, h, row)) }
        chunk(out, "IEND") {}
        return out.toByteArray()
    }

    private fun idat(w: Int, h: Int, row: (Int, IntArray) -> Unit): ByteArray {
        val buf = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        DeflaterOutputStream(buf, deflater).use { z ->
            val values = IntArray(w * CHANNELS)
            var prev = ByteArray(w * CHANNELS * BYTES)
            var cur = ByteArray(prev.size)
            val line = ByteArray(prev.size + 1)
            line[0] = FILTER_UP.toByte()
            for (y in 0 until h) {
                row(y, values)
                for (i in values.indices) {
                    val v = values[i].coerceIn(0, MAX16)
                    cur[BYTES * i] = (v shr SHIFT).toByte()
                    cur[BYTES * i + 1] = (v and MASK).toByte()
                }
                for (i in cur.indices) line[i + 1] = (cur[i] - prev[i]).toByte()
                z.write(line)
                val t = prev
                prev = cur
                cur = t
            }
        }
        deflater.end()
        return buf.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val buf = ByteArrayOutputStream()
        DeflaterOutputStream(buf).use { it.write(data) }
        return buf.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, body: (DataOutputStream) -> Unit) {
        val data = ByteArrayOutputStream().also { DataOutputStream(it).use(body) }.toByteArray()
        val t = type.toByteArray(Charsets.US_ASCII)
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(t)
            write(data)
            writeInt(
                CRC32().apply {
                    update(t)
                    update(data)
                }.value.toInt()
            )
        }
    }

    private val Char.b get() = code.toByte()
}
