package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.Typeface
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
import app.retrofilm.pipeline.FrameOptions
import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryApplier
import java.io.InputStream

/** Ustawienia pliku wyjściowego (arkusz Zapis). Domyślnie JPEG 4:4:4, jakość 95, sRGB, EXIF bez GPS. */
data class ExportFormat(
    val quality: Int = JpegExporter.DEFAULT_QUALITY,
    val chroma444: Boolean = true,
    val keepExif: Boolean = true
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
        return ExportSource.withFallback(open) { src ->
            val jpeg = encode(src.bitmap, src.exif)
            src.bitmap.recycle()
            ExportOutput(jpeg, src.reduced)
        }
    }

    suspend fun encode(source: Bitmap, exif: ExifData?): ByteArray {
        val cropped = GeometryApplier.apply(source, look.geometry)
        val faces = if (look.adjustments.usesSmartSharpen) FaceFinder.find(cropped) else emptyList()
        val rendered = ExportPipeline.render(
            cropped,
            look.lut,
            engine,
            frame,
            typeface,
            look.intensity,
            look.adjustments,
            faces
        )
        val out = ExportPipeline.resize(rendered, maxSide)
        if (out !== rendered && rendered !== cropped && rendered !== source) rendered.recycle()
        val options = JpegOptions(
            quality = format.quality,
            subsampling = if (format.chroma444) ChromaSubsampling.Full444 else ChromaSubsampling.Half420,
            exif = exif?.takeIf { format.keepExif }?.let { ExifWriter.write(it, out.width, out.height, SOFTWARE) }
        )
        val jpeg = ExportPipeline.encode(cropped, out, frame, typeface, options)
        if (out !== cropped && out !== source) out.recycle()
        if (cropped !== source) cropped.recycle()
        return jpeg
    }

    companion object {
        const val SOFTWARE = "Retro Film"
    }
}
