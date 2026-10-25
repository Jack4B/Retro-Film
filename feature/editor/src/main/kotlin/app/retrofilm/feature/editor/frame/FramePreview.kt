package app.retrofilm.feature.editor.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SizeF
import app.retrofilm.core.ui.CaveatFamily
import app.retrofilm.core.ui.photo.LocalPhotoRenderer
import app.retrofilm.core.ui.photo.drawPhoto
import app.retrofilm.feature.editor.R

/** Podgląd ramki na żywo; ten sam układ co eksport (FrameGeometry). */
@Composable
fun FramePreview(
    settings: FrameSettings,
    signature: String,
    photoAspect: Float,
    photoSeed: Int,
    matrix: ColorMatrix?,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val renderer = LocalPhotoRenderer.current
    val canvasRatio = if (!settings.enabled) photoAspect else settings.aspect.ratio ?: photoAspect
    val desc = when {
        !settings.enabled -> stringResource(R.string.frame_preview_none)
        !settings.signatureEnabled -> stringResource(R.string.frame_preview_no_signature, settings.aspect.label)
        signature.isBlank() -> stringResource(R.string.frame_preview_empty_signature, settings.aspect.label)
        else -> stringResource(R.string.frame_preview_signature, settings.aspect.label, signature)
    }
    Canvas(modifier.aspectRatio(canvasRatio).semantics { contentDescription = desc }) {
        if (!settings.enabled) {
            drawPhoto(renderer, photoSeed, Rect(Offset.Zero, size), matrix)
            return@Canvas
        }
        val l = FrameGeometry.layout(
            SizeF(size.width, size.height),
            SizeF(photoAspect, 1f),
            settings.margin,
            settings.signatureEnabled
        )
        drawRect(settings.background.color)
        drawPhoto(
            renderer,
            photoSeed,
            Rect(Offset(l.photo.left, l.photo.top), Size(l.photo.width, l.photo.height)),
            matrix
        )
        val band = l.signatureBand
        if (band == null || signature.isBlank()) return@Canvas
        val style = signatureStyle(settings.signatureColor.color, density * fontScale)
        val fit = FrameGeometry.fitSignature(
            l.signatureBaseSize,
            l.signatureMaxWidth,
            band.height,
            ComposeSignatureMeasurer(measurer, signature, style)
        )
        val result = measurer.measure(
            signature,
            style(fit.sizePx),
            maxLines = fit.lines,
            softWrap = fit.lines > 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = l.signatureMaxWidth.toInt())
        )
        // Za długi podpis (fits = false) nie jest rysowany — ani w podglądzie, ani w eksporcie.
        if (!fit.fits) return@Canvas
        val r = FrameGeometry.signatureBounds(l, result.size.width.toFloat(), result.size.height.toFloat())
            ?: return@Canvas
        drawText(result, topLeft = Offset(r.left, r.top))
    }
}
