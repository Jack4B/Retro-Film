package app.retrofilm.feature.editor.frame

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import app.retrofilm.core.frame.SignatureMeasurer
import app.retrofilm.core.frame.TextBlock
import app.retrofilm.core.ui.CaveatFamily

/** Styl podpisu dla rozmiaru w pikselach; [pxPerSp] = density · fontScale. */
fun signatureStyle(color: Color, pxPerSp: Float): (Float) -> TextStyle = { px ->
    TextStyle(
        fontFamily = CaveatFamily,
        fontSize = (px / pxPerSp).sp,
        color = color,
        textAlign = TextAlign.Center
    )
}

/** Pomiar podpisu przez Compose [TextMeasurer] – te same parametry co przy rysowaniu. */
class ComposeSignatureMeasurer(
    private val measurer: TextMeasurer,
    private val text: String,
    private val style: (Float) -> TextStyle
) : SignatureMeasurer {
    override fun measure(sizePx: Float, wrapWidth: Float?): TextBlock {
        val r = if (wrapWidth == null) {
            measurer.measure(text, style(sizePx), softWrap = false, maxLines = 1)
        } else {
            measurer.measure(
                text,
                style(sizePx),
                softWrap = true,
                constraints = Constraints(maxWidth = wrapWidth.toInt())
            )
        }
        return TextBlock(r.size.width.toFloat(), r.size.height.toFloat(), r.lineCount)
    }
}
