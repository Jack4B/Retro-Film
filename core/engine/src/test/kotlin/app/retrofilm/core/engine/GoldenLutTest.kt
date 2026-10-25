package app.retrofilm.core.engine

import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden: siatka 17³ (tools/golden/grid17.png) przez LUT-y z APK, trilinear, porównanie z PNG
 * z tools/golden (tools/golden/make_golden.py), tolerancja ≤ 1 poziom. golden.json musi
 * pokrywać dokładnie identity + filmy startowe z assets/luts/catalog.json.
 */
class GoldenLutTest {
    private val assets = File("src/main/assets")
    private val golden = File("../../tools/golden")

    private data class Case(val lut: String, val file: String, val input: String, val expected: String)

    private fun field(obj: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(obj)?.groupValues?.get(1) ?: error("brak $name w $obj")

    private fun objects(json: String): List<String> = Regex("\\{[^{}]*\\}").findAll(json).map { it.value }.toList()

    private fun cases(): List<Case> = objects(File(golden, "golden.json").readText()).map {
        Case(field(it, "lut"), field(it, "file"), field(it, "input"), field(it, "expected"))
    }

    @Test
    fun goldenCoversExactlyApkStarterSet() {
        val apk = objects(File(assets, "luts/catalog.json").readText()).map { field(it, "id") }.toSet()
        assertEquals(11, apk.size)
        assertTrue("identity" in apk)
        assertEquals(apk, cases().map { it.lut }.toSet())
    }

    @Test
    fun lutOutputsMatchGolden() {
        val catalog = objects(File(assets, "luts/catalog.json").readText()).associateBy { field(it, "id") }
        for (c in cases()) {
            val bytes = File(assets, c.file).readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals("sha256 ${c.lut}", field(catalog.getValue(c.lut), "sha256"), sha)
            val n = Regex("\"size\"\\s*:\\s*(\\d+)").find(catalog.getValue(c.lut))!!.groupValues[1].toInt()
            assertEquals(n * n * n * 3, bytes.size)
            val maxDiff = maxDiff(bytes, n, ImageIO.read(File(golden, c.input)), ImageIO.read(File(golden, c.expected)))
            assertTrue("${c.lut}: max różnica $maxDiff > 1", maxDiff <= 1)
        }
    }

    private fun maxDiff(lut: ByteArray, n: Int, input: BufferedImage, expected: BufferedImage): Int {
        var maxDiff = 0
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                val px = input.getRGB(x, y)
                val out = apply(lut, n, (px shr 16) and 0xff, (px shr 8) and 0xff, px and 0xff)
                val e = expected.getRGB(x, y)
                val exp = intArrayOf((e shr 16) and 0xff, (e shr 8) and 0xff, e and 0xff)
                maxDiff = maxOf(maxDiff, (0..2).maxOf { abs(out[it] - exp[it]) })
            }
        }
        return maxDiff
    }

    private fun apply(lut: ByteArray, n: Int, r: Int, g: Int, b: Int): IntArray {
        val s = (n - 1) / 255.0
        val p = doubleArrayOf(r * s, g * s, b * s)
        val i0 = IntArray(3) { minOf(floor(p[it]).toInt(), n - 2) }
        val f = DoubleArray(3) { p[it] - i0[it] }
        val out = DoubleArray(3)
        for (corner in 0..7) {
            val d = IntArray(3) { (corner shr it) and 1 }
            val w = (0..2).fold(1.0) { acc, k -> acc * if (d[k] == 1) f[k] else 1 - f[k] }
            val idx = 3 * ((i0[0] + d[0]) + (i0[1] + d[1]) * n + (i0[2] + d[2]) * n * n)
            for (k in 0..2) out[k] += w * (lut[idx + k].toInt() and 0xff) / 255.0
        }
        return IntArray(3) { (out[it].coerceIn(0.0, 1.0) * 255).roundToInt() }
    }
}
