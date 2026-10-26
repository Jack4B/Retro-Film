package app.retrofilm.ui

import app.retrofilm.pipeline.Geometry
import app.retrofilm.pipeline.GeometryMath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kadrowanie gestem: przeciągnięcie przesuwa zdjęcie pod stałym kadrem (okno kadru idzie w przeciwną stronę),
 * szczypnięcie przybliża kadr. Przeliczenie ekranu na [Geometry.zoom]/[Geometry.panX]/[Geometry.panY] uwzględnia
 * prostowanie i perspektywę, więc palec trzyma ten sam punkt zdjęcia także przy obróconym kadrze.
 */
object CropGesture {
    private const val DEG_TO_RAD = Math.PI / 180.0

    /** Ruch palców na ekranie: przesunięcie, współczynnik szczypnięcia i skala ekranu (px ekranu na px zdjęcia). */
    data class Move(val dragX: Float, val dragY: Float, val pinch: Float, val screenPerImage: Float)

    fun apply(g: Geometry, w: Int, h: Int, move: Move): Geometry {
        if (move.screenPerImage <= 0f) return g
        val zoom = (g.zoom * move.pinch).coerceIn(1f, Geometry.MAX_ZOOM)
        val zoomed = g.copy(zoom = zoom)
        val win = GeometryMath.window(w, h, zoomed)
        // Okno przesuwa się o przeciwny wektor ruchu, w układzie zdjęcia przed obrotem o kąt prostowania.
        val ddx = -move.dragX / move.screenPerImage
        val ddy = -move.dragY / move.screenPerImage
        val angle = g.straighten * DEG_TO_RAD
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val x = win.crop.offsetX + c * ddx + s * ddy - win.centerX
        val y = win.crop.offsetY - s * ddx + c * ddy - win.centerY
        return zoomed.copy(panX = pan(x, win.reach.left, win.reach.right), panY = pan(y, win.reach.up, win.reach.down))
    }

    /** Odwrotność skali przesunięcia: [delta] px od środka odniesienia → −1..1 względem zakresu danej strony. */
    private fun pan(delta: Float, negative: Float, positive: Float): Float {
        val range = if (delta < 0f) negative else positive
        return if (range <= 0f) 0f else (delta / range).coerceIn(-1f, 1f)
    }
}
