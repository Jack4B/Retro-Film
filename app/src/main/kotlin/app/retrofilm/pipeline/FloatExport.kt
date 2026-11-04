package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import app.retrofilm.core.engine.CpuLut
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.Srgb
import app.retrofilm.perf.SaveTrace
import app.retrofilm.perf.SaveTrace.Companion.timed
import java.nio.IntBuffer

/** Przestrzeń barw pliku wyjściowego. */
enum class OutputSpace { Srgb, DisplayP3 }

/**
 * Eksport z LUT-em wewnątrz potoku float (zamiast osobnego przebiegu silnika na 8-bitowej bitmapie): korekty → LUT
 * (czworościenny, siła mieszana w świetle liniowym) → wykończenie, a między etapami ~10 bitów ([Frac]). Jedna
 * kwantyzacja z ditheringiem na samym końcu.
 *
 * Display P3: piksele są przez cały potok wartościami zakodowanymi w P3 (bitmapy robocze mają etykietę sRGB, więc
 * `getPixels` nie konwertuje), a plik dostaje profil P3. LUT-y są zdefiniowane w sRGB, więc etap filmu przelicza P3
 * → sRGB, próbkuje LUT w zakresie sRGB i dodaje z powrotem część spoza gamutu sRGB ([LutStage]). Korekty i
 * wykończenie działają na liczbach P3 – wygląd może minimalnie odbiegać od sRGB (nieco mocniejsze nasycenie).
 */
object FloatExport {
    /** Bitmapa robocza: dla sRGB – [ColorPolicy.toSrgb]; dla P3 – konwersja do P3 i surowa kopia z etykietą sRGB. */
    fun working(photo: Bitmap, space: OutputSpace): Bitmap {
        if (space == OutputSpace.Srgb) return ColorPolicy.toSrgb(photo)
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val converted = if (photo.colorSpace == p3 && photo.config == Bitmap.Config.ARGB_8888) {
            photo
        } else {
            Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888, photo.hasAlpha(), p3).also {
                Canvas(it).drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        val raw = IntArray(photo.width * photo.height)
        converted.copyPixelsToBuffer(IntBuffer.wrap(raw))
        if (converted !== photo) converted.recycle()
        val out = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        out.copyPixelsFromBuffer(IntBuffer.wrap(raw))
        return out
    }

    /** Wyrazistość korekt i podgląd maski wyostrzania wymagają osobnych przebiegów (inna analiza pasów). */
    private fun canFuse(first: Adjustments, finishing: Adjustments) = first.clarity == 0f && !finishing.sharpMaskPreview

    /**
     * Korekty + LUT + wykończenie na bitmapie roboczej ([working]); wynik nowa bitmapa (wejście bez zmian).
     * [fracOut] (PNG 16) = ułamki kanałów ostatniego etapu zamiast ditheringu (~10 bitów precyzji).
     */
    @Suppress("LongParameterList") // te same dane co ExportPipeline.render
    fun look(
        work: Bitmap,
        lut: Lut?,
        intensity: Float,
        adjustments: Adjustments,
        space: OutputSpace,
        faces: List<FaceBox> = emptyList(),
        fracOut: ByteArray? = null,
        trace: SaveTrace? = null,
        fused: Boolean = true
    ): Bitmap {
        val stage = lut?.let { LutStage(it, intensity, space == OutputSpace.DisplayP3) }
        val finishing = adjustments.finishing()
        // maski są mieszane na 8 bitach po pasach – wtedy korekty osobno (jak dotąd), LUT dalej w potoku float
        val masked = adjustments.corrections().masks.isNotEmpty()
        val input = if (masked) {
            trace.timed("masks") { AdjustmentsApplier.apply(work, adjustments.corrections(), lut != null) }
        } else {
            work
        }
        val first = if (masked) Adjustments.None else adjustments.corrections()
        val deband = lut != null && !masked
        val single = finishing.isNeutral || (fused && !masked && canFuse(first, finishing))
        if (single) {
            // G: jeden przebieg – bez pośredniej bitmapy i ułamków (−60 MB przy 12 MP, analiza i odczyt raz)
            val finish = finishing.takeUnless { it.isNeutral }
            val name = if (finish == null) "lut_render_dither" else "lut_effects_dither"
            return trace.timed(name) {
                val io = StripIo(fracOut = fracOut, stage = stage, finish = finish)
                runOrShrink(input, first, deband, if (finish == null) emptyList() else faces, io)
            }.also {
                if (input !== work) input.recycle()
            }
        }
        val frac = ByteArray(work.width * work.height)
        val filmed = trace.timed("lut_render") {
            StripPipeline.run(input, first, deband, emptyList(), StripIo(null, frac, stage, false))
        }
        if (input !== work) input.recycle()
        val finished = trace.timed("effects_dither") {
            StripPipeline.run(filmed, finishing, false, faces, StripIo(fracIn = frac, fracOut = fracOut))
        }
        filmed.recycle()
        return finished
    }
}

/** H: brak pamięci przy pełnym budżecie pasów → ponów najmniejszymi pasami (zwykle jeden wątek). */
private fun runOrShrink(input: Bitmap, adj: Adjustments, deband: Boolean, faces: List<FaceBox>, io: StripIo): Bitmap =
    try {
        StripPipeline.run(input, adj, deband, faces, io)
    } catch (_: OutOfMemoryError) {
        StripPipeline.run(input, adj, deband, faces, io.copyWith(StripBudget.MIN_BYTES))
    }

/**
 * LUT na kanałach float (0..255) w pasie. [wide] = wartości w Display P3: próbkowanie w sRGB po przycięciu do jego
 * gamutu, a różnica (część poza sRGB) wraca do wyniku w świetle liniowym.
 */
internal class LutStage(private val lut: Lut, intensity: Float, private val wide: Boolean) : PlaneStage {
    private val t = intensity.coerceIn(0f, 1f)

