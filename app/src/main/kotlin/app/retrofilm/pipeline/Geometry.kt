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
    val panY: Float = 0f
) {
    val isNeutral: Boolean
        get() = quarterTurns % FULL_TURN == 0 && straighten == 0f && aspect == CropAspect.Original && zoom == 1f

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

    fun crop(w: Int, h: Int, g: Geometry): Crop {
        val turned = g.quarterTurns % 2 != 0
        val tw = (if (turned) h else w).toFloat()
        val th = (if (turned) w else h).toFloat()
        val a = targetRatio(tw, th, g.aspect, g.flip)
        val rad = abs(g.straighten) * DEG_TO_RAD
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val fit = min(tw / (c + s / a), th / (s + c / a))
        val cw = fit / g.zoom.coerceIn(1f, Geometry.MAX_ZOOM)
        val ch = cw / a
        // Wszystkie cztery narożniki obróconego kadru muszą zostać w zdjęciu: stąd zapas przesunięcia.
        val ex = cw / 2f * c + ch / 2f * s
        val ey = cw / 2f * s + ch / 2f * c
        val ox = g.panX.coerceIn(-1f, 1f) * maxOf(0f, tw / 2f - ex)
        val oy = g.panY.coerceIn(-1f, 1f) * maxOf(0f, th / 2f - ey)
        return Crop(cw, ch, ox, oy)
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
        val out = if (cs != null) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(g.quarterTurns * QUARTER)
            postTranslate(-crop.offsetX, -crop.offsetY)
            postRotate(g.straighten)
            postTranslate(w / 2f, h / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private const val QUARTER = 90f
}
