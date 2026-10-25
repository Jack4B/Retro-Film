package app.retrofilm.core.engine.pack

import app.retrofilm.core.engine.Lut
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.Inflater

/**
 * Dekoder HaldCLUT PNG (8 bit/kanał, RGB lub RGBA, bez przeplotu) do siatki LUT [Lut].
 *
 * Poziom L: obraz L^3 x L^3, siatka L^2. Kolejność Hald: piksele wierszami, `r` najszybciej,
 * potem `g`, na końcu `b` – identyczna jak w buforze [Lut], więc piksele kopiujemy liniowo.
 * Czysty JVM (bez BitmapFactory), żeby nie tracić precyzji przez premultiplied alpha / profile kolorów.
 */
object HaldClutDecoder {
    const val FORMAT_PNG_HALD = "png-hald"
    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val COLOR_RGB = 2
    private const val COLOR_RGBA = 6
    private const val BIT_DEPTH_8 = 8
    private const val BYTE_MASK = 0xFF
    private const val CHUNK_TYPE_LENGTH = 4
    private const val RGB_CHANNELS = 3
    private const val RGBA_CHANNELS = 4
    private const val FILTER_AVERAGE = 3
    private const val FILTER_PAETH = 4

    fun decode(input: InputStream, expectedSize: Int? = null): Lut {
        val png = readPng(input)
        val size = gridSizeFor(png.width, png.height)
        require(expectedSize == null || expectedSize == size) { "Hald daje siatkę $size, oczekiwano $expectedSize" }
        val rgb = unfilter(png)
        return Lut(size, rgb)
    }

    /** Rozmiar siatki dla obrazu Hald: szerokość = L^3, siatka = L^2. */
    fun gridSizeFor(width: Int, height: Int): Int {
        require(width == height) { "Hald musi być kwadratem: ${width}x$height" }
        val level = Math.round(Math.cbrt(width.toDouble())).toInt()
        require(level * level * level == width) { "Szerokość $width nie jest sześcianem poziomu Hald" }
        return level * level
    }

    private class Png(val width: Int, val height: Int, val channels: Int, val idat: ByteArray)

    private fun readPng(input: InputStream): Png {
        val data = DataInputStream(input.buffered())
        val sig = ByteArray(PNG_SIGNATURE.size)
        data.readFully(sig)
        require(sig.contentEquals(PNG_SIGNATURE)) { "To nie jest PNG" }
        var width = 0
        var height = 0
        var channels = 0
        val idat = ByteArrayOutputStream()
        while (true) {
            val len = data.readInt()
            val type = ByteArray(CHUNK_TYPE_LENGTH).also { data.readFully(it) }.toString(Charsets.US_ASCII)
            val body = ByteArray(len).also { data.readFully(it) }
            data.readInt() // CRC – integralność pliku zapewnia SHA-256 z katalogu
            when (type) {
                "IHDR" -> {
                    val h = DataInputStream(body.inputStream())
                    width = h.readInt()
                    height = h.readInt()
                    val depth = h.readUnsignedByte()
                    val color = h.readUnsignedByte()
                    h.readUnsignedByte()
                    h.readUnsignedByte()
                    val interlace = h.readUnsignedByte()
                    require(depth == BIT_DEPTH_8) { "Obsługiwane tylko 8 bit/kanał, jest $depth" }
                    require(interlace == 0) { "PNG z przeplotem nieobsługiwany" }
                    channels = when (color) {
                        COLOR_RGB -> RGB_CHANNELS
                        COLOR_RGBA -> RGBA_CHANNELS
                        else -> throw IllegalArgumentException("Nieobsługiwany typ koloru PNG: $color")
                    }
                }

                "IDAT" -> idat.write(body)

                "IEND" -> break
            }
        }
        require(width > 0 && channels > 0) { "Brak IHDR" }
        return Png(width, height, channels, idat.toByteArray())
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun unfilter(png: Png): ByteArray {
        val stride = png.width * png.channels
        val raw = ByteArray((stride + 1) * png.height)
        val inflater = Inflater()
        try {
            inflater.setInput(png.idat)
            var off = 0
            while (off < raw.size) {
                val n = inflater.inflate(raw, off, raw.size - off)
                require(n > 0 || !(inflater.finished() || inflater.needsInput())) { "Ucięte dane PNG" }
                off += n
            }
        } finally {
            inflater.end()
        }
        val out = ByteArray(png.width * png.height * Lut.CHANNELS)
        var prev = ByteArray(stride)
        var cur = ByteArray(stride)
        val bpp = png.channels
        var o = 0
        for (y in 0 until png.height) {
            val base = y * (stride + 1)
            val filter = raw[base].toInt()
            for (x in 0 until stride) {
                val v = raw[base + 1 + x].toInt() and BYTE_MASK
                val a = if (x >= bpp) cur[x - bpp].toInt() and BYTE_MASK else 0
                val b = prev[x].toInt() and BYTE_MASK
                val c = if (x >= bpp) prev[x - bpp].toInt() and BYTE_MASK else 0
                val pred = when (filter) {
                    0 -> 0
                    1 -> a
                    2 -> b
                    FILTER_AVERAGE -> (a + b) ushr 1
                    FILTER_PAETH -> paeth(a, b, c)
                    else -> throw IllegalArgumentException("Zły filtr PNG: $filter")
                }
                cur[x] = (v + pred).toByte()
            }
            var x = 0
            while (x < stride) {
                out[o++] = cur[x]
                out[o++] = cur[x + 1]
                out[o++] = cur[x + 2]
                x += bpp
            }
            val t = prev
            prev = cur
            cur = t
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = Math.abs(p - a)
        val pb = Math.abs(p - b)
        val pc = Math.abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }
}
