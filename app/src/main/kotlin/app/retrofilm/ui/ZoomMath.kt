package app.retrofilm.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Wymiary w pikselach. */
internal data class Dim(val w: Int, val h: Int) {
    val isEmpty: Boolean get() = w <= 0 || h <= 0
}

/** Skala podglądu względem rozmiaru zapisywanego zdjęcia i jego wymiary (bez Androida, do testów). */
internal object ZoomMath {
    private const val PERCENT = 100f
    private const val MEGA = 1_000_000f

    /**
     * Ile procent rozmiaru zapisywanego zdjęcia widać na ekranie: 100% = jeden piksel zdjęcia na jeden piksel
     * ekranu. [box] to pole podglądu, [shown] rozmiar wyświetlanej bitmapy (podglądu), [exportLong] dłuższy bok
     * zapisywanego zdjęcia, a [zoom] bieżące powiększenie gestem.
     */
    fun percent(box: Dim, shown: Dim, exportLong: Int, zoom: Float): Int {
        if (box.isEmpty || shown.isEmpty || exportLong <= 0) return 0
        val fit = min(box.w.toFloat() / shown.w, box.h.toFloat() / shown.h)
        return (fit * max(shown.w, shown.h) / exportLong * zoom * PERCENT).roundToInt()
    }

    /** Wymiary zapisywanego zdjęcia przy proporcjach wyświetlanej bitmapy i dłuższym boku [exportLong]. */
    fun exportSize(shownW: Int, shownH: Int, exportLong: Int): Pair<Int, Int> {
        val long = max(shownW, shownH).coerceAtLeast(1)
        return (shownW.toFloat() * exportLong / long).roundToInt() to
            (shownH.toFloat() * exportLong / long).roundToInt()
    }

    fun megapixels(w: Int, h: Int): Float = w.toFloat() * h / MEGA
}
