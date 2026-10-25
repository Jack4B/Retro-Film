package app.retrofilm.core.engine

import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Narzędzie (nie zwykły test): porównuje interpolację trójliniową i czworościenną LUT-ów 33³ (.rgb8)
 * z oryginałem HaldCLUT (ground truth) – ΔE2000 (sRGB D65 → Lab) we wszystkich punktach siatki HaldCLUT.
 *
 * Uruchomienie (pomijane bez zmiennych środowiskowych):
 * `RF_HALD_DIR=/ścieżka/HaldCLUT RF_LUT_DIR=core/engine/src/main/assets/luts ./gradlew :core:engine:testDebugUnitTest
 *  --tests '*LutAccuracyTool*'` → raport `build/reports/lut-accuracy.md`.
 * Opcjonalnie `RF_LUT_FILTER=superia` (podciąg id).
 */
class LutAccuracyTool {
    @Test
    fun compareInterpolations() {
        val haldDir = System.getenv("RF_HALD_DIR")?.let(::File)
        val lutDir = System.getenv("RF_LUT_DIR")?.let(::File)
        assumeTrue("Brak RF_HALD_DIR/RF_LUT_DIR – pomijam", haldDir?.isDirectory == true && lutDir?.isDirectory == true)
        val filter = System.getenv("RF_LUT_FILTER").orEmpty()
        val entries = ENTRY.findAll(File(lutDir, "catalog.json").readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { filter in it.first }
            .toList()
        // Ograniczona równoległość – HaldCLUT 144³ zajmuje dużo pamięci.
        val pool = java.util.concurrent.ForkJoinPool(2)
        val rows = pool.submit<List<Triple<String, Stats, Stats>>> {
            entries.parallelStream().map { (id, hald) ->
                val lut = Lut.fromBinary(File(lutDir, "$id.rgb8").readBytes())
                val truth = loadHald(File(haldDir, hald))
                Triple(
                    id,
                    evaluate(truth, lut, Interpolation.TRILINEAR),
                    evaluate(truth, lut, Interpolation.TETRAHEDRAL)
                )
            }.toList()
        }.get().sortedBy { it.first }
        pool.shutdown()
        val out = StringBuilder("| LUT | tri max | tri p99 | tri mean | tetra max | tetra p99 | tetra mean |\n")
        out.append("|---|---|---|---|---|---|---|\n")
        for ((id, t, q) in rows) out.append("| $id | ${t.fmt()} | ${q.fmt()} |\n")
        out.append("| **średnia** | ${avg(rows.map { it.second }).fmt()} | ${avg(rows.map { it.third }).fmt()} |\n")
        val tetraBetter = rows.count { it.third.p99 < it.second.p99 }
        out.append("\nTetra lepsze (p99) w $tetraBetter/${rows.size} LUT-ach.\n")
        val report = File("build/reports/lut-accuracy.md").apply { parentFile.mkdirs() }
        report.writeText(out.toString())
        println(out)
    }

    private class Stats(val max: Double, val p99: Double, val mean: Double) {
        fun fmt() = String.format(Locale.ROOT, "%.4f | %.4f | %.4f", max, p99, mean)
    }

    private fun avg(s: List<Stats>) = Stats(s.maxOf { it.max }, s.map { it.p99 }.average(), s.map { it.mean }.average())

    /** HaldCLUT jako tablica [b][g][r] RGB 0..1 (płaska), n = rozmiar siatki. */
    private class Hald(val n: Int, val rgb: FloatArray)

    private fun loadHald(f: File): Hald {
        val img = ImageIO.read(f)
        val w = img.width
        val total = w * img.height
        val n = Math.round(cbrt(total.toDouble())).toInt()
        val px = img.getRGB(0, 0, w, img.height, null, 0, w)
        val rgb = FloatArray(total * 3)
        for (i in 0 until total) {
            rgb[i * 3] = (px[i] shr 16 and 0xFF) / 255f
            rgb[i * 3 + 1] = (px[i] shr 8 and 0xFF) / 255f
            rgb[i * 3 + 2] = (px[i] and 0xFF) / 255f
        }
        return Hald(n, rgb)
    }

    private fun evaluate(h: Hald, lut: Lut, mode: Interpolation): Stats {
        val n = h.n
        val des = DoubleArray(n * n * n)
        val got = FloatArray(3)
        val max = (n - 1).toFloat()
        var i = 0
        for (b in 0 until n) {
            for (g in 0 until n) {
                for (r in 0 until n) {
                    CpuLut.sample(lut, r / max, g / max, b / max, got, mode)
                    des[i] = deltaE2000(
                        lab(h.rgb[i * 3], h.rgb[i * 3 + 1], h.rgb[i * 3 + 2]),
                        lab(got[0], got[1], got[2])
                    )
                    i++
                }
            }
        }
        des.sort()
        return Stats(des.last(), des[((des.size - 1) * 0.99).toInt()], des.average())
    }

    private fun lin(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun lab(r: Float, g: Float, b: Float): DoubleArray {
        val lr = lin(r.coerceIn(0f, 1f))
        val lg = lin(g.coerceIn(0f, 1f))
        val lb = lin(b.coerceIn(0f, 1f))
        val x = (0.4124564 * lr + 0.3575761 * lg + 0.1804375 * lb) / 0.95047
        val y = 0.2126729 * lr + 0.7151522 * lg + 0.0721750 * lb
        val z = (0.0193339 * lr + 0.1191920 * lg + 0.9503041 * lb) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    @Suppress("LongMethod")
    private fun deltaE2000(l1: DoubleArray, l2: DoubleArray): Double {
        val c1 = hypot(l1[1], l1[2])
        val c2 = hypot(l2[1], l2[2])
        val cb7 = ((c1 + c2) / 2).pow(7)
        val gg = 0.5 * (1 - sqrt(cb7 / (cb7 + P25)))
        val a1 = (1 + gg) * l1[1]
        val a2 = (1 + gg) * l2[1]
        val c1p = hypot(a1, l1[2])
        val c2p = hypot(a2, l2[2])
        val h1 = (Math.toDegrees(atan2(l1[2], a1)) + 360) % 360
        val h2 = (Math.toDegrees(atan2(l2[2], a2)) + 360) % 360
        val dl = l2[0] - l1[0]
        val dc = c2p - c1p
        var dh = h2 - h1
        if (dh > 180) {
            dh -= 360
        } else if (dh < -180) {
            dh += 360
        }
        if (c1p * c2p == 0.0) dh = 0.0
        val dH = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dh / 2))
        val lb = (l1[0] + l2[0]) / 2
        val cbp = (c1p + c2p) / 2
        val hs = h1 + h2
        val hb = when {
            c1p * c2p == 0.0 -> hs
            abs(h1 - h2) <= 180 -> hs / 2
            hs < 360 -> (hs + 360) / 2
            else -> (hs - 360) / 2
        }
        val t = 1 - 0.17 * cos(Math.toRadians(hb - 30)) + 0.24 * cos(Math.toRadians(2 * hb)) +
            0.32 * cos(Math.toRadians(3 * hb + 6)) - 0.20 * cos(Math.toRadians(4 * hb - 63))
        val dT = 30 * exp(-((hb - 275) / 25).pow(2))
        val cbp7 = cbp.pow(7)
        val rc = 2 * sqrt(cbp7 / (cbp7 + P25))
        val sl = 1 + 0.015 * (lb - 50).pow(2) / sqrt(20 + (lb - 50).pow(2))
        val sc = 1 + 0.045 * cbp
        val sh = 1 + 0.015 * cbp * t
        val rt = -sin(Math.toRadians(2 * dT)) * rc
        return sqrt((dl / sl).pow(2) + (dc / sc).pow(2) + (dH / sh).pow(2) + rt * (dc / sc) * (dH / sh))
    }

    private companion object {
        val P25 = 25.0.pow(7)
        val ENTRY = Regex("\"id\":\\s*\"([^\"]+)\"[^}]*?\"source_hald\":\\s*\"([^\"]+)\"")
    }
}
