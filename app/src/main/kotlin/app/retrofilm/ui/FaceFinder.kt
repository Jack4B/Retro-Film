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
 *
 * Decyzja (poprawka e, 2026-11): ML Kit Face Detection (wersja z modelem w APK) oceniono jako zamiennik, ale dodaje
 * ok. 6,9 MB na ABI i wysyła anonimowe statystyki użycia do Google (Firebase datatransport), co łamie zasadę
 * „aplikacja działa offline, bez telemetrii”. Zostaje wykrywacz systemowy (nie jest oznaczony jako przestarzały),
 * z gwarancją bezpiecznej ścieżki: bez wykrytej twarzy mapa [app.retrofilm.pipeline.SemanticSharpen] nie wzmacnia
 * gładkiej skóry (mnożnik ≈ 1, test `SemanticSharpenTest.undetectedSkinIsNotBoosted`).
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
        var scaled: Bitmap? = null
        var small: Bitmap? = null
        return try {
            scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            small = scaled.copy(Bitmap.Config.RGB_565, false) ?: return emptyList()
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
        } finally {
            // pośrednie bitmapy (do ~0,5 MB) zwalniane od razu; nigdy nie zwalniamy wejścia wywołującego
            small?.takeIf { it !== bitmap }?.recycle()
            scaled?.takeIf { it !== bitmap && it !== small }?.recycle()
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
