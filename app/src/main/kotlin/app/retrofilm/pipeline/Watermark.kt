package app.retrofilm.pipeline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * Znak wodny aplikacji: kod QR z linkiem do repozytorium ([URL]) w prawym dolnym rogu zdjęcia, na białym tle
 * z ciszą wokół kodu (wymóg czytników QR). Macierz modułów (QR wersja 3, korekcja M, 29×29) jest wygenerowana
 * z góry dla stałego adresu i sprawdzona dekoderem, więc nie ma zależności od biblioteki kodów kreskowych.
 */
internal object Watermark {
    const val URL = "https://github.com/Jack4B/Retro-Film"

    private const val SIZE_FRACTION = 0.11f
    private const val INSET_FRACTION = 0.02f
    private const val QUIET_MODULES = 2
    private const val CORNER_FRACTION = 0.12f
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

    /** Rysuje kod w prawym dolnym rogu prostokąta [area] (obszar zdjęcia). */
    fun draw(canvas: Canvas, area: RectF) {
        val n = MODULES.size
        val unit = min(area.width(), area.height())
        val side = unit * SIZE_FRACTION
        val inset = unit * INSET_FRACTION
        val cell = side / (n + 2 * QUIET_MODULES)
        val right = area.right - inset
        val bottom = area.bottom - inset
        val left = right - side
        val top = bottom - side
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(left, top, right, bottom), side * CORNER_FRACTION, side * CORNER_FRACTION, white)
        val ink = Paint().apply { color = Color.BLACK }
        val originX = left + QUIET_MODULES * cell
        val originY = top + QUIET_MODULES * cell
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
}
