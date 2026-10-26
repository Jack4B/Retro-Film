package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Proporcje kadru; „Oryginał” zachowuje proporcje zdjęcia po obrocie. Orientacja idzie za zdjęciem. */
@Suppress("MagicNumber") // proporcje są danymi, nie „magicznymi liczbami”
enum class CropAspect(val label: String, val ratio: Float?) {
    Original("Oryginał", null),
    Square("1:1", 1f),
    Portrait45("4:5", 5f / 4f),
    Standard43("4:3", 4f / 3f),
    Classic32("3:2", 3f / 2f),
    Wide169("16:9", 16f / 9f)
}

/**
 * Kadrowanie i obrót: [quarterTurns] obrotów o 90° w prawo, prostowanie w stopniach (+ = w prawo),
 * proporcje ([aspect], [flip] zamienia poziom z pionem), przybliżenie kadru [zoom] (1 = największy kadr)
 * oraz przesunięcie okna kadru [panX]/[panY] w zakresie −1..1 (0 = środek, ±1 = do krawędzi zdjęcia).
 */
data class Geometry(
    val quarterTurns: Int = 0,
    val straighten: Float = 0f,
    val aspect: CropAspect = CropAspect.Original,
    val flip: Boolean = false,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Perspektywa pionowa −1..1 (pochylenie kamery; prostuje zbiegające się pionowe linie), zob. [Perspective]. */
    val perspV: Float = 0f,
    /** Perspektywa pozioma −1..1 (obrót kamery wokół pionu). */
    val perspH: Float = 0f
) {
    val hasPerspective: Boolean get() = perspV != 0f || perspH != 0f

    val isNeutral: Boolean
        get() = quarterTurns % FULL_TURN == 0 && straighten == 0f && aspect == CropAspect.Original && zoom == 1f &&
            !hasPerspective

    companion object {
        const val FULL_TURN = 4
        const val MAX_STRAIGHTEN = 15f
        const val MAX_ZOOM = 5f
    }
}

/** Czysta matematyka kadru: największy prostokąt o zadanych proporcjach mieszczący się w obróconym zdjęciu. */
object GeometryMath {
    private const val DEG_TO_RAD = PI / 180.0

    /** Rozmiar wyniku (px) dla zdjęcia [w]×[h]. Bez żadnych zmian zwraca ([w], [h]). */
    fun outputSize(w: Int, h: Int, g: Geometry): Pair<Int, Int> {
        val c = crop(w, h, g)
        return c.width.roundToInt().coerceAtLeast(1) to c.height.roundToInt().coerceAtLeast(1)
    }

    /** Okno kadru w obróconym zdjęciu: rozmiar i przesunięcie środka (px) od środka zdjęcia. */
    data class Crop(val width: Float, val height: Float, val offsetX: Float, val offsetY: Float)

    /**
     * Okno kadru razem z jego położeniem odniesienia i zakresami przesunięcia (px od środka odniesienia w lewo,
     * w prawo, w górę i w dół), z których [Geometry.panX]/[Geometry.panY] biorą swoją skalę (też dla gestów).
     */
    data class Window(val crop: Crop, val centerX: Float, val centerY: Float, val reach: Reach)

    /** Zakres przesunięcia okna kadru (px) w każdą stronę. */
    data class Reach(val left: Float, val right: Float, val up: Float, val down: Float)

    fun crop(w: Int, h: Int, g: Geometry): Crop = window(w, h, g).crop

    fun window(w: Int, h: Int, g: Geometry): Window {
        val turned = g.quarterTurns % 2 != 0
        val tw = (if (turned) h else w).toFloat()
        val th = (if (turned) w else h).toFloat()
        val a = targetRatio(tw, th, g.aspect, g.flip)
        if (g.hasPerspective) return perspectiveWindow(tw, th, a, g)
        val rad = abs(g.straighten) * DEG_TO_RAD
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val fit = min(tw / (c + s / a), th / (s + c / a))
        val cw = fit / g.zoom.coerceIn(1f, Geometry.MAX_ZOOM)
        val ch = cw / a
        // Wszystkie cztery narożniki obróconego kadru muszą zostać w zdjęciu: stąd zapas przesunięcia.
        val ex = cw / 2f * c + ch / 2f * s
        val ey = cw / 2f * s + ch / 2f * c
        val rx = maxOf(0f, tw / 2f - ex)
        val ry = maxOf(0f, th / 2f - ey)
        val crop = Crop(cw, ch, g.panX.coerceIn(-1f, 1f) * rx, g.panY.coerceIn(-1f, 1f) * ry)
        return Window(crop, 0f, 0f, Reach(rx, rx, ry, ry))
    }

