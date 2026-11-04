package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Gainmap
import android.graphics.Typeface
import android.os.Build
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutEngine
import app.retrofilm.core.export.ChromaSubsampling
import app.retrofilm.core.export.ExifData
import app.retrofilm.core.export.ExifWriter
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.JpegOptions
import app.retrofilm.perf.SaveTrace
import app.retrofilm.perf.SaveTrace.Companion.timed
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.ExportSource
import app.retrofilm.pipeline.FileType
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.FrameRenderer
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import app.retrofilm.pipeline.HeicExport
import app.retrofilm.pipeline.OutputSpace
import app.retrofilm.pipeline.Png16Export
import app.retrofilm.pipeline.Resampler
import app.retrofilm.pipeline.UltraHdrExport
import java.io.InputStream

/** Ustawienia pliku wyjściowego (arkusz Zapis). Domyślnie JPEG 4:4:4, jakość 95, sRGB, EXIF bez GPS. */
data class ExportFormat(
    val quality: Int = JpegExporter.DEFAULT_QUALITY,
    val chroma444: Boolean = true,
    val keepExif: Boolean = true,
    val space: OutputSpace = OutputSpace.Srgb,
    val type: FileType = FileType.Jpeg,
    /** Zachowanie gain mapy Ultra HDR (przełącznik „HDR ze zdjęcia” i zdjęcie z gain mapą); wymusza 4:2:0. */
    val hdr: Boolean = false
)

/** Edycja jednego zdjęcia potrzebna do zapisu. */
internal data class ExportLook(
    val geometry: Geometry,
    val lut: Lut?,
    val intensity: Float,
    val adjustments: Adjustments
)

/** Wynik zapisu jednego zdjęcia; [reduced] = zapisane mniejsze niż plik źródłowy (brak pamięci). */
class ExportOutput(val jpeg: ByteArray, val reduced: Boolean)

