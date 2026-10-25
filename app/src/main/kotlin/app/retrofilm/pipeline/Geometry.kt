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
    Classic32("3:2", 3f / 2f),
    Wide169("16:9", 16f / 9f)
}

/** Kadrowanie i obrót: [quarterTurns] obrotów o 90° w prawo, prostowanie w stopniach (+ = w prawo), kadr środkowy. */
data class Geometry(
    val quarterTurns: Int = 0,
    val straighten: Float = 0f,
    val aspect: CropAspect = CropAspect.Original
) {
    val isNeutral: Boolean get() = quarterTurns % FULL_TURN == 0 && straighten == 0f && aspect == CropAspect.Original

    companion object {
        const val FULL_TURN = 4
        const val MAX_STRAIGHTEN = 15f
    }
}

/** Czysta matematyka kadru: największy prostokąt o zadanych proporcjach mieszczący się w obróconym zdjęciu. */
object GeometryMath {
    private const val DEG_TO_RAD = PI / 180.0

    /** Rozmiar wyniku (px) dla zdjęcia [w]×[h]. Bez żadnych zmian zwraca ([w], [h]). */
    fun outputSize(w: Int, h: Int, g: Geometry): Pair<Int, Int> {
        val turned = g.quarterTurns % 2 != 0
        val tw = (if (turned) h else w).toFloat()
        val th = (if (turned) w else h).toFloat()
        val a = targetRatio(tw, th, g.aspect)
        val rad = abs(g.straighten) * DEG_TO_RAD
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val cw = min(tw / (c + s / a), th / (s + c / a))
        val ch = cw / a
        return cw.roundToInt().coerceAtLeast(1) to ch.roundToInt().coerceAtLeast(1)
    }

    /** Proporcje (szerokość/wysokość) kadru; zdjęcie pionowe dostaje odwrócone proporcje presetu. */
    fun targetRatio(tw: Float, th: Float, aspect: CropAspect): Float {
        val r = aspect.ratio ?: return tw / th
        return if (tw >= th) r else 1f / r
    }
}

/** Nakłada [Geometry] na bitmapę (nowa bitmapa w tej samej przestrzeni barw; neutralne = ta sama bitmapa). */
object GeometryApplier {
    fun apply(src: Bitmap, g: Geometry): Bitmap {
        if (g.isNeutral) return src
        val (w, h) = GeometryMath.outputSize(src.width, src.height, g)
        val cs = src.colorSpace
        val out = if (cs != null) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false, cs)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(g.quarterTurns * QUARTER + g.straighten)
            postTranslate(w / 2f, h / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private const val QUARTER = 90f
}
