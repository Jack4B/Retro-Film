package app.retrofilm.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Wymiary w pikselach. */
internal data class Dim(val w: Int, val h: Int) {
    val isEmpty: Boolean get() = w <= 0 || h <= 0
}

/** Prostokąt w pikselach zdjęcia (lewo/góra włącznie, prawo/dół wyłącznie). */
internal data class PhotoRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

/**
 * Skala podglądu względem rozmiaru zapisywanego zdjęcia i geometria widocznego wycinka przy pinch-zoom
 * (O: ROI zamiast pełnego 2560).
 */
internal object ZoomMath {
    private const val PERCENT = 100f
    private const val MEGA = 1_000_000f

    fun percent(box: Dim, shown: Dim, exportLong: Int, zoom: Float): Int {
        if (box.isEmpty || shown.isEmpty || exportLong <= 0) return 0
        val fit = min(box.w.toFloat() / shown.w, box.h.toFloat() / shown.h)
        return (fit * max(shown.w, shown.h) / exportLong * zoom * PERCENT).roundToInt()
    }

    fun exportSize(shownW: Int, shownH: Int, exportLong: Int): Pair<Int, Int> {
        val long = max(shownW, shownH).coerceAtLeast(1)
        return (shownW.toFloat() * exportLong / long).roundToInt() to
            (shownH.toFloat() * exportLong / long).roundToInt()
    }

    fun megapixels(w: Int, h: Int): Float = w.toFloat() * h / MEGA

    /**
     * Widoczny fragment zdjęcia przy ContentScale.Fit + graphicsLayer(scale, offset wokół środka widoku).
     * [margin] rozszerza wycinek (zapas na pan bez natychmiastowego przerysowania).
     */
    @Suppress("LongParameterList", "ComplexCondition", "ReturnCount")
    fun visiblePhotoRect(
        photoW: Int,
        photoH: Int,
        viewW: Int,
        viewH: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        margin: Float = 0.35f
    ): PhotoRect {
        if (photoW <= 0 || photoH <= 0 || viewW <= 0 || viewH <= 0) {
            return PhotoRect(0, 0, photoW.coerceAtLeast(0), photoH.coerceAtLeast(0))
        }
        val fit = min(viewW.toFloat() / photoW, viewH.toFloat() / photoH)
        val z = scale.coerceAtLeast(1f)
        // środek widoku w układzie przed zoomem → współrzędne zdjęcia
        val cx = viewW / 2f
        val cy = viewH / 2f
        fun viewToPhoto(vx: Float, vy: Float): Pair<Float, Float> {
            // odwrotność: translation potem scale wokół środka
            val lx = (vx - cx - offsetX) / z + cx
            val ly = (vy - cy - offsetY) / z + cy
            val px = (lx - (viewW - photoW * fit) / 2f) / fit
            val py = (ly - (viewH - photoH * fit) / 2f) / fit
            return px to py
        }
        val corners = listOf(
            viewToPhoto(0f, 0f),
            viewToPhoto(viewW.toFloat(), 0f),
            viewToPhoto(0f, viewH.toFloat()),
            viewToPhoto(viewW.toFloat(), viewH.toFloat())
        )
        var left = corners.minOf { it.first }
        var top = corners.minOf { it.second }
        var right = corners.maxOf { it.first }
        var bottom = corners.maxOf { it.second }
        val mw = (right - left) * margin
        val mh = (bottom - top) * margin
        left -= mw
        right += mw
        top -= mh
        bottom += mh
        return PhotoRect(
            left.roundToInt().coerceIn(0, photoW),
            top.roundToInt().coerceIn(0, photoH),
            right.roundToInt().coerceIn(0, photoW),
            bottom.roundToInt().coerceIn(0, photoH)
        )
    }

    fun expand(r: PhotoRect, halo: Int, photoW: Int, photoH: Int): PhotoRect = PhotoRect(
        (r.left - halo).coerceAtLeast(0),
        (r.top - halo).coerceAtLeast(0),
        (r.right + halo).coerceAtMost(photoW),
        (r.bottom + halo).coerceAtMost(photoH)
    )
}

/**
 * P: jak Compose graphicsLayer ma skalować podgląd.
 * ROI aktywny = bitmapa już jest wycinkiem (identity); inaczej pinch-zoom na całej bitmapie
 * (w tym zdjęcie+ramka po FrameRenderer).
 */
internal data class ComposeZoomLayer(val scale: Float, val offsetX: Float, val offsetY: Float)

/**
 * [roiActive] – bitmapa to już wycinek; [awaitingRoi] – Q: czekamy na pierwszy ROI po double-tap
 * (nie pokazuj 2,5× na pełnym kadrze, potem identity – jeden skok gdy ROI gotowe).
 */
internal fun composeZoomLayer(
    roiActive: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    awaitingRoi: Boolean = false
): ComposeZoomLayer =
    if (roiActive || awaitingRoi) ComposeZoomLayer(1f, 0f, 0f) else ComposeZoomLayer(scale, offsetX, offsetY)
