package app.retrofilm.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import app.retrofilm.R
import kotlin.math.min

/**
 * Znak wodny aplikacji w prawym dolnym rogu zdjęcia, na białej karcie: u góry ikona aplikacji z nazwą [NAME],
 * pod nią kod QR z linkiem do repozytorium ([URL]) z ciszą wokół kodu (wymóg czytników QR).
 * Macierz modułów (QR wersja 3, korekcja M, 29×29) jest wygenerowana
 * z góry dla stałego adresu i sprawdzona dekoderem, więc nie ma zależności od biblioteki kodów kreskowych.
 */
internal object Watermark {
    const val URL = "https://github.com/Jack4B/Retro-Film"
    const val NAME = "Retro-Film"

    private const val SIZE_FRACTION = 0.11f
    private const val INSET_FRACTION = 0.02f
    private const val QUIET_MODULES = 2
    private const val CORNER_FRACTION = 0.12f
    private const val HEADER_FRACTION = 0.26f
    private const val HEADER_PAD = 0.07f
    private const val ICON_FRACTION = 0.8f
    private const val ICON_CORNER = 0.22f
    private const val ICON_PX = 128
    private const val TEXT_GAP = 0.25f
    private const val NAME_COLOR = 0xFF2B2B2B.toInt()

    /** Ikona aplikacji (ustawiana raz przy starcie przez [installIcon]); bez niej karta ma tylko nazwę. */
    @Volatile
    var icon: Bitmap? = null
    private val MODULES = listOf(
        "11111110001001111010001111111",
        "10000010111010001110101000001",
        "10111010110110011001001011101",
        "10111010101010111110101011101",
        "10111010011011001001101011101",
        "10000010001011100000101000001",
        "11111110101010101010101111111",
        "00000000110111001010000000000",
        "10000010100110100000011001110",
        "00111101111010110011010110110",
        "01001011111110011110000010000",
        "11011100011000100011010001000",
        "01101110110111101010101100001",
        "01101000101011111110101110011",
        "00001111100101110100110011100",
        "00000000010001110001000110101",
        "11011010111010110001010101100",
        "10000000110110001001001110111",
        "11111111110101110011000011001",
        "10000000111100101010101000000",
        "10110011000010000101111110111",
        "00000000110101011010100011000",
        "11111110010011111001101011100",
        "10000010010101011001100010000",
        "10111010001001001001111111001",
        "10111010010010011001110001101",
        "10111010010100010001111111110",
        "10000010000110111010001101101",
        "11111110110001111101001110100"
    )

    /** Renderuje ikonę launchera aplikacji do bitmapy używanej w znaku wodnym. */
    fun installIcon(context: Context) {
        val d = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return
        val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON_PX, ICON_PX)
        d.draw(Canvas(bmp))
        icon = bmp
    }

    /** Rysuje kartę w prawym dolnym rogu prostokąta [area] (obszar zdjęcia). */
    fun draw(canvas: Canvas, area: RectF) {
        val n = MODULES.size
        val unit = min(area.width(), area.height())
        val side = unit * SIZE_FRACTION
        val inset = unit * INSET_FRACTION
        val cell = side / (n + 2 * QUIET_MODULES)
        val right = area.right - inset
        val bottom = area.bottom - inset
        val left = right - side
        val qrTop = bottom - side
        val headerH = side * HEADER_FRACTION
        val card = RectF(left, qrTop - headerH, right, bottom)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(card, side * CORNER_FRACTION, side * CORNER_FRACTION, white)
        drawHeader(canvas, RectF(left, card.top, right, qrTop))
        val ink = Paint().apply { color = Color.BLACK }
        val originX = left + QUIET_MODULES * cell
        val originY = qrTop + QUIET_MODULES * cell
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (MODULES[y][x] == '1') {
                    canvas.drawRect(
                        originX + x * cell,
                        originY + y * cell,
                        originX + (x + 1) * cell,
                        originY + (y + 1) * cell,
                        ink
                    )
                }
            }
        }
    }

    /** Nagłówek karty: ikona i nazwa aplikacji w jednym wierszu, wyśrodkowane. */
    private fun drawHeader(canvas: Canvas, box: RectF) {
        val pad = box.width() * HEADER_PAD
        val iconSize = if (icon != null) box.height() * ICON_FRACTION else 0f
        val gap = if (icon != null) iconSize * TEXT_GAP else 0f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_COLOR
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = box.height() * ICON_FRACTION
        }
        val room = box.width() - 2 * pad - iconSize - gap
        val measured = text.measureText(NAME)
        if (measured > room) text.textSize *= room / measured
        val total = iconSize + gap + text.measureText(NAME)
        var x = box.left + (box.width() - total) / 2f
        val cy = box.centerY()
        icon?.let { bmp ->
            val dst = RectF(x, cy - iconSize / 2f, x + iconSize, cy + iconSize / 2f)
            canvas.save()
            val r = iconSize * ICON_CORNER
            canvas.clipPath(Path().apply { addRoundRect(dst, r, r, Path.Direction.CW) })
            canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            x += iconSize + gap
        }
        val fm = text.fontMetrics
        canvas.drawText(NAME, x, cy - (fm.ascent + fm.descent) / 2f, text)
    }
}
