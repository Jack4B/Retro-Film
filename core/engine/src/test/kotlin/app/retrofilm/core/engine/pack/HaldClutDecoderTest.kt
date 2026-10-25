package app.retrofilm.core.engine.pack

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Test

class HaldClutDecoderTest {
    @Test
    fun identityLevel12DecodesTo144Grid() {
        val png = identityHald(level = 12)
        val lut = HaldClutDecoder.decode(png.inputStream(), expectedSize = 144)
        assertEquals(144, lut.size)
        assertEquals(144 * 144 * 144 * 3, lut.data.size)
        for ((r, g, b) in listOf(Triple(0, 0, 0), Triple(143, 0, 0), Triple(0, 143, 0), Triple(5, 77, 143))) {
            assertEquals(value(r, 144), lut.node(r, g, b, 0))
            assertEquals(value(g, 144), lut.node(r, g, b, 1))
            assertEquals(value(b, 144), lut.node(r, g, b, 2))
        }
    }

    @Test
    fun allFilterTypesAndRgbaAreHandled() {
        val png = identityHald(level = 4, rgba = true, filters = true)
        val lut = HaldClutDecoder.decode(png.inputStream())
        assertEquals(16, lut.size)
        for (b in 0 until 16) {
            for (g in 0 until 16) {
                for (r in 0 until 16) {
                    assertEquals(value(r, 16), lut.node(r, g, b, 0))
                    assertEquals(value(b, 16), lut.node(r, g, b, 2))
                }
            }
        }
    }

    @Test
    fun identityLevel16DecodesTo256Grid() {
        val lut = HaldClutDecoder.decode(identityHald(level = 16).inputStream(), expectedSize = 256)
        assertEquals(256, lut.size)
        assertEquals(50_331_648, lut.data.size)
        assertEquals(255, lut.node(255, 0, 7, 0))
        assertEquals(7, lut.node(255, 0, 7, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongExpectedSizeRejected() {
        HaldClutDecoder.decode(identityHald(level = 4).inputStream(), expectedSize = 144)
    }

    companion object {
        fun value(i: Int, n: Int) = Math.round(i * 255f / (n - 1))

        /** Syntetyczny Hald tożsamościowy: piksel k ↦ (r,g,b), r najszybciej. */
        @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
        fun identityHald(level: Int, rgba: Boolean = false, filters: Boolean = false): ByteArray {
            val n = level * level
            val w = level * level * level
            val ch = if (rgba) 4 else 3
            val pixels = ByteArray(w * w * ch)
            var k = 0
            for (b in 0 until n) {
                for (g in 0 until n) {
                    for (r in 0 until n) {
                        pixels[k++] = value(r, n).toByte()
                        pixels[k++] = value(g, n).toByte()
                        pixels[k++] = value(b, n).toByte()
                        if (rgba) pixels[k++] = -1
                    }
                }
            }
            val stride = w * ch
            val raw = ByteArrayOutputStream()
            for (y in 0 until w) {
                val f = if (filters) y % 5 else 0
                raw.write(f)
                for (x in 0 until stride) {
                    val i = y * stride + x
                    val v = pixels[i].toInt() and 0xFF
                    val a = if (x >= ch) pixels[i - ch].toInt() and 0xFF else 0
                    val up = if (y > 0) pixels[i - stride].toInt() and 0xFF else 0
                    val c = if (x >= ch && y > 0) pixels[i - stride - ch].toInt() and 0xFF else 0
                    val pred = when (f) {
                        1 -> a
                        2 -> up
                        3 -> (a + up) / 2
                        4 -> paeth(a, up, c)
                        else -> 0
                    }
                    raw.write((v - pred) and 0xFF)
                }
            }
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
            val ihdr = ByteArrayOutputStream().also {
                DataOutputStream(it).apply {
                    writeInt(w)
                    writeInt(w)
                    writeByte(8)
                    writeByte(if (rgba) 6 else 2)
                    writeByte(0)
                    writeByte(0)
                    writeByte(0)
                }
            }.toByteArray()
            chunk(out, "IHDR", ihdr)
            val def = Deflater()
            def.setInput(raw.toByteArray())
            def.finish()
            val z = ByteArrayOutputStream()
            val buf = ByteArray(65536)
            while (!def.finished()) z.write(buf, 0, def.deflate(buf))
            chunk(out, "IDAT", z.toByteArray())
            chunk(out, "IEND", ByteArray(0))
            return out.toByteArray()
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

        private fun chunk(out: ByteArrayOutputStream, type: String, body: ByteArray) {
            val d = DataOutputStream(out)
            d.writeInt(body.size)
            val t = type.toByteArray(Charsets.US_ASCII)
            d.write(t)
            d.write(body)
            val crc = CRC32().apply {
                update(t)
                update(body)
            }
            d.writeInt(crc.value.toInt())
        }
    }
}
