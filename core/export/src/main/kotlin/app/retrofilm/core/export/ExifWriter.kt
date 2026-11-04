package app.retrofilm.core.export

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToLong

/**
 * Biała lista EXIF przenoszona do eksportu: aparat, obiektyw, ekspozycja, data, autor. Nigdy GPS, MakerNote,
 * miniatury ani numery seryjne – nie ma ich w tym typie, więc nie da się ich przepisać przez pomyłkę.
 */
data class ExifData(
    val make: String? = null,
    val model: String? = null,
    val lensMake: String? = null,
    val lensModel: String? = null,
    val dateTimeOriginal: String? = null,
    val offsetTimeOriginal: String? = null,
    val exposureTime: Double? = null,
    val fNumber: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val focalLength35: Int? = null,
    val artist: String? = null,
    val copyright: String? = null
)

/** Zapis bloku TIFF/EXIF (big-endian, IFD0 + Exif IFD) z [ExifData]. */
@Suppress("MagicNumber")
object ExifWriter {
    private const val ASCII = 2
    private const val SHORT = 3
    private const val LONG = 4
    private const val RATIONAL = 5
    private const val UNDEFINED = 7
    private const val EXIF_IFD = 0x8769

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    /** [wideGamut] – ColorSpace = Uncalibrated (P3 opisuje profil ICC), inaczej sRGB. */
    fun write(d: ExifData, width: Int, height: Int, software: String, wideGamut: Boolean = false): ByteArray {
        val ifd0 = ifd0(d, software)
        val exif = exifIfd(d, width, height, wideGamut)
        val ifd0Size = ifdSize(ifd0.size + 1, ifd0)
        val exifOffset = 8 + ifd0Size
        val all = (ifd0 + long(EXIF_IFD, exifOffset)).sortedBy { it.tag }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x4D, 0x4D, 0, 42, 0, 0, 0, 8))
        out.write(ifd(all, 8))
        out.write(ifd(exif.sortedBy { it.tag }, exifOffset))
        return out.toByteArray()
    }

    private fun ifd0(d: ExifData, software: String) = listOfNotNull(
        d.make?.let { ascii(0x010F, it) },
        d.model?.let { ascii(0x0110, it) },
        short(0x0112, 1),
        ascii(0x0131, software),
        d.artist?.let { ascii(0x013B, it) },
        d.copyright?.let { ascii(0x8298, it) }
    )

    private fun exifIfd(d: ExifData, width: Int, height: Int, wideGamut: Boolean) = listOfNotNull(
        d.exposureTime?.let { rational(0x829A, it) },
        d.fNumber?.let { rational(0x829D, it) },
        d.iso?.let { short(0x8827, it.coerceIn(0, 65535)) },
        Entry(0x9000, UNDEFINED, 4, "0232".toByteArray(Charsets.US_ASCII)),
        d.dateTimeOriginal?.let { ascii(0x9003, it) },
        d.offsetTimeOriginal?.let { ascii(0x9011, it) },
        d.focalLength?.let { rational(0x920A, it) },
        short(0xA001, if (wideGamut) 0xFFFF else 1),
        long(0xA002, width),
        long(0xA003, height),
        d.focalLength35?.let { short(0xA405, it.coerceIn(0, 65535)) },
        d.lensMake?.let { ascii(0xA433, it) },
        d.lensModel?.let { ascii(0xA434, it) }
    )

    private fun ifdSize(n: Int, entries: List<Entry>) =
        2 + n * 12 + 4 + entries.sumOf { if (it.data.size > 4) pad(it.data.size) else 0 }

    private fun pad(n: Int) = (n + 1) and 1.inv()

    private fun ifd(entries: List<Entry>, at: Int): ByteArray {
        val head = 2 + entries.size * 12 + 4
        val b = ByteBuffer.allocate(ifdSize(entries.size, entries))
        b.putShort(entries.size.toShort())
        var extra = at + head
        val tail = ByteArrayOutputStream()
        for (e in entries) {
            b.putShort(e.tag.toShort())
            b.putShort(e.type.toShort())
            b.putInt(e.count)
            if (e.data.size <= 4) {
                b.put(e.data.copyOf(4))
            } else {
                b.putInt(extra)
                tail.write(e.data.copyOf(pad(e.data.size)))
                extra += pad(e.data.size)
            }
        }
        b.putInt(0)
        b.put(tail.toByteArray())
        return b.array()
    }

    private fun ascii(tag: Int, s: String): Entry {
        val bytes = s.filter { it.code in 32..126 }.take(255).toByteArray(Charsets.US_ASCII) + 0
        return Entry(tag, ASCII, bytes.size, bytes)
    }

    private fun short(tag: Int, v: Int) = Entry(tag, SHORT, 1, byteArrayOf((v shr 8).toByte(), v.toByte()))

    private fun long(tag: Int, v: Int) = Entry(tag, LONG, 1, ByteBuffer.allocate(4).putInt(v).array())

    private fun rational(tag: Int, v: Double): Entry {
        val (n, d) = if (v > 0 && v < 1) 1L to (1 / v).roundToLong() else (v * 1000).roundToLong() to 1000L
        return Entry(tag, RATIONAL, 1, ByteBuffer.allocate(8).putInt(n.toInt()).putInt(d.toInt()).array())
    }
}
