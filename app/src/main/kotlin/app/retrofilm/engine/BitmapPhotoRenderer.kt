package app.retrofilm.engine

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.retrofilm.core.ui.photo.NeutralPhotoRenderer
import app.retrofilm.core.ui.photo.PhotoRenderer
import kotlin.math.roundToInt

/** Źródło zdjęć po kluczu (galeria / pamięć podręczna miniatur). */
fun interface PhotoSource {
    fun bitmap(key: Int): ImageBitmap?
}

/**
 * Prawdziwy [PhotoRenderer]: rysuje bitmapę ze [source] przyciętą do proporcji [Rect] (center-crop),
 * z macierzą koloru podglądu LUT. Brak bitmapy → [NeutralPhotoRenderer].
 */
class BitmapPhotoRenderer(private val source: PhotoSource) : PhotoRenderer {
    override fun DrawScope.drawPhoto(key: Int, rect: Rect, matrix: ColorMatrix?) {
        val image = source.bitmap(key)
        if (image == null) {
            with(NeutralPhotoRenderer) { drawPhoto(key, rect, matrix) }
            return
        }
        val srcRatio = image.width.toFloat() / image.height
        val dstRatio = rect.width / rect.height
        val (sw, sh) = if (srcRatio > dstRatio) {
            (image.height * dstRatio).roundToInt() to image.height
        } else {
            image.width to (image.width / dstRatio).roundToInt()
        }
        drawImage(
            image = image,
            srcOffset = IntOffset((image.width - sw) / 2, (image.height - sh) / 2),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            colorFilter = matrix?.let { ColorFilter.colorMatrix(it) }
        )
    }
}
