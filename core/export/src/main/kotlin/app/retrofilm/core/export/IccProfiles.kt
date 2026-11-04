package app.retrofilm.core.export

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Profile ICC v2 (monitor RGB, PCS XYZ D50) budowane w kodzie: sRGB i Display P3 mają tę samą krzywą (sRGB),
 * różnią się kolorami podstawowymi (adaptacja Bradforda do D50). Bez pliku profilu w pliku JPEG przeglądarki
 * i Android zgadują przestrzeń; z profilem każdy menedżer koloru pokaże to samo.
 */
@Suppress("MagicNumber")
object IccProfiles {
    private const val CURVE_POINTS = 1024

    private val SRGB = arrayOf(
        doubleArrayOf(0.4360053, 0.2224691, 0.01393),
        doubleArrayOf(0.3850738, 0.7168952, 0.0971068),
        doubleArrayOf(0.1431147, 0.0606315, 0.7143447)
    )
    private val P3 = arrayOf(
        doubleArrayOf(0.515075, 0.2411702, -0.0010486),
        doubleArrayOf(0.2919397, 0.6922355, 0.0418841),
        doubleArrayOf(0.1571791, 0.06659, 0.7845459)
    )

    private val srgbBytes by lazy { build("sRGB IEC61966-2.1 (Retro Film)", SRGB) }
    private val p3Bytes by lazy { build("Display P3 (Retro Film)", P3) }

    fun srgb(): ByteArray = srgbBytes.copyOf()

    fun displayP3(): ByteArray = p3Bytes.copyOf()

    /** Nazwa z tagu `desc` – do testów i diagnostyki. */
    fun description(icc: ByteArray): String? {
        val b = ByteBuffer.wrap(icc)
        val n = b.getInt(128)
        for (i in 0 until n) {
            val at = 132 + i * 12
            if (b.getInt(at) == sig("desc")) {
                val off = b.getInt(at + 4)
                val len = b.getInt(off + 8)
                return String(icc, off + 12, len - 1, Charsets.US_ASCII)
            }
        }
        return null
    }

    private fun build(name: String, primaries: Array<DoubleArray>): ByteArray {
        val curve = curv()
        val tags = listOf(
            "desc" to desc(name),
            "cprt" to text("No copyright, use freely"),
            "wtpt" to xyz(doubleArrayOf(0.9642, 1.0, 0.8249)),
            "rXYZ" to xyz(primaries[0]),
            "gXYZ" to xyz(primaries[1]),
            "bXYZ" to xyz(primaries[2]),
            "rTRC" to curve,
            "gTRC" to curve,
            "bTRC" to curve
        )
        val tableSize = 4 + tags.size * 12
        val body = ByteArrayOutputStream()
        val offsets = HashMap<ByteArray, Int>()
        val entries = tags.map { (s, data) ->
            val off = offsets.getOrPut(data) {
                val o = 128 + tableSize + body.size()
                body.write(data)
                while (body.size() % 4 != 0) body.write(0)
                o
            }
            Triple(s, off, data.size)
        }
        val total = 128 + tableSize + body.size()
        val out = ByteBuffer.allocate(total)
        out.putInt(total)
        out.putInt(0)
        out.putInt(0x02100000)
        out.putInt(sig("mntr"))
        out.putInt(sig("RGB "))
        out.putInt(sig("XYZ "))
        repeat(3) { out.putInt(0) } // data/czas
        out.putInt(sig("acsp"))
        repeat(7) { out.putInt(0) } // platforma, flagi, producent, model, atrybuty(8), intencja
        out.putInt(s15(0.9642))
        out.putInt(s15(1.0))
        out.putInt(s15(0.8249))
        out.position(128)
        out.putInt(entries.size)
        for ((s, off, size) in entries) {
            out.putInt(sig(s))
            out.putInt(off)
            out.putInt(size)
        }
        out.put(body.toByteArray())
        return out.array()
    }

    private fun desc(name: String): ByteArray {
        val ascii = name.toByteArray(Charsets.US_ASCII)
        val b = ByteBuffer.allocate(12 + ascii.size + 1 + 8 + 3 + 67)
        b.putInt(sig("desc"))
        b.putInt(0)
        b.putInt(ascii.size + 1)
        b.put(ascii)
        b.put(0)
        return b.array()
    }

    private fun text(s: String): ByteArray {
        val ascii = s.toByteArray(Charsets.US_ASCII)
        val b = ByteBuffer.allocate(8 + ascii.size + 1)
        b.putInt(sig("text"))
        b.putInt(0)
        b.put(ascii)
        return b.array()
    }

    private fun xyz(v: DoubleArray): ByteArray = ByteBuffer.allocate(20).apply {
        putInt(sig("XYZ "))
        putInt(0)
        v.forEach { putInt(s15(it)) }
    }.array()

    private fun curv(): ByteArray {
        val b = ByteBuffer.allocate(12 + CURVE_POINTS * 2)
        b.putInt(sig("curv"))
        b.putInt(0)
        b.putInt(CURVE_POINTS)
        for (i in 0 until CURVE_POINTS) {
            val x = i / (CURVE_POINTS - 1.0)
            val y = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
            b.putShort((y * 65535).roundToInt().toShort())
        }
        return b.array()
    }

    private fun s15(v: Double) = (v * 65536).roundToInt()

    private fun sig(s: String) = s.fold(0) { acc, c -> (acc shl 8) or c.code }
}
