package app.retrofilm.core.export

import java.io.ByteArrayOutputStream
import java.util.stream.IntStream
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Koder JPEG baseline w czystym Kotlinie, bez podpróbkowania chromy (4:4:4) – `Bitmap.compress` zawsze robi 4:2:0,
 * co rozmywa ostre krawędzie koloru (czerwień, napisy, ramki). Tablice kwantyzacji i Huffmana ze standardu (aneks K,
 * skalowanie jakości jak w IJG), DCT zmiennoprzecinkowe AAN. Obraz jest czytany pasami ([rows]), DCT liczy się
 * równolegle na pasie, a kodowanie entropijne idzie po kolei – pamięć nie zależy od rozmiaru zdjęcia.
 */
@Suppress("MagicNumber", "TooManyFunctions") // stałe i prymitywy formatu JPEG (ITU T.81)
object JpegEncoder {
    private const val BAND_BLOCKS = 32
    private const val BLOCK = 8
    private const val COEFS = 64

    /**
     * [rows] wypełnia `out` wierszami ARGB od `y0` (liczba wierszy `count`, szerokość [width]).
     * [icc] trafia do APP2 `ICC_PROFILE`, [exif] (blok TIFF bez nagłówka `Exif\0\0`) do APP1.
     */
    @Suppress("LongParameterList")
    fun encode(
        width: Int,
        height: Int,
        quality: Int,
        icc: ByteArray?,
        exif: ByteArray?,
        rows: (y0: Int, count: Int, out: IntArray) -> Unit
    ): ByteArray {
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE)
        val q = Tables(quality.coerceIn(1, 100))
        val out = ByteArrayOutputStream(width * height / 2 + 4096)
        Headers.write(
            out,
            width,
            height,
            q,
            JpegMetadata.withMetadata(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), icc, exif)
        )
        val bits = BitWriter(out)
        val bw = (width + 7) / 8
        val bandRows = BLOCK * BAND_BLOCKS
        val buf = IntArray(width * bandRows)
        val coefs = Array(3) { ShortArray(bw * BAND_BLOCKS * COEFS) }
        val dc = IntArray(3)
        var y0 = 0
        while (y0 < height) {
            val count = minOf(bandRows, height - y0)
            rows(y0, count, buf)
            val bh = (count + 7) / 8
            IntStream.range(0, bh * bw).parallel().forEach { b ->
                transformBlock(buf, width, count, b % bw * BLOCK, b / bw * BLOCK, q, coefs, b * COEFS)
            }
            for (b in 0 until bh * bw) {
                for (c in 0..2) dc[c] = bits.block(coefs[c], b * COEFS, dc[c], if (c == 0) LUMA else CHROMA)
            }
            y0 += count
        }
        bits.flush()
        out.write(0xFF)
        out.write(0xD9)
        return out.toByteArray()
    }

    @Suppress("LongParameterList")
    private fun transformBlock(
        px: IntArray,
        w: Int,
        h: Int,
        x0: Int,
        y0: Int,
        q: Tables,
        out: Array<ShortArray>,
        at: Int
    ) {
        val ycc = Array(3) { FloatArray(COEFS) }
        for (j in 0 until BLOCK) {
            val y = minOf(y0 + j, h - 1)
            for (i in 0 until BLOCK) {
                val c = px[y * w + minOf(x0 + i, w - 1)]
                val r = ((c shr 16) and 0xFF).toFloat()
                val g = ((c shr 8) and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()
                val k = j * BLOCK + i
                ycc[0][k] = 0.299f * r + 0.587f * g + 0.114f * b - 128f
                ycc[1][k] = -0.168736f * r - 0.331264f * g + 0.5f * b
                ycc[2][k] = 0.5f * r - 0.418688f * g - 0.081312f * b
            }
        }
        for (c in 0..2) {
            Dct.forward(ycc[c])
            val div = if (c == 0) q.lumaDiv else q.chromaDiv
            val dst = out[c]
            for (k in 0 until COEFS) {
                val v = ycc[c][ZIGZAG[k]] * div[ZIGZAG[k]]
                dst[at + k] = (if (v >= 0) v + 0.5f else v - 0.5f).toInt().toShort()
            }
        }
    }

    /** Tablice kwantyzacji (kolejność naturalna) i odwrotności dzielników z czynnikami AAN. */
    internal class Tables(quality: Int) {
        val luma = scale(BASE_LUMA, quality)
        val chroma = scale(BASE_CHROMA, quality)
        val lumaDiv = divisors(luma)
        val chromaDiv = divisors(chroma)

        private fun scale(base: IntArray, quality: Int): IntArray {
            val s = if (quality < 50) 5000 / quality else 200 - quality * 2
            return IntArray(COEFS) { ((base[it] * s + 50) / 100).coerceIn(1, 255) }
        }

        private fun divisors(t: IntArray) = FloatArray(COEFS) {
            1f / (t[it] * AAN[it / BLOCK] * AAN[it % BLOCK] * 8f)
        }
    }

    /** DCT 8×8 AAN (jak `jfdctflt.c`), wynik w kolejności naturalnej, przeskalowany o czynniki AAN. */
    private object Dct {
        fun forward(d: FloatArray) {
            for (r in 0 until BLOCK) pass(d, r * BLOCK, 1)
            for (c in 0 until BLOCK) pass(d, c, BLOCK)
        }

        @Suppress("LongMethod")
        private fun pass(d: FloatArray, o: Int, s: Int) {
            val t0 = d[o] + d[o + 7 * s]
            val t7 = d[o] - d[o + 7 * s]
            val t1 = d[o + s] + d[o + 6 * s]
            val t6 = d[o + s] - d[o + 6 * s]
            val t2 = d[o + 2 * s] + d[o + 5 * s]
            val t5 = d[o + 2 * s] - d[o + 5 * s]
            val t3 = d[o + 3 * s] + d[o + 4 * s]
            val t4 = d[o + 3 * s] - d[o + 4 * s]
            val t10 = t0 + t3
            val t13 = t0 - t3
            val t11 = t1 + t2
            val t12 = t1 - t2
            d[o] = t10 + t11
            d[o + 4 * s] = t10 - t11
            val z1 = (t12 + t13) * 0.707106781f
            d[o + 2 * s] = t13 + z1
            d[o + 6 * s] = t13 - z1
            val a10 = t4 + t5
            val a11 = t5 + t6
            val a12 = t6 + t7
            val z5 = (a10 - a12) * 0.382683433f
            val z2 = 0.541196100f * a10 + z5
            val z4 = 1.306562965f * a12 + z5
            val z3 = a11 * 0.707106781f
            val z11 = t7 + z3
            val z13 = t7 - z3
            d[o + 5 * s] = z13 + z2
            d[o + 3 * s] = z13 - z2
            d[o + s] = z11 + z4
            d[o + 7 * s] = z11 - z4
        }
    }

    /** Huffman (kody z tablic BITS/VALS) i zapis bitów z wstawianiem 0x00 po 0xFF. */
    internal class Huffman(bits: IntArray, vals: IntArray) {
        val code = IntArray(256)
        val size = IntArray(256)

        init {
            var c = 0
            var k = 0
            for (len in 1..16) {
                repeat(bits[len - 1]) {
                    code[vals[k]] = c
                    size[vals[k]] = len
                    c++
                    k++
                }
                c = c shl 1
            }
        }
    }

    private class BitWriter(private val out: ByteArrayOutputStream) {
        private var acc = 0L
        private var n = 0

        fun put(value: Int, len: Int) {
            if (len == 0) return
            acc = (acc shl len) or (value.toLong() and ((1L shl len) - 1))
            n += len
            while (n >= 8) {
                val b = ((acc shr (n - 8)) and 0xFF).toInt()
                out.write(b)
                if (b == 0xFF) out.write(0)
                n -= 8
            }
        }

        fun flush() {
            if (n > 0) put((1 shl (8 - n)) - 1, 8 - n)
        }

        /** Koduje blok (zigzag) i zwraca nowy predyktor DC. */
        fun block(c: ShortArray, at: Int, prevDc: Int, t: Pair<Huffman, Huffman>): Int {
            val (dcT, acT) = t
            val dc = c[at].toInt()
            emit(dc - prevDc, dcT, 0)
            var run = 0
            for (k in 1 until COEFS) {
                val v = c[at + k].toInt()
                if (v == 0) {
                    run++
                    continue
                }
                while (run > 15) {
                    put(acT.code[0xF0], acT.size[0xF0])
                    run -= 16
                }
                emit(v, acT, run)
                run = 0
            }
            if (run > 0) put(acT.code[0], acT.size[0])
            return dc
        }

        private fun emit(v: Int, t: Huffman, run: Int) {
            val a = if (v < 0) -v else v
            val nbits = if (a == 0) 0 else 32 - Integer.numberOfLeadingZeros(a)
            val sym = (run shl 4) or nbits
            put(t.code[sym], t.size[sym])
            put(if (v < 0) v - 1 else v, nbits)
        }
    }

    private object Headers {
        /** [meta] = SOI + segmenty ICC/EXIF (z [JpegMetadata.withMetadata]). */
        fun write(out: ByteArrayOutputStream, w: Int, h: Int, q: Tables, meta: ByteArray) {
            out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            segment(out, 0xE0, byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
            out.write(meta, 2, meta.size - 2)
            val dqt = ByteArrayOutputStream()
            for ((id, t) in listOf(0 to q.luma, 1 to q.chroma)) {
                dqt.write(id)
                for (k in 0 until COEFS) dqt.write(t[ZIGZAG[k]])
            }
            segment(out, 0xDB, dqt.toByteArray())
            segment(
                out,
                0xC0,
                byteArrayOf(8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), 3) +
                    byteArrayOf(1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1)
            )
            val dht = ByteArrayOutputStream()
            for ((cls, bits, vals) in HUFFMAN_SPECS) {
                dht.write(cls)
                bits.forEach { dht.write(it) }
                vals.forEach { dht.write(it) }
            }
            segment(out, 0xC4, dht.toByteArray())
            segment(out, 0xDA, byteArrayOf(3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0))
        }

        private fun segment(out: ByteArrayOutputStream, marker: Int, body: ByteArray) =
            JpegMetadata.segment(out, marker, body)
    }

    private const val MAX_SIDE = 65535

    private val AAN = FloatArray(BLOCK) {
        if (it == 0) 1f else (cos(it * Math.PI / 16) * sqrt(2.0)).toFloat()
    }

    internal val ZIGZAG = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14,
        21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60,
        61, 54, 47, 55, 62, 63
    )

    private val BASE_LUMA = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29,
        51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121,
        120, 101, 72, 92, 95, 98, 112, 100, 103, 99
    )
    private val BASE_CHROMA = IntArray(COEFS) { 99 }.also {
        val top = intArrayOf(17, 18, 24, 47, 18, 21, 26, 66, 24, 26, 56, 99, 47, 66, 99, 99)
        for (k in 0 until 16) it[(k / 4) * BLOCK + k % 4] = top[k]
    }

    private val DC_LUMA_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val DC_LUMA_VALS = IntArray(12) { it }
    private val DC_CHROMA_BITS = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
    private val DC_CHROMA_VALS = IntArray(12) { it }
    private val AC_LUMA_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
    private val AC_LUMA_VALS = hex(
        "01020300041105122131410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435" +
            "363738393a434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a9293949596" +
            "9798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9ea" +
            "f1f2f3f4f5f6f7f8f9fa"
    )
    private val AC_CHROMA_BITS = intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77)
    private val AC_CHROMA_VALS = hex(
        "000102031104052131061241510761711322328108144291a1b1c109233352f0156272d10a162434e125f11718191a26272829" +
            "2a35363738393a434445464748494a535455565758595a636465666768696a737475767778797a82838485868788898a9293" +
            "9495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae2e3e4e5e6e7e8" +
            "e9eaf2f3f4f5f6f7f8f9fa"
    )

    private fun hex(s: String) = IntArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16) }

    private val HUFFMAN_SPECS = listOf(
        Triple(0x00, DC_LUMA_BITS, DC_LUMA_VALS),
        Triple(0x10, AC_LUMA_BITS, AC_LUMA_VALS),
        Triple(0x01, DC_CHROMA_BITS, DC_CHROMA_VALS),
        Triple(0x11, AC_CHROMA_BITS, AC_CHROMA_VALS)
    )

    private val LUMA = Huffman(DC_LUMA_BITS, DC_LUMA_VALS) to Huffman(AC_LUMA_BITS, AC_LUMA_VALS)
    private val CHROMA = Huffman(DC_CHROMA_BITS, DC_CHROMA_VALS) to Huffman(AC_CHROMA_BITS, AC_CHROMA_VALS)
}