    override fun apply(planes: Planes, from: Int, count: Int) {
        val rgb = FloatArray(Lut.CHANNELS)
        val lin = FloatArray(Lut.CHANNELS)
        for (i in from until from + count) {
            rgb[0] = planes.r[i] / MAX
            rgb[1] = planes.g[i] / MAX
            rgb[2] = planes.b[i] / MAX
            if (wide) wideSample(rgb, lin) else grade(rgb)
            planes.r[i] = rgb[0].coerceIn(0f, 1f) * MAX
            planes.g[i] = rgb[1].coerceIn(0f, 1f) * MAX
            planes.b[i] = rgb[2].coerceIn(0f, 1f) * MAX
        }
    }

    /** sRGB 0..1 → wynik filmu z siłą [t] (w miejscu). */
    private fun grade(rgb: FloatArray) {
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]
        CpuLut.sampleTetrahedral(lut, r, g, b, rgb)
        rgb[0] = CpuLut.mixLinear(r, rgb[0], t)
        rgb[1] = CpuLut.mixLinear(g, rgb[1], t)
        rgb[2] = CpuLut.mixLinear(b, rgb[2], t)
    }

    private fun wideSample(rgb: FloatArray, lin: FloatArray) {
        for (c in 0 until Lut.CHANNELS) lin[c] = Srgb.toLinear(rgb[c].coerceIn(0f, 1f))
        Gamut.mul(Gamut.P3_TO_SRGB, lin)
        val excess = FloatArray(Lut.CHANNELS)
        for (c in 0 until Lut.CHANNELS) {
            val inside = lin[c].coerceIn(0f, 1f)
            excess[c] = lin[c] - inside
            rgb[c] = Srgb.fromLinear(inside)
        }
        grade(rgb)
        for (c in 0 until Lut.CHANNELS) lin[c] = Srgb.toLinear(rgb[c].coerceIn(0f, 1f)) + excess[c]
        Gamut.mul(Gamut.SRGB_TO_P3, lin)
        for (c in 0 until Lut.CHANNELS) rgb[c] = Srgb.fromLinear(lin[c].coerceIn(0f, 1f))
    }

    private companion object {
        const val MAX = 255f
    }
}

/** Macierze liniowe D65 między sRGB a Display P3. */
internal object Gamut {
    val P3_TO_SRGB = floatArrayOf(
        1.2249401f, -0.2249404f, 0f,
        -0.0420569f, 1.0420571f, 0f,
        -0.0196376f, -0.0786361f, 1.0982735f
    )
    val SRGB_TO_P3 = floatArrayOf(
        0.8224621f, 0.1775380f, 0f,
        0.0331941f, 0.9668058f, 0f,
        0.0170827f, 0.0723974f, 0.9105199f
    )

    private const val N = 3

    fun mul(m: FloatArray, v: FloatArray) {
        val r = v[0]
        val g = v[1]
        val b = v[2]
        for (row in 0 until N) v[row] = m[row * N] * r + m[row * N + 1] * g + m[row * N + 2] * b
    }
}
