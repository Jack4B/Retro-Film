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
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.ExportPipeline
import app.retrofilm.pipeline.ExportSource
import app.retrofilm.pipeline.FileType
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import app.retrofilm.pipeline.HeicExport
import app.retrofilm.pipeline.OutputSpace
import app.retrofilm.pipeline.Png16Export
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
    /** [open] = plik źródłowy (pełna rozdzielczość); null = tylko [fallback] (np. testy bez pliku). */
    suspend fun run(open: (() -> InputStream)?, fallback: Bitmap?): ExportOutput {
        if (open == null) return ExportOutput(encode(checkNotNull(fallback), null), reduced = false)
        val gain = if (format.hdr && format.type == FileType.Jpeg &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            UltraHdrExport.gainmapOf(open)
        } else {
            null
        }
        return ExportSource.withFallback(open) { src ->
            val jpeg = encode(src.bitmap, src.exif, gain)
            src.bitmap.recycle()
            ExportOutput(jpeg, src.reduced)
        }
    }

    suspend fun encode(source: Bitmap, exif: ExifData?, gain: Any? = null): ByteArray {
        val cropped = GeometryApplier.apply(source, look.geometry)
        val hdr = gain != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        // ramka przesuwa piksele, więc ułamki PNG 16 mają sens tylko bez niej (i bez zmiany rozmiaru, niżej)
        val frac = (format.type == FileType.Png16 && !frame.enabled).takeIf { it }?.let {
            ByteArray(cropped.width * cropped.height)
        }
        val usedFrame = if (hdr) frame.copy(enabled = false) else frame
        val faces = if (look.adjustments.usesSmartSharpen) FaceFinder.find(cropped) else emptyList()
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
            frac
        )
        val out = ExportPipeline.resize(rendered, maxSide)
        if (out !== rendered && rendered !== cropped && rendered !== source) rendered.recycle()
        val special = special(out, if (out === rendered) frac else null, gain)
        val bytes = special ?: ExportPipeline.encode(cropped, out, usedFrame, typeface, jpegOptions(exif, out))
        if (out !== cropped && out !== source) out.recycle()
        if (cropped !== source) cropped.recycle()
        return bytes
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
