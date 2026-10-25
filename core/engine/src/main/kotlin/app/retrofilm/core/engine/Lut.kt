package app.retrofilm.core.engine

/**
 * LUT 3D w formacie binarnym RGB8 lub RGB16 (docs/LUT-BIN.md).
 *
 * Surowe `size^3 * 3 * bytesPerChannel` bajtów bez nagłówka; indeks `r` zmienia się najszybciej,
 * potem `g`, na końcu `b`. RGB16 to uint16 little-endian (0..65535). Rozmiar i format pochodzą
 * z wpisu `catalog.json` (pola `size`, `format`).
 */
class Lut(val size: Int, val data: ByteArray, val bytesPerChannel: Int = 1) {
    init {
        require(size in MIN_SIZE..MAX_SIZE) { "Rozmiar LUT $size poza zakresem $MIN_SIZE..$MAX_SIZE" }
        require(bytesPerChannel == 1 || bytesPerChannel == 2) { "Nieobsługiwane bajty/kanał: $bytesPerChannel" }
        val expected = expectedBytes(size, bytesPerChannel)
        require(data.size == expected) { "LUT $size^3 wymaga $expected bajtów, jest ${data.size}" }
    }

    /** Wartość kanału [channel] (0=R, 1=G, 2=B) w węźle jako 0..255 (zaokrąglona). */
    fun node(r: Int, g: Int, b: Int, channel: Int): Int = Math.round(nodeF(r, g, b, channel))

    /** Wartość kanału w węźle w skali 0..255 z pełną precyzją (RGB16 → ułamki). */
    fun nodeF(r: Int, g: Int, b: Int, channel: Int): Float {
        val i = (((b * size + g) * size + r) * CHANNELS + channel) * bytesPerChannel
        return if (bytesPerChannel == 1) {
            (data[i].toInt() and BYTE_MASK).toFloat()
        } else {
            val v = (data[i].toInt() and BYTE_MASK) or ((data[i + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)
            v / U16_PER_U8
        }
    }

    /** Offset bajtowy pierwszego kanału węzła w [data]. */
    fun offset(r: Int, g: Int, b: Int): Int = ((b * size + g) * size + r) * CHANNELS * bytesPerChannel

    /** Wartość kanału jako 0..255 (dla tekstur RGBA8 na GPU). */
    fun node8(r: Int, g: Int, b: Int, channel: Int): Int = node(r, g, b, channel).coerceIn(0, MAX_BYTE)

    companion object {
        const val DEFAULT_SIZE = 33
        const val CHANNELS = 3
        const val MIN_SIZE = 2
        const val MAX_SIZE = 256
        const val FORMAT_RGB8 = "rgb8"
        const val FORMAT_RGB16 = "rgb16"
        private const val BYTE_MASK = 0xFF
        private const val MAX_BYTE = 255
        private const val BITS_PER_BYTE = 8
        private const val U16_PER_U8 = 257f

        fun expectedBytes(size: Int, bytesPerChannel: Int): Int = size * size * size * CHANNELS * bytesPerChannel

        fun bytesPerChannel(format: String): Int = when (format.lowercase()) {
            FORMAT_RGB8 -> 1
            FORMAT_RGB16 -> 2
            else -> throw IllegalArgumentException("Nieznany format LUT: $format")
        }

        /** Wczytuje LUT o zadanym rozmiarze i formacie (z katalogu); długość jest walidowana. */
        fun fromBinary(bytes: ByteArray, size: Int, format: String = FORMAT_RGB8): Lut =
            Lut(size, bytes, bytesPerChannel(format))

        /** Wczytuje surowy LUT RGB8, rozmiar wyliczany z długości (gdy brak wpisu katalogu). */
        fun fromBinary(bytes: ByteArray): Lut {
            val size = Math.round(Math.cbrt((bytes.size / CHANNELS).toDouble())).toInt()
            return Lut(size, bytes)
        }

        /** LUT tożsamościowy (wyjście = wejście w węzłach siatki). */
        fun identity(size: Int = DEFAULT_SIZE): Lut = generate(size) { r, g, b -> Triple(r, g, b) }

        /** Generuje LUT RGB8 z funkcji węzła: argumenty i wynik w zakresie 0..1. */
        fun generate(size: Int, f: (Float, Float, Float) -> Triple<Float, Float, Float>): Lut {
            val out = ByteArray(size * size * size * CHANNELS)
            var i = 0
            val max = (size - 1).toFloat()
            for (b in 0 until size) {
                for (g in 0 until size) {
                    for (r in 0 until size) {
                        val (ro, go, bo) = f(r / max, g / max, b / max)
                        out[i++] = toByte(ro)
                        out[i++] = toByte(go)
                        out[i++] = toByte(bo)
                    }
                }
            }
            return Lut(size, out)
        }

        private fun toByte(v: Float): Byte = Math.round(v.coerceIn(0f, 1f) * MAX_BYTE).toByte()
    }
}
