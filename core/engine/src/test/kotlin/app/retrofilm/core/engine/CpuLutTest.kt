package app.retrofilm.core.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CpuLutTest {
    private val pixels = IntArray(PIXEL_COUNT) { i ->
        val r = i * 37 and 0xFF
        val g = i * 101 and 0xFF
        val b = i * 211 and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun identityLutKeepsPixels() {
        val out = CpuLut.applyToPixels(pixels, Lut.identity(), 1f)
        assertArrayEquals(pixels, out)
    }

    @Test
    fun zeroIntensityKeepsPixels() {
        val invert = Lut.generate(Lut.DEFAULT_SIZE) { r, g, b -> Triple(1 - r, 1 - g, 1 - b) }
        assertArrayEquals(pixels, CpuLut.applyToPixels(pixels, invert, 0f))
    }

    @Test
    fun invertLutGivesKnownValues() {
        val invert = Lut.generate(Lut.DEFAULT_SIZE) { r, g, b -> Triple(1 - r, 1 - g, 1 - b) }
        val src = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0x80102030.toInt())
        val out = CpuLut.applyToPixels(src, invert, 1f)
        assertEquals(0xFFFFFFFF.toInt(), out[0])
        assertEquals(0xFF000000.toInt(), out[1])
        // Alfa zachowana, kanały odwrócone z tolerancją 1 poziomu (kwantyzacja węzłów do 8 bitów).
        assertEquals(0x80, out[2] ushr 24)
        assertNear(0xFF - 0x10, out[2] shr 16 and 0xFF)
        assertNear(0xFF - 0x20, out[2] shr 8 and 0xFF)
        assertNear(0xFF - 0x30, out[2] and 0xFF)
    }

    @Test
    fun trilinearInterpolatesBetweenNodes() {
        // LUT 2^3: czerwony kanał wyjścia = 200 * r, reszta 0. Wejście r=0.5 → 100.
        val data = ByteArray(2 * 2 * 2 * 3)
        val lut = Lut(2, data)
        data[lut.offset(1, 0, 0)] = 200.toByte()
        data[lut.offset(1, 1, 0)] = 200.toByte()
        data[lut.offset(1, 0, 1)] = 200.toByte()
        data[lut.offset(1, 1, 1)] = 200.toByte()
        val out = FloatArray(3)
        CpuLut.sample(lut, 0.5f, 0.3f, 0.7f, out)
        assertEquals(100f / 255f, out[0], 1e-5f)
        assertEquals(0f, out[1], 1e-6f)
    }

    @Test
    fun halfIntensityMixes() {
        val black = Lut.generate(Lut.DEFAULT_SIZE) { _, _, _ -> Triple(0f, 0f, 0f) }
        val out = CpuLut.applyToPixels(intArrayOf(0xFFC8C8C8.toInt()), black, 0.5f)
        assertEquals(0xFF646464.toInt(), out[0])
    }

    @Test
    fun binaryFormatIsRedFastest() {
        val bytes = ByteArray(33 * 33 * 33 * 3)
        bytes[3] = 7 // wpis (r=1, g=0, b=0), kanał R
        val lut = Lut.fromBinary(bytes)
        assertEquals(33, lut.size)
        assertEquals(7, lut.node(1, 0, 0, 0))
        assertEquals(0, lut.node(0, 1, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongLength() {
        Lut(33, ByteArray(10))
    }

    private fun assertNear(expected: Int, actual: Int) {
        assertEquals(expected.toFloat(), actual.toFloat(), 1f)
    }

    private companion object {
        const val PIXEL_COUNT = 4096
    }
}