    /**
     * Kadr po korekcie perspektywy: zdjęcie to czworokąt [Perspective.quad], okno ([CropFit]) ma największy rozmiar
     * o proporcjach [a], a przesunięcie [Geometry.panX]/[Geometry.panY] liczone jest od optymalnego środka do
     * krawędzi dozwolonego obszaru w daną stronę.
     */
    private fun perspectiveWindow(tw: Float, th: Float, a: Float, g: Geometry): Window {
        val m = Perspective.matrix(tw.toDouble(), th.toDouble(), g.perspV, g.perspH)
        val fit = CropFit(Perspective.quad(tw.toDouble(), th.toDouble(), m), a.toDouble(), g.straighten * DEG_TO_RAD)
        val best = fit.best()
        val cw = best.width / g.zoom.coerceIn(1f, Geometry.MAX_ZOOM)
        val r = fit.ranges(best.centerX, best.centerY, cw)
        val px = g.panX.coerceIn(-1f, 1f)
        val py = g.panY.coerceIn(-1f, 1f)
        val ox = best.centerX + if (px < 0f) px * r.left else px * r.right
        val oy = best.centerY + if (py < 0f) py * r.up else py * r.down
        val width = cw.toFloat().coerceAtLeast(1f)
        val crop = Crop(width, width / a, ox.toFloat(), oy.toFloat())
        val reach = Reach(r.left.toFloat(), r.right.toFloat(), r.up.toFloat(), r.down.toFloat())
        return Window(crop, best.centerX.toFloat(), best.centerY.toFloat(), reach)
    }

    /** Proporcje (szer./wys.) kadru; zdjęcie pionowe dostaje odwrócone proporcje presetu, [flip] je zamienia. */
    fun targetRatio(tw: Float, th: Float, aspect: CropAspect, flip: Boolean = false): Float {
        val r = aspect.ratio ?: return tw / th
        val base = if (tw >= th) r else 1f / r
        return if (flip) 1f / base else base
    }
}

/** Nakłada [Geometry] na bitmapę (nowa bitmapa w tej samej przestrzeni barw; neutralne = ta sama bitmapa). */
object GeometryApplier {
    fun apply(src: Bitmap, g: Geometry): Bitmap {
        if (g.isNeutral) return src
        val crop = GeometryMath.crop(src.width, src.height, g)
        val w = crop.width.roundToInt().coerceAtLeast(1)
        val h = crop.height.roundToInt().coerceAtLeast(1)
        val cs = src.colorSpace
        val make = { bw: Int, bh: Int ->
            if (cs != null) {
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888, false, cs)
            } else {
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            }
        }
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(g.quarterTurns * QUARTER)
            if (g.hasPerspective) postConcat(perspectiveMatrix(src, g))
            postTranslate(-crop.offsetX, -crop.offsetY)
            postRotate(g.straighten)
            postTranslate(w / 2f, h / 2f)
        }
        // Prostowanie i perspektywa wymagają interpolacji (bikubiczna); obrót o 90° i przesunięcie są dokładne.
        return if (g.straighten != 0f || g.hasPerspective) {
            BicubicWarp.warp(src, m, w, h, make)
        } else {
            snapTranslation(m)
            make(w, h).also { Canvas(it).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG)) }
        }
    }

    /** Homografia perspektywy w układzie obróconego (o ćwiartki) zdjęcia ze środkiem w zerze. */
    private fun perspectiveMatrix(src: Bitmap, g: Geometry): Matrix {
        val turned = g.quarterTurns % 2 != 0
        val tw = (if (turned) src.height else src.width).toDouble()
        val th = (if (turned) src.width else src.height).toDouble()
        val h = Perspective.matrix(tw, th, g.perspV, g.perspH)
        return Matrix().apply { setValues(FloatArray(Perspective.SIZE) { h[it].toFloat() }) }
    }

    /** Zaokrągla przesunięcie do całych pikseli, żeby kopiowanie piksel w piksel nie rozmywało obrazu. */
    private fun snapTranslation(m: Matrix) {
        val v = FloatArray(MATRIX_SIZE)
        m.getValues(v)
        v[Matrix.MTRANS_X] = v[Matrix.MTRANS_X].roundToInt().toFloat()
        v[Matrix.MTRANS_Y] = v[Matrix.MTRANS_Y].roundToInt().toFloat()
        m.setValues(v)
    }

    private const val QUARTER = 90f
    private const val MATRIX_SIZE = 9
}
