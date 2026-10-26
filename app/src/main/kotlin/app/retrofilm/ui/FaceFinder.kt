package app.retrofilm.ui

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector
import app.retrofilm.pipeline.FaceBox
import kotlin.math.max

/**
 * Wykrywanie twarzy wbudowanym w Androida [FaceDetector] (działa lokalnie, bez dodatkowych bibliotek i sieci;
 * rozpoznaje twarze zwrócone przodem). Wyniki to ramki znormalizowane do obrazu, używane przez auto-ulepszenie
 * do znalezienia skóry. Każda awaria wykrywacza oznacza po prostu brak twarzy.
 */
internal object FaceFinder {
    private const val SIDE = 480
    private const val MAX_FACES = 6
    private const val MIN_CONFIDENCE = 0.4f
    private const val CENTER_SHIFT = 0.35f
    private const val HALF_WIDTH = 1.2f
    private const val HALF_HEIGHT = 1.5f

    fun find(bitmap: Bitmap): List<FaceBox> {
        val long = max(bitmap.width, bitmap.height)
        val k = minOf(1f, SIDE.toFloat() / long)
        // wykrywacz wymaga RGB_565 i parzystej szerokości
        val w = ((bitmap.width * k).toInt() and 1.inv()).coerceAtLeast(2)
        val h = (bitmap.height * k).toInt().coerceAtLeast(2)
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val small = scaled.copy(Bitmap.Config.RGB_565, false) ?: return emptyList()
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val found = FaceDetector(w, h, MAX_FACES).findFaces(small, faces)
            (0 until found).mapNotNull {
                faces[it]?.takeIf { f -> f.confidence() >= MIN_CONFIDENCE }?.let { f -> box(f, w, h) }
            }
        } catch (_: IllegalArgumentException) {
            emptyList()
        } catch (_: OutOfMemoryError) {
            emptyList()
        } catch (_: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    private fun box(face: FaceDetector.Face, w: Int, h: Int): FaceBox {
        val mid = PointF()
        face.getMidPoint(mid)
        val eyes = face.eyesDistance()
        val cy = mid.y + CENTER_SHIFT * eyes
        return FaceBox(
            ((mid.x - HALF_WIDTH * eyes) / w).coerceIn(0f, 1f),
            ((cy - HALF_HEIGHT * eyes) / h).coerceIn(0f, 1f),
            ((mid.x + HALF_WIDTH * eyes) / w).coerceIn(0f, 1f),
            ((cy + HALF_HEIGHT * eyes) / h).coerceIn(0f, 1f)
        )
    }
}
