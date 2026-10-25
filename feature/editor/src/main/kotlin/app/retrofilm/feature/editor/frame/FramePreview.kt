package app.retrofilm.feature.editor.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import app.retrofilm.core.frame.FrameGeometry
import app.retrofilm.core.frame.SizeF
import app.retrofilm.core.ui.CaveatFamily
import app.retrofilm.core.ui.photo.LocalPhotoRenderer
import app.retrofilm.core.ui.photo.drawPhoto
import kotlin.math.roundToInt

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
        !settings.enabled -> "Podgląd zdjęcia bez ramki"
        !settings.signatureEnabled -> "Podgląd: ramka ${settings.aspect.label}, bez podpisu"
        signature.isBlank() -> "Podgląd: ramka ${settings.aspect.label}, pusty pas podpisu"
        else -> "Podgląd: ramka ${settings.aspect.label}, podpis $signature"
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
        val band = l.signatureBand ?: return@Canvas
        if (signature.isBlank()) return@Canvas
        fun style(px: Float) = TextStyle(
            fontFamily = CaveatFamily,
            fontSize = (px / density / fontScale).sp,
            color = settings.signatureColor.color,
            textAlign = TextAlign.Center
        )
        val maxW = l.signatureMaxWidth
        val fit = FrameGeometry.fitSignature(l.signatureBaseSize, maxW) { px ->
            measurer.measure(signature, style(px), maxLines = 1, softWrap = false).size.width.toFloat()
        }
        val result = measurer.measure(
            signature,
            style(fit.sizePx),
            maxLines = fit.lines,
            softWrap = fit.lines > 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxW.roundToInt())
        )
        drawText(
            result,
            topLeft = Offset(
                (size.width - result.size.width) / 2f,
                band.top + (band.height - result.size.height) / 2f
            )
        )
    }
}
