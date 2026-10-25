package app.retrofilm.core.engine

/**
 * LUT 3D w formacie binarnym RGB8.
 *
 * Format pliku `.rgb8` (zdefiniowany w docs/LUT-BIN.md): surowe `size^3 * 3` bajtów bez nagłówka,
 * kolejność wpisów: indeks `r` zmienia się najszybciej, potem `g`, na końcu `b`
 * (offset = `((b * size + g) * size + r) * 3`), każdy wpis to bajty R, G, B (0..255).
 */
class Lut(val size: Int, val data: ByteArray) {
    init {
        require(size in MIN_SIZE..MAX_SIZE) { "Rozmiar LUT $size poza zakresem $MIN_SIZE..$MAX_SIZE" }
        require(data.size == size * size * size * CHANNELS) {
            "LUT $size^3 wymaga ${size * size * size * CHANNELS} bajtów, jest ${data.size}"
        }
    }

    /** Wartość kanału [channel] (0=R, 1=G, 2=B) w węźle siatki jako 0..255. */
    fun node(r: Int, g: Int, b: Int, channel: Int): Int = data[offset(r, g, b) + channel].toInt() and BYTE_MASK

    fun offset(r: Int, g: Int, b: Int): Int = ((b * size + g) * size + r) * CHANNELS

    companion object {
        const val DEFAULT_SIZE = 33
        const val CHANNELS = 3
        const val MIN_SIZE = 2
        const val MAX_SIZE = 64
        private const val BYTE_MASK = 0xFF
        private const val MAX_BYTE = 255

        /** Wczytuje surowy LUT RGB8; rozmiar wyliczany z długości (domyślnie 33^3). */
        fun fromBinary(bytes: ByteArray): Lut {
            val entries = bytes.size / CHANNELS
            val size = Math.round(Math.cbrt(entries.toDouble())).toInt()
            return Lut(size, bytes)
        }

        /** LUT tożsamościowy (wyjście = wejście w węzłach siatki). */
        fun identity(size: Int = DEFAULT_SIZE): Lut = generate(size) { r, g, b -> Triple(r, g, b) }

        /** Generuje LUT z funkcji węzła: argumenty i wynik w zakresie 0..1. */
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
