package app.retrofilm.pipeline

/**
 * Informacje o zapasie jasności spoza pikseli SDR, dla automatycznej korekty ([AutoContext]), w pełni offline:
 * - Ultra HDR JPEG: `hdrgm:HDRCapacityMax` (albo `GainMapMax`) z XMP gain mapy – ile EV ponad biel SDR zapisano
 *   w gain mapie, więc rozjaśnienie może sięgnąć nieco ponad przepalenie podglądu ([AutoEnhance] ogranicza to kolanem);
 * - DNG: znacznik `BaselineExposure` (0xC62A) – zalecana przez producenta korekta ekspozycji, prior przy niskiej
 *   pewności pomiaru.
 * Brak danych = [NONE]. Analiza nie dekoduje obrazu (kilka kB nagłówka).
 */
@Suppress("MagicNumber") // znaczniki TIFF i rozmiary pól
object HdrAnalysis {
    data class HdrInfo(val headroomEv: Float, val baselineEv: Float?)

    val NONE = HdrInfo(0f, null)
    private const val BASELINE_EXPOSURE = 0xC62A
    private const val SRATIONAL = 10
    private const val MAX_HEADROOM_EV = 6f
    private const val XMP_SCAN = 256 * 1024
    private val CAPACITY = Regex(
        """hdrgm:(?:HDRCapacityMax|GainMapMax)\s*=\s*"\s*(-?[0-9.]+)|""" +
            """<hdrgm:(?:HDRCapacityMax|GainMapMax)>\s*(-?[0-9.]+)"""
    )

    fun of(bytes: ByteArray): HdrInfo = when {
        isTiff(bytes) -> HdrInfo(0f, baselineExposure(bytes))
        UltraHdrJpeg.hasGainMap(bytes) -> HdrInfo(gainMapHeadroom(bytes), null)
        else -> NONE
    }

    /** Największa wartość HDRCapacityMax/GainMapMax (log2) z XMP na początku pliku; 0 gdy brak. */
    fun gainMapHeadroom(bytes: ByteArray): Float {
        val text = String(bytes, 0, minOf(bytes.size, XMP_SCAN), Charsets.ISO_8859_1)
        return CAPACITY.findAll(text)
            .mapNotNull { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toFloatOrNull() }
            .maxOrNull()?.coerceIn(0f, MAX_HEADROOM_EV) ?: 0f
    }

    /** BaselineExposure z IFD0 pliku TIFF/DNG albo null. */
    fun baselineExposure(b: ByteArray): Float? {
        if (!isTiff(b)) return null
        val r = Reader(b, b[0] == 'I'.code.toByte())
        val ifd = r.u32(4).toInt()
        val n = if (ifd in 8..b.size - 2) r.u16(ifd) else 0
        val entry = (0 until n).map { ifd + 2 + it * 12 }
            .firstOrNull { e -> e + 12 <= b.size && r.u16(e) == BASELINE_EXPOSURE && r.u16(e + 2) == SRATIONAL }
        val at = entry?.let { r.u32(it + 8).toInt() }?.takeIf { it in 0..b.size - 8 }
        val den = at?.let { r.u32(it + 4).toInt() }?.takeIf { it != 0 }
        return den?.let { r.u32(at).toInt().toFloat() / it }
    }

    private class Reader(val b: ByteArray, val le: Boolean) {
        fun u16(at: Int) = if (le) (u(at) or (u(at + 1) shl 8)) else ((u(at) shl 8) or u(at + 1))

        fun u32(at: Int): Long {
            val v = (0..3).map { u(at + it).toLong() }
            return if (le) {
                v[0] or (v[1] shl 8) or (v[2] shl 16) or (v[3] shl 24)
            } else {
                (v[0] shl 24) or (v[1] shl 16) or (v[2] shl 8) or v[3]
            }
        }

        private fun u(at: Int) = b[at].toInt() and 0xFF
    }

    private fun isTiff(b: ByteArray) = b.size > 8 && (
        (b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte() && b[2].toInt() == 42 && b[3].toInt() == 0) ||
            (b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte() && b[2].toInt() == 0 && b[3].toInt() == 42)
        )
}
