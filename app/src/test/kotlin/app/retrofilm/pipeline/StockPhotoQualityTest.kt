package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Raport jakości algorytmów na zestawie zdjęć (stockowych, pobieranych tylko na czas testu; nie są częścią repo).
 * Ścieżka katalogu ze zdjęciami: zmienna STOCK_PHOTOS, wyniki (PNG + report.md): QUALITY_OUT.
 * Bez STOCK_PHOTOS test jest pomijany. Metryki liczone na danych z znaną prawdą (sztuczna mgła i szum).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StockPhotoQualityTest {
    private val report = StringBuilder()
    private val failures = mutableListOf<String>()

    @Test fun qualityOnStockPhotos() {
        val dir = System.getenv("STOCK_PHOTOS")?.let(::File)
        assumeTrue("brak STOCK_PHOTOS", dir != null && dir.isDirectory)
        val out = System.getenv("QUALITY_OUT")?.let(::File)?.also { it.mkdirs() }
        report.appendLine("# Raport jakości algorytmów\n")
        val files = dir!!.listFiles { f -> f.extension.lowercase() == "jpg" }.orEmpty().sortedBy { it.name }
        assertTrue("brak zdjęć", files.isNotEmpty())
        for (f in files) photoReport(f, out)
        performance()
        report.appendLine("\n## Niezgodności\n")
        if (failures.isEmpty()) report.appendLine("Brak.") else failures.forEach { report.appendLine("- $it") }
        out?.resolve("report.md")?.writeText(report.toString())
        println(report)
        assertTrue("Niezgodności: $failures", failures.isEmpty())
    }

    private fun photoReport(file: File, out: File?) {
        val src = load(file)
        val name = file.nameWithoutExtension
        report.appendLine("## $name (${src.width}×${src.height})\n")
        val px = pixels(src)
        val tiles = mutableListOf(src)

        val shadows = AdjustmentsApplier.apply(src, Adjustments(shadows = 1f))
        val highlights = AdjustmentsApplier.apply(src, Adjustments(highlights = -1f))
        val clarity = AdjustmentsApplier.apply(src, Adjustments(clarity = 0.8f))
        tiles += listOf(shadows, highlights, clarity)
        checkTone(name, px, pixels(shadows), pixels(highlights))
        checkBrightRegion(name, px, pixels(highlights), src.width, src.height)
        checkDarkRegion(name, px, pixels(shadows), src.width, src.height)
        checkClarity(name, px, pixels(clarity), src.width, src.height)
        reportHalo(px, pixels(AdjustmentsApplier.apply(src, Adjustments(sharpness = 1f))), src.width, src.height)

        val (clean, hazy) = synthHaze(px, src.width, src.height)
        val dehazed = hazy.copyOf().also { Dehaze.apply(it, src.width, src.height, 1f) }
        val psnrHazy = psnr(clean, hazy)
        val psnrDehazed = psnr(clean, dehazed)
        report.appendLine("- Dehaze: PSNR zamglonego ${f1(psnrHazy)} dB → po dehaze ${f1(psnrDehazed)} dB")
        if (psnrDehazed <=
            psnrHazy
        ) {
            failures += "$name: dehaze nie poprawia PSNR (${f1(psnrHazy)} → ${f1(psnrDehazed)})"
        }
        tiles += listOf(bitmap(hazy, src.width, src.height), bitmap(dehazed, src.width, src.height))

        val noisy = addColourNoise(px)
        val denoised = noisy.copyOf().also { ChromaDenoise.apply(it, src.width, src.height, 1f) }
        val chromaBefore = chromaError(px, noisy)
        val chromaAfter = chromaError(px, denoised)
        val lumaDrift = lumaError(noisy, denoised)
        report.appendLine(
            "- Odszumianie: błąd chrominancji ${f1(chromaBefore)} → ${f1(chromaAfter)}, dryf jasności ${f1(lumaDrift)}"
        )
        if (chromaAfter >=
            chromaBefore * CHROMA_GAIN
        ) {
            failures += "$name: odszumianie nie zmniejsza błędu koloru dość mocno"
        }
        if (lumaDrift > MAX_LUMA_DRIFT) failures += "$name: odszumianie zmienia jasność (${f1(lumaDrift)})"
        tiles += listOf(bitmap(noisy, src.width, src.height), bitmap(denoised, src.width, src.height))

        val saturated = AdjustmentsApplier.apply(src, Adjustments(saturation = 0.6f))
        val warm = AdjustmentsApplier.apply(src, Adjustments(temperature = 0.4f))
        val look = AdjustmentsApplier.apply(
            src,
            Adjustments(contrast = 0.3f, saturation = 0.2f, fade = 0.3f, grain = 0.4f, vignette = 0.4f, clarity = 0.3f)
        )
        tiles += listOf(saturated, warm, look)
        checkColour(name, src, saturated, warm, look)
        out?.let { sheet(tiles, it.resolve("$name.png")) }
        report.appendLine()
    }

    private fun checkTone(name: String, orig: IntArray, shadows: IntArray, highlights: IntArray) {
        val order = orig.indices.sortedBy { luma(orig[it]) }
        val q = order.size / 4
        val dark = order.take(q)
        val bright = order.takeLast(q)
        val gainDark = dark.map { luma(shadows[it]) - luma(orig[it]) }.average()
        // prawdziwe światła (jasność ≥ 65%); na ciemnych zdjęciach jasna ćwiartka to jeszcze półtony
        val lit = orig.indices.filter { luma(orig[it]) >= REAL_HIGHLIGHT }
        val litEnough = lit.size >= orig.size / 100
        val driftBright = if (litEnough) lit.map { luma(shadows[it]) - luma(orig[it]) }.average() else 0.0
        val cutBright = bright.map { luma(orig[it]) - luma(highlights[it]) }.average()
        // prawdziwe cienie (jasność ≤ 35%); na pochmurnych, jasnych zdjęciach ciemna ćwiartka jest już półtonem
        val deep = orig.indices.filter { luma(orig[it]) <= DEEP_SHADOW }
        val deepEnough = deep.size >= orig.size / 100
        val driftDark = if (deepEnough) deep.map { luma(highlights[it]) - luma(orig[it]) }.average() else 0.0
        val darkIsDeep = dark.map { luma(orig[it]) }.average() <= DEEP_SHADOW
        val hue = hueShift(orig, shadows)
        report.appendLine(
            "- Cienie +100%: ciemna ćwiartka +${f1(gainDark)}, prawdziwe światła ${f1(driftBright)}; " +
                "światła −100%: jasna ćwiartka −${f1(cutBright)}, prawdziwe cienie ${f1(driftDark)}" +
                (if (deepEnough) "" else " (brak głębokich cieni)") + "; przesunięcie barwy ${f1(hue)}°"
        )
        if (darkIsDeep && gainDark < MIN_TONE_GAIN) failures += "$name: cienie za słabo rozjaśniają (${f1(gainDark)})"
        if (abs(driftBright) > MAX_TONE_LEAK) failures += "$name: cienie ruszają jasne partie (${f1(driftBright)})"
        if (cutBright < MIN_TONE_GAIN / 2) failures += "$name: światła za słabo przyciemniają (${f1(cutBright)})"
        if (abs(driftDark) > MAX_TONE_LEAK) failures += "$name: światła ruszają ciemne partie (${f1(driftDark)})"
        if (hue > MAX_HUE_SHIFT) failures += "$name: cienie przesuwają barwy o ${f1(hue)}°"
    }

    /**
     * Jasny obszar (górna ćwiartka jasności, np. pochmurne niebo) po światłach −100%: lokalny kontrast i barwność
     * mają się zachować (stosunek ≥ 0,9), bo samo przyciemnienie robi z nieba płaską szarość.
     */
    private fun checkBrightRegion(name: String, orig: IntArray, highlights: IntArray, w: Int, h: Int) {
        val bright = orig.indices.sortedBy { luma(orig[it]) }.takeLast(orig.size / 4)
        val mask = BooleanArray(orig.size).also { m -> bright.forEach { m[it] = true } }
        val c0 = localContrast(orig, mask, w, h)
        val c1 = localContrast(highlights, mask, w, h)
        val k0 = bright.map { chromaOf(orig[it]) }.average()
        val k1 = bright.map { chromaOf(highlights[it]) }.average()
        val contrastRatio = if (c0 > 0.0) c1 / c0 else 1.0
        val chromaRatio = if (k0 > 0.0) k1 / k0 else 1.0
        report.appendLine(
            "- Jasny obszar po światłach −100%: lokalny kontrast ${f1(c0)} → ${f1(c1)} (×${f2(contrastRatio)}), " +
                "barwność ${f1(k0)} → ${f1(k1)} (×${f2(chromaRatio)})"
        )
        if (contrastRatio <
            MIN_BRIGHT_RATIO
        ) {
            failures += "$name: światła spłaszczają jasny obszar (×${f2(contrastRatio)})"
        }
        if (chromaRatio < MIN_BRIGHT_RATIO) failures += "$name: światła szarzeją jasny obszar (×${f2(chromaRatio)})"
    }

    /**
     * Ciemny obszar (dolna ćwiartka jasności) po cieniach +100%: faktura nie może się spłaszczyć (kontrast lokalny
     * ≥ 0,9), szum nie może wybuchnąć (drobny detal najwyżej 3×), a barwność ma nie spaść.
     */
    private fun checkDarkRegion(name: String, orig: IntArray, shadows: IntArray, w: Int, h: Int) {
        val dark = orig.indices.sortedBy { luma(orig[it]) }.take(orig.size / 4)
        val mask = BooleanArray(orig.size).also { m -> dark.forEach { m[it] = true } }
        val c0 = localContrast(orig, mask, w, h)
        val c1 = localContrast(shadows, mask, w, h)
        val n0 = fineDetail(orig, mask, w, h)
        val n1 = fineDetail(shadows, mask, w, h)
        val k0 = dark.map { chromaOf(orig[it]) }.average()
        val k1 = dark.map { chromaOf(shadows[it]) }.average()
        val contrastRatio = if (c0 > 0.0) c1 / c0 else 1.0
        val noiseRatio = if (n0 > 0.0) n1 / n0 else 1.0
        val chromaRatio = if (k0 > 0.0) k1 / k0 else 1.0
        report.appendLine(
            "- Ciemny obszar po cieniach +100%: lokalny kontrast ${f1(c0)} → ${f1(c1)} (×${f2(contrastRatio)}), " +
                "drobny detal/szum ×${f2(noiseRatio)}, barwność ${f1(k0)} → ${f1(k1)} (×${f2(chromaRatio)})"
        )
        if (contrastRatio <
            MIN_BRIGHT_RATIO
        ) {
            failures += "$name: cienie spłaszczają ciemny obszar (×${f2(contrastRatio)})"
        }
        if (noiseRatio > MAX_NOISE_RATIO) failures += "$name: cienie wzmacniają szum (×${f2(noiseRatio)})"
        if (chromaRatio < MIN_BRIGHT_RATIO) failures += "$name: cienie odbarwiają ciemny obszar (×${f2(chromaRatio)})"
    }

    /** Energia drobnego detalu (laplasjan 3×3) w [mask]; przy rozjaśnianiu cieni rośnie głównie szum. */
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

    private fun chromaOf(c: Int): Double {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return (max(r, max(g, b)) - min(r, min(g, b))).toDouble()
    }

    /** Średnia różnica jasności piksela od czterech sąsiadów oddalonych o [LOCAL_STEP] px, tylko w [mask]. */
    private fun localContrast(px: IntArray, mask: BooleanArray, w: Int, h: Int): Double {
        var sum = 0.0
        var n = 0
        for (y in LOCAL_STEP until h - LOCAL_STEP) {
            for (x in LOCAL_STEP until w - LOCAL_STEP) {
                val i = y * w + x
                if (!mask[i]) continue
                val mean = (
                    luma(px[i - LOCAL_STEP]) + luma(px[i + LOCAL_STEP]) +
                        luma(px[i - LOCAL_STEP * w]) + luma(px[i + LOCAL_STEP * w])
                    ) / 4.0
                sum += abs(luma(px[i]) - mean)
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun checkClarity(name: String, orig: IntArray, clarity: IntArray, w: Int, h: Int) {
        val meanShift = abs(orig.map { luma(it) }.average() - clarity.map { luma(it) }.average())
        val before = laplacianEnergy(orig, w, h)
        val after = laplacianEnergy(clarity, w, h)
        report.appendLine(
            "- Wyrazistość 80%: energia detalu ${f1(before)} → ${f1(after)}, zmiana średniej jasności ${f1(meanShift)}"
        )
        if (after <= before) failures += "$name: wyrazistość nie zwiększa lokalnego kontrastu"
        if (meanShift > MAX_MEAN_SHIFT) failures += "$name: wyrazistość przesuwa jasność o ${f1(meanShift)}"
    }

    /**
     * Aureole wyostrzania: o ile jasność po wyostrzaniu wychodzi poza zakres jasności oryginału w sąsiedztwie 5×5
     * (przestrzelenie na krawędziach). Na razie tylko do raportu: średnia i odsetek pikseli z przestrzeleniem > 12.
     */
    private fun reportHalo(orig: IntArray, sharp: IntArray, w: Int, h: Int) {
        var sum = 0.0
        var strong = 0
        var n = 0
        for (y in HALO_R until h - HALO_R step HALO_STEP) {
            for (x in HALO_R until w - HALO_R step HALO_STEP) {
                val (lo, hi) = neighbourhoodRange(orig, w, x, y)
                val v = luma(sharp[y * w + x])
                val over = maxOf(0.0, v - hi) + maxOf(0.0, lo - v)
                sum += over
                if (over > HALO_STRONG) strong++
                n++
            }
        }
        report.appendLine(
            "- Ostrość 100%: przestrzelenie na krawędziach średnio ${f2(sum / n)}, > ${HALO_STRONG.toInt()} w ${
                f1(100.0 * strong / n)
            }% pikseli"
        )
    }

    private fun neighbourhoodRange(px: IntArray, w: Int, x: Int, y: Int): Pair<Double, Double> {
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (dy in -HALO_R..HALO_R) {
            for (dx in -HALO_R..HALO_R) {
                val l = luma(px[(y + dy) * w + x + dx])
                lo = minOf(lo, l)
                hi = maxOf(hi, l)
            }
        }
        return lo to hi
    }

    private fun checkColour(name: String, src: Bitmap, saturated: Bitmap, warm: Bitmap, look: Bitmap) {
        val c0 = colourfulness(pixels(src))
        val cSat = colourfulness(pixels(saturated))
        val cLook = colourfulness(pixels(look))
        val warmShift = meanRedMinusBlue(pixels(warm)) - meanRedMinusBlue(pixels(src))
        report.appendLine(
            "- Kolor: barwność ${f1(
                c0
            )} → nasycenie +60% ${f1(cSat)}, zestaw „look” ${f1(cLook)}; ciepło +40%: R−B +${f1(warmShift)}"
        )
        if (cSat <= c0) failures += "$name: nasycenie nie zwiększa barwności"
        if (cLook <
            c0 * MIN_LOOK_COLOUR
        ) {
            failures += "$name: zestaw look robi zdjęcie szarym (${f1(cLook)} vs ${f1(c0)})"
        }
        if (warmShift <= 0) failures += "$name: ciepło nie przesuwa balansu w stronę czerwieni"
    }

    private fun performance() {
        report.appendLine(
            "## Wydajność (runner CI, 12 Mpx = 4000×3000, ${Runtime.getRuntime().availableProcessors()} rdzenie, " +
                "wątki robocze: ${Parallel.workers}, mediana z $PERF_RUNS po rozgrzewce)\n"
        )
        val w = 4000
        val h = 3000
        try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val rnd = Random(1)
            val row = IntArray(w)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = (x * 255 / w + rnd.nextInt(RANDOM_SPAN)).coerceIn(0, 255)
                    row[x] = Color.rgb(v, (v + y / 20).coerceIn(0, 255), 255 - v)
                }
                bmp.setPixels(row, 0, w, 0, y, w, 1)
            }
            val cases = mapOf(
                "korekty podstawowe" to Adjustments(exposure = 0.3f, contrast = 0.2f, saturation = 0.2f),
                "cienie i światła" to Adjustments(shadows = 0.6f, highlights = -0.4f),
                "wyrazistość + ostrość" to Adjustments(clarity = 0.5f, sharpness = 0.4f),
                "dehaze" to Adjustments(dehaze = 0.6f),
                "odszumianie kolorów" to Adjustments(denoise = 0.6f),
                "wszystko naraz" to Adjustments(
                    shadows = 0.4f,
                    clarity = 0.4f,
                    sharpness = 0.3f,
                    dehaze = 0.4f,
                    denoise = 0.4f,
                    grain = 0.3f
                )
            )
            AdjustmentsApplier.apply(bmp, cases.getValue("wszystko naraz")) // rozgrzewka JIT
            for ((label, adj) in cases) {
                val times = List(PERF_RUNS) {
                    val t0 = System.nanoTime()
                    AdjustmentsApplier.apply(bmp, adj)
                    (System.nanoTime() - t0) / NANOS_PER_MS
                }.sorted()
                report.appendLine("- $label: ${times[PERF_RUNS / 2]} ms")
            }
            stageBreakdown(bmp)
        } catch (e: OutOfMemoryError) {
            report.appendLine("- BRAK PAMIĘCI przy 12 Mpx: ${e.message}")
            failures += "12 Mpx: OutOfMemoryError (pamięć na telefonie będzie jeszcze ciaśniejsza)"
        }
    }

    /** Rozbicie czasu etapów „wszystko naraz” (pojedynczo, na kopii pikseli): pokazuje, co jeszcze jest sekwencyjne. */
    private fun stageBreakdown(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val stages = linkedMapOf<String, () -> Unit>(
            "kopia pikseli" to { px.copyOf() },
            "odszumianie kolorów" to { ChromaDenoise.apply(px.copyOf(), w, h, 0.4f) },
            "dehaze" to { Dehaze.apply(px.copyOf(), w, h, 0.4f) },
            "wyostrzanie + wyrazistość" to { DetailFilter.apply(px.copyOf(), w, h, 0.3f, 0.4f) },
            "przebieg pikseli (cienie, ziarno)" to {
                AdjustmentsApplier.apply(bmp, Adjustments(shadows = 0.4f, grain = 0.3f))
            }
        )
        report.appendLine("\nRozbicie „wszystko naraz” na etapy:")
        for ((label, block) in stages) {
            val times = List(PERF_RUNS) {
                val t0 = System.nanoTime()
                block()
                (System.nanoTime() - t0) / NANOS_PER_MS
            }.sorted()
            report.appendLine("- $label: ${times[PERF_RUNS / 2]} ms")
        }
    }

    // --- narzędzia ---

    private fun load(f: File): Bitmap {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = requireNotNull(BitmapFactory.decodeFile(f.path, opts)) { "nie wczytano ${f.name}" }
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        return if (scale >= 1f) {
            bmp
        } else {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        }
    }

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also {
        b.getPixels(it, 0, b.width, 0, 0, b.width, b.height)
    }

    private fun bitmap(px: IntArray, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, w, 0, 0, w, h) }

    private fun sheet(tiles: List<Bitmap>, file: File) {
        val cols = SHEET_COLS
        val tw = TILE_W
        val th = tiles[0].height * tw / tiles[0].width
        val rows = (tiles.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(cols * tw, rows * th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        tiles.forEachIndexed { i, t ->
            val s = Bitmap.createScaledBitmap(t, tw, th, true)
            canvas.drawBitmap(s, ((i % cols) * tw).toFloat(), ((i / cols) * th).toFloat(), null)
        }
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun synthHaze(px: IntArray, w: Int, h: Int): Pair<IntArray, IntArray> {
        val air = intArrayOf(AIR_R, AIR_G, AIR_B)
        val hazy = IntArray(px.size) { i ->
            val y = i / w
            val t = T_FAR + (T_NEAR - T_FAR) * y / h
            val c = px[i]
            Color.rgb(mix(Color.red(c), air[0], t), mix(Color.green(c), air[1], t), mix(Color.blue(c), air[2], t))
        }
        return px to hazy
    }

    private fun mix(j: Int, a: Int, t: Float): Int = (j * t + a * (1 - t)).toInt().coerceIn(0, 255)

    private fun addColourNoise(px: IntArray): IntArray {
        val rnd = Random(SEED)
        return IntArray(px.size) { i ->
            val c = px[i]
            val n = rnd.nextGaussian().toFloat() * NOISE_SIGMA
            val m = rnd.nextGaussian().toFloat() * NOISE_SIGMA
            Color.rgb(
                (Color.red(c) + n).toInt().coerceIn(0, 255),
                (Color.green(c) - (n + m) / 2).toInt().coerceIn(0, 255),
                (Color.blue(c) + m).toInt().coerceIn(0, 255)
            )
        }
    }

    private fun psnr(a: IntArray, b: IntArray): Double {
        var se = 0.0
        for (i in a.indices) {
            se += sq(Color.red(a[i]) - Color.red(b[i])) + sq(Color.green(a[i]) - Color.green(b[i])) +
                sq(Color.blue(a[i]) - Color.blue(b[i]))
        }
        val mse = se / (a.size * 3)
        return if (mse == 0.0) 99.0 else 10 * log10(255.0 * 255.0 / mse)
    }

    private fun sq(v: Int) = (v * v).toDouble()

    private fun luma(c: Int): Double = 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)

    private fun chromaError(ref: IntArray, test: IntArray): Double =
        ref.indices.sumOf { abs(cb(ref[it]) - cb(test[it])) + abs(cr(ref[it]) - cr(test[it])) } / ref.size

    private fun cb(c: Int) = Color.blue(c) - luma(c)

    private fun cr(c: Int) = Color.red(c) - luma(c)

    private fun lumaError(a: IntArray, b: IntArray): Double =
        a.indices.sumOf { abs(luma(a[it]) - luma(b[it])) } / a.size

    private fun hueShift(a: IntArray, b: IntArray): Double {
        val ha = FloatArray(3)
        val hb = FloatArray(3)
        var sum = 0.0
        var n = 0
        for (i in a.indices) {
            Color.colorToHSV(a[i], ha)
            Color.colorToHSV(b[i], hb)
            if (ha[1] > MIN_SAT && ha[2] > MIN_VAL && hb[1] > MIN_SAT) {
                val d = abs(ha[0] - hb[0])
                sum += min(d, 360f - d)
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun laplacianEnergy(px: IntArray, w: Int, h: Int): Double {
        var sum = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                sum += abs(4 * luma(px[i]) - luma(px[i - 1]) - luma(px[i + 1]) - luma(px[i - w]) - luma(px[i + w]))
            }
        }
        return sum / (w * h)
    }

    private fun colourfulness(px: IntArray): Double {
        val rg = px.map { (Color.red(it) - Color.green(it)).toDouble() }
        val yb = px.map { (0.5 * (Color.red(it) + Color.green(it)) - Color.blue(it)) }
        return sqrt(sq(rg) + sq(yb)) + MEAN_WEIGHT * sqrt(rg.average() * rg.average() + yb.average() * yb.average())
    }

    private fun sq(v: List<Double>): Double {
        val m = v.average()
        return v.sumOf { (it - m) * (it - m) } / v.size
    }

    private fun meanRedMinusBlue(px: IntArray): Double =
        px.sumOf { (Color.red(it) - Color.blue(it)).toDouble() } / px.size

    private fun f1(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)

    private fun f2(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)

    private companion object {
        const val MAX_SIDE = 1600
        const val SEED = 42L
        const val NOISE_SIGMA = 10f
        const val AIR_R = 232
        const val AIR_G = 236
        const val AIR_B = 242
        const val T_FAR = 0.4f
        const val T_NEAR = 0.9f
        const val CHROMA_GAIN = 0.7
        const val MAX_LUMA_DRIFT = 4.0
        const val MIN_TONE_GAIN = 5.0
        const val MIN_BRIGHT_RATIO = 0.9
        const val DEEP_SHADOW = 89.0
        const val MAX_NOISE_RATIO = 3.0
        const val LOCAL_STEP = 3
        const val MAX_TONE_LEAK = 10.0
        const val REAL_HIGHLIGHT = 165.0
        const val PERF_RUNS = 3
        const val HALO_R = 2
        const val HALO_STEP = 2
        const val HALO_STRONG = 12.0
        const val MAX_HUE_SHIFT = 4.0
        const val MAX_MEAN_SHIFT = 5.0
        const val MIN_LOOK_COLOUR = 0.6
        const val MIN_SAT = 0.25f
        const val MIN_VAL = 0.2f
        const val MEAN_WEIGHT = 0.3
        const val SHEET_COLS = 4
        const val TILE_W = 320
        const val RANDOM_SPAN = 24
        const val NANOS_PER_MS = 1_000_000L
    }
}
