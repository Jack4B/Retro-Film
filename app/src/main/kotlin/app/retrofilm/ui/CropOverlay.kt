package app.retrofilm.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.retrofilm.pipeline.GeometryMath

private const val GRID_ALPHA = 0.45f
private const val THIRDS = 3
private val GridStroke = 1.dp

/** Przeciągnięcie / szczypnięcie na podglądzie w zakładce Kadr: przesuwa i przybliża okno kadru. */
internal fun dragCrop(state: MainState, shown: Bitmap?, box: IntSize, pan: Offset, pinch: Float) {
    val photo = state.photo ?: return
    val bmp = shown ?: return
    val g = state.geometry
    val crop = GeometryMath.crop(photo.width, photo.height, g)
    val fit = minOf(box.width.toFloat() / bmp.width, box.height.toFloat() / bmp.height)
    val move = CropGesture.Move(pan.x, pan.y, pinch, bmp.width * fit / crop.width)
    state.updateGeometry(CropGesture.apply(g, photo.width, photo.height, move))
}

/** Siatka trójpodziału nałożona na zdjęcie ([imageW]×[imageH] dopasowane do całego obszaru), pomoc przy kadrowaniu. */
@Composable
internal fun ThirdsGrid(imageW: Int, imageH: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val fit = minOf(size.width / imageW, size.height / imageH)
        val w = imageW * fit
        val h = imageH * fit
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val color = Color.White.copy(alpha = GRID_ALPHA)
        val stroke = GridStroke.toPx()
        for (i in 1 until THIRDS) {
            val x = left + w * i / THIRDS
            val y = top + h * i / THIRDS
            drawLine(color, Offset(x, top), Offset(x, top + h), stroke)
            drawLine(color, Offset(left, y), Offset(left + w, y), stroke)
        }
    }
}
