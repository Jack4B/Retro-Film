package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Straż regresji suwaków (B6): każdy suwak w skrajnych położeniach na zdjęciach stockowych (STOCK_PHOTOS, wyniki w
 * QUALITY_OUT/sliders.md; bez STOCK_PHOTOS test jest pomijany). Mierzy: kontrast lokalny i barwność względem
 * oryginału, przyrost przyciętych pikseli, przestrzelenie na krawędziach (halo) i szum w ciemnej ćwiartce. Progi dla
 * suwaków „odzyskujących” (światła −, cienie +, biele −, czernie +): przycięcia ≤ 0,5%, halo ≤ 0,3%.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SliderRegressionTest {
    private class Case(val name: String, val adj: Adjustments, val guarded: Boolean)

    private val cases = listOf(
        Case("światła −100%", Adjustments(highlights = -1f), true),
        Case("cienie +100%", Adjustments(shadows = 1f), true),
        Case("biele −100%", Adjustments(whites = -1f), true),
        Case("czernie +100%", Adjustments(blacks = 1f), true),
        Case("tekstura +100%", Adjustments(texture = 1f), false),
        Case("tekstura −100%", Adjustments(texture = -1f), false),
        Case("wyrazistość +80%", Adjustments(clarity = 0.8f), false),
        Case("kontrast −100%", Adjustments(contrast = -1f), false),
        Case("kontrast +100%", Adjustments(contrast = 1f), false),
        Case("nasycenie +100%", Adjustments(saturation = 1f), false),
        Case("ostrość koloru 100%", Adjustments(chromaEdges = 1f), false),
        Case("odszumianie jasności 100%", Adjustments(lumaDenoise = 1f), false),
        Case("ostrość 100%", Adjustments(sharpness = 1f), false)
    )

    private val report = StringBuilder()
    private val failures = mutableListOf<String>()

    @Test fun slidersStayWithinGuards() {
        val dir = System.getenv("STOCK_PHOTOS")?.let(::File)
        assumeTrue("brak STOCK_PHOTOS", dir != null && dir.isDirectory)
        val out = System.getenv("QUALITY_OUT")?.let(::File)?.also { it.mkdirs() }
        report.appendLine("# Straż regresji suwaków\n")
        val files = dir!!.listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }
        for (f in files) photo(f)
        report.appendLine("\n## Niezgodności\n")
        if (failures.isEmpty()) report.appendLine("Brak.") else failures.forEach { report.appendLine("- $it") }
        out?.resolve("sliders.md")?.writeText(report.toString())
        println(report)
        assertTrue("Niezgodności: $failures", failures.isEmpty())
    }

    private fun photo(file: File) {
        val src = load(file)
        val w = src.width
        val h = src.height
        val base = pixels(src)
        val dark = darkQuarter(base)
        report.appendLine("## ${file.nameWithoutExtension} ($w×$h)\n")
        report.appendLine("| suwak | kontrast | barwność | przycięcia | halo | szum w cieniach |")
        report.appendLine("|---|---|---|---|---|---|")
        val baseContrast = localContrast(base, w, h)
        val baseChroma = chroma(base)
        val baseClip = clipped(base)
        val baseNoise = fineDetail(base, dark, w, h)
        for (c in cases) {
            val px = pixels(AdjustmentsApplier.apply(src, c.adj))
            val lc = localContrast(px, w, h) / baseContrast
            val ch = chroma(px) / baseChroma
            val clip = clipped(px) - baseClip
            val halo = haloShare(base, px, w, h)
            val noise = fineDetail(px, dark, w, h) / max(baseNoise, 1e-6)
            report.appendLine("| ${c.name} | ×${f2(lc)} | ×${f2(ch)} | ${f2(clip)}% | ${f2(halo)}% | ×${f2(noise)} |")
            if (c.guarded) {
                if (clip > MAX_CLIP) failures += "${file.nameWithoutExtension}: ${c.name}: przycięcia +${f2(clip)}%"
                if (halo > MAX_HALO) failures += "${file.nameWithoutExtension}: ${c.name}: halo ${f2(halo)}%"
            }
        }
        report.appendLine()
    }

    private fun load(f: File): Bitmap {
        val bmp = requireNotNull(BitmapFactory.decodeFile(f.path)) { "nie wczytano ${f.name}" }
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        if (scale >= 1f) return bmp
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also {
        b.getPixels(it, 0, b.width, 0, 0, b.width, b.height)
    }

    private fun luma(c: Int) = 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)

    private fun darkQuarter(px: IntArray): BooleanArray {
        val order = px.indices.sortedBy { luma(px[it]) }
        return BooleanArray(px.size).also { m -> order.take(px.size / 4).forEach { m[it] = true } }
    }

    private fun localContrast(px: IntArray, w: Int, h: Int): Double {
        var sum = 0.0
        var n = 0
        for (y in STEP until h - STEP) {
            for (x in STEP until w - STEP) {
                val i = y * w + x
                val mean = (
                    luma(px[i - STEP]) + luma(px[i + STEP]) + luma(px[i - STEP * w]) + luma(px[i + STEP * w])
                    ) / 4.0
                sum += abs(luma(px[i]) - mean)
                n++
            }
        }
        return sum / n
    }

    private fun chroma(px: IntArray): Double = px.sumOf {
        val hi = max(Color.red(it), max(Color.green(it), Color.blue(it)))
        val lo = min(Color.red(it), min(Color.green(it), Color.blue(it)))
        (hi - lo).toDouble()
    } / px.size

    private fun clipped(px: IntArray): Double = 100.0 * px.count {
        val r = Color.red(it)
        val g = Color.green(it)
        val b = Color.blue(it)
        min(r, min(g, b)) == 0 || max(r, max(g, b)) == 255
    } / px.size

    private fun fineDetail(px: IntArray, mask: BooleanArray, w: Int, h: Int): Double {
        var sum = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (!mask[i]) continue
                sum += abs(4 * luma(px[i]) - luma(px[i - 1]) - luma(px[i + 1]) - luma(px[i - w]) - luma(px[i + w]))
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    /**
     * Udział próbek, w których wynik wychodzi o ponad [HALO_STRONG] poziomów poza zakres jasności oryginału 5×5, po
     * odjęciu lokalnego przesunięcia tonalnego (średnia wyniku − średnia oryginału w oknie). Zwykłe przyciemnienie
     * czy rozjaśnienie nie jest halo; liczy się przestrzelenie na krawędziach.
     */
    private fun haloShare(ref: IntArray, out: IntArray, w: Int, h: Int): Double {
        var strong = 0
        var n = 0
        for (y in HALO_R until h - HALO_R step HALO_STEP) {
            for (x in HALO_R until w - HALO_R step HALO_STEP) {
                val (lo, hi, meanRef) = range(ref, w, x, y)
                val shift = range(out, w, x, y).third - meanRef
                val v = luma(out[y * w + x])
                if (max(0.0, v - (hi + shift)) + max(0.0, lo + shift - v) > HALO_STRONG) strong++
                n++
            }
        }
        return 100.0 * strong / n
    }

    /** Minimum, maksimum i średnia jasności w oknie 5×5 wokół ([x], [y]). */
    private fun range(px: IntArray, w: Int, x: Int, y: Int): Triple<Double, Double, Double> {
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var sum = 0.0
        for (dy in -HALO_R..HALO_R) {
            for (dx in -HALO_R..HALO_R) {
                val l = luma(px[(y + dy) * w + x + dx])
                lo = min(lo, l)
                hi = max(hi, l)
                sum += l
            }
        }
        return Triple(lo, hi, sum / WINDOW)
    }

    private fun f2(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)

    private companion object {
        const val MAX_SIDE = 1000
        const val STEP = 3
        const val MAX_CLIP = 0.5
        const val MAX_HALO = 0.3
        const val HALO_R = 2
        const val HALO_STEP = 2
        const val HALO_STRONG = 12.0
        const val WINDOW = 25.0
    }
}