/** Jedno zdjęcie do zapisu: edycja + ustawienia pliku. Liczy na pełnej rozdzielczości źródła ([ExportSource]). */
internal class ExportJob(
    val look: ExportLook,
    val engine: LutEngine,
    val frame: FrameOptions,
    val typeface: Typeface,
    val maxSide: Int,
    val format: ExportFormat
) {
    /** Etapy zapisu do logu czasów; null = bez wpisów (testy, miniatury). */
    var trace: SaveTrace? = null

    /** [open] = plik źródłowy (pełna rozdzielczość); null = tylko [fallback] (np. testy bez pliku). */
    suspend fun run(open: (() -> InputStream)?, fallback: Bitmap?): ExportOutput {
        if (open == null) return ExportOutput(encode(checkNotNull(fallback), null), reduced = false)
        val gain = if (format.hdr && format.type == FileType.Jpeg &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            trace.timed("gainmap") { UltraHdrExport.gainmapOf(open) }
        } else {
            null
        }
        return ExportSource.withFallback(open, trace) { src ->
            val jpeg = encode(src.bitmap, src.exif, gain)
            src.bitmap.recycle()
            ExportOutput(jpeg, src.reduced)
        }
    }

    suspend fun encode(source: Bitmap, exif: ExifData?, gain: Any? = null): ByteArray {
        val crop = trace.timed("crop") { GeometryApplier.apply(source, look.geometry) }
        val (cropped, usedFrame) = fitToFrame(crop, frameFor(gain))
        if (cropped !== crop && crop !== source) crop.recycle()
        // ramka przesuwa piksele, więc ułamki PNG 16 mają sens tylko bez niej (i bez zmiany rozmiaru, niżej)
        val frac = (format.type == FileType.Png16 && !frame.enabled).takeIf { it }?.let {
            ByteArray(cropped.width * cropped.height)
        }
        val faces = if (look.adjustments.usesSmartSharpen) {
            trace.timed("faces") {
                FaceFinder.find(cropped)
            }
        } else {
            emptyList()
        }
        trace?.progress?.range(RENDER_FROM, RENDER_TO)
        val rendered = ExportPipeline.render(
            cropped,
            look.lut,
            engine,
            usedFrame,
            typeface,
            look.intensity,
            look.adjustments,
            faces,
            format.space,
            frac,
            trace
        )
        trace?.progress?.close()
        val out = trace.timed("resize") { ExportPipeline.resize(rendered, maxSide) }
        if (out !== rendered && rendered !== cropped && rendered !== source) rendered.recycle()
        val bytes = encodeFile(cropped, out, if (out === rendered) frac else null, exif, gain, usedFrame)
        if (out !== cropped && out !== source) out.recycle()
        if (cropped !== source) cropped.recycle()
        trace?.progress?.mark(ENCODED)
        return bytes
    }

    @Suppress("LongParameterList")
    private fun encodeFile(
        cropped: Bitmap,
        out: Bitmap,
        frac: ByteArray?,
        exif: ExifData?,
        gain: Any?,
        usedFrame: FrameOptions
    ): ByteArray {
        val kind = formatName(gain)
        val special = if (kind.startsWith("jpeg")) null else trace.timed("encode_$kind") { special(out, frac, gain) }
        return special ?: run {
            val options = trace.timed("exif_icc") { jpegOptions(exif, out) }
            trace.timed("encode_$kind") { ExportPipeline.encode(cropped, out, usedFrame, typeface, options) }
        }
    }

    /**
     * K: tryb „Oryginalny rozmiar pliku” – zdjęcie zmniejszone (Lanczos-3 w świetle liniowym, równolegle) do okna
     * ramki jeszcze przed potokiem, więc potok liczy mniej pikseli, a ramka kopiuje je 1:1 na płótno o wymiarach
     * oryginału ([FrameOptions.fitLongSide]).
     */
    @Suppress("ReturnCount")
    private fun fitToFrame(photo: Bitmap, used: FrameOptions): Pair<Bitmap, FrameOptions> {
        val window = FrameRenderer.originalWindow(used, photo.width, photo.height, typeface) ?: return photo to used
        val fitted = used.copy(fitLongSide = maxOf(photo.width, photo.height))
        if (window.first == photo.width && window.second == photo.height) return photo to fitted
        val (w, h) = window
        val scaled = trace.timed("frame_fit") {
            val px = Resampler.resize(photo, w, h)
            val space = photo.colorSpace
            val bmp = if (space != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, true, space)
            } else {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            bmp
        }
        return scaled to fitted
    }

    /** Ultra HDR bez ramki (mapa wzmocnienia odpowiada pikselom zdjęcia). */
    private fun frameFor(gain: Any?): FrameOptions = if (gain != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ) {
        frame.copy(enabled = false)
    } else {
        frame
    }

    /** Nazwa formatu w logu czasów (`save_encode_<format>`). */
    private fun formatName(gain: Any?): String = when {
        format.type == FileType.Png16 -> "png16"
        format.type == FileType.Heic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> "heic"
        gain != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "ultrahdr"
        format.chroma444 -> "jpeg444"
        else -> "jpeg420"
    }

    private fun jpegOptions(exif: ExifData?, out: Bitmap): JpegOptions {
        val wide = format.space == OutputSpace.DisplayP3
        return JpegOptions(
            quality = format.quality,
            subsampling = if (format.chroma444) ChromaSubsampling.Full444 else ChromaSubsampling.Half420,
            exif = exif?.takeIf {
                format.keepExif
            }?.let { ExifWriter.write(it, out.width, out.height, SOFTWARE, wide) },
            wideGamut = wide
        )
    }

    /** PNG 16, HEIC albo Ultra HDR; null = zwykły JPEG własnym enkoderem. */
    private fun special(out: Bitmap, frac: ByteArray?, gain: Any?): ByteArray? = when {
        format.type == FileType.Png16 -> Png16Export.encode(out, frac, format.space)

        format.type == FileType.Heic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            HeicExport.encode(out, format.quality)

        gain != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            UltraHdrExport.encode(out, gain as Gainmap, look.geometry, format.quality)

        else -> null
    }

    companion object {
        const val SOFTWARE = "Retro Film"
    }
}

private const val RENDER_FROM = 0.15f
private const val RENDER_TO = 0.85f
private const val ENCODED = 0.97f
