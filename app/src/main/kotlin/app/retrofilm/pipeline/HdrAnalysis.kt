package app.retrofilm.pipeline

import kotlin.math.pow

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

    /**
     * Zapas HDR (EV) zmierzony z samej gain mapy (I): 99. percentyl wzmocnienia log2 = lerp(min, max, g^(1/γ))
     * zakodowanych wartości [encoded] (0..255). Metadane HDRCapacityMax mówią o możliwościach, nie o treści
     * zdjęcia – scena bez jasnych świateł ma mapę bliską zera i nie powinna dostać zapasu „na kolano”.
     */
    fun measuredHeadroom(encoded: IntArray, minLog2: Float, maxLog2: Float, gamma: Float): Float {
        if (encoded.isEmpty()) return 0f
        val hist = IntArray(BYTE_LEVELS)
        for (v in encoded) hist[v.coerceIn(0, BYTE_LEVELS - 1)]++
        val limit = (encoded.size * P99).toInt()
        var acc = 0
        var level = BYTE_LEVELS - 1
        for (i in 0 until BYTE_LEVELS) {
            acc += hist[i]
            if (acc > limit) {
                level = i
                break
            }
        }
        val g = (level / (BYTE_LEVELS - 1f)).pow(1f / gamma.coerceAtLeast(MIN_GAMMA))
        return (minLog2 + (maxLog2 - minLog2) * g).coerceIn(0f, MAX_HEADROOM_EV)
    }

    /**
     * Zapas HDR (EV) DNG z pomniejszonego dekodu F16 w liniowym rozszerzonym sRGB (J): 99,5 percentyl luminancji
     * ponad biel SDR (1,0) po zniesieniu BaselineExposure, którą dekoder już nałożył ([baselineEv] – jasność po
     * zalecanej ekspozycji producenta to punkt odniesienia, zapas liczymy względem niej). Dekoder, który obcina
     * do 1,0, daje 0 – bezpieczny brak zapasu.
     */
    fun rawHeadroom(linearLuma: FloatArray): Float {
        if (linearLuma.isEmpty()) return 0f
        val sorted = linearLuma.copyOf().also { it.sort() }
        val p = sorted[((sorted.size - 1) * RAW_PERCENTILE).toInt()]
        return if (p <= 1f) 0f else (kotlin.math.ln(p) / kotlin.math.ln(2f)).coerceIn(0f, MAX_HEADROOM_EV)
    }

    private const val RAW_PERCENTILE = 0.995f
    private const val BYTE_LEVELS = 256
    private const val P99 = 0.99f
    private const val MIN_GAMMA = 0.01f

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

    internal fun isTiff(b: ByteArray) = b.size > 8 && (
        (b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte() && b[2].toInt() == 42 && b[3].toInt() == 0) ||
            (b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte() && b[2].toInt() == 0 && b[3].toInt() == 42)
        )
}
