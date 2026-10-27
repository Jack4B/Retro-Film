package app.retrofilm.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/** Jaka część szerokości zdjęcia musi być przesunięta palcem, żeby przełączyć zdjęcie serii. */
private const val SWIPE_WIDTH_FRACTION = 0.2f

/** Przesunięcie musi być wyraźnie poziome (przynajmniej dwa razy dłuższe w poziomie niż w pionie). */
private const val SWIPE_HORIZONTAL_RATIO = 2f

/** Powyżej tej skali zdjęcie jest powiększone, więc palec je przesuwa zamiast przełączać zdjęcie serii. */
private const val UNZOOMED = 1.01f

/** Zdjęcie serii przełącza się palcem tylko bez kadrowania i bez powiększenia. */
internal fun canSwipe(crop: Boolean, zoom: ZoomState) = !crop && zoom.scale <= UNZOOMED

/**
 * Które zdjęcie serii pokazać po przesunięciu palca o ([dx], [dy]) na zdjęciu o szerokości [width]: `1` = następne
 * (przesunięcie w lewo), `-1` = poprzednie (w prawo), `0` = bez zmiany (za krótkie, zbyt pionowe albo dotyk wieloma
 * palcami, czyli szczypanie).
 */
internal fun swipeStep(dx: Float, dy: Float, width: Float, multiTouch: Boolean): Int = when {
    multiTouch || abs(dx) < width * SWIPE_WIDTH_FRACTION || abs(dx) < abs(dy) * SWIPE_HORIZONTAL_RATIO -> 0
    dx < 0f -> 1
    else -> -1
}

/**
 * Przesunięcie palcem w lewo albo w prawo przełącza zdjęcie serii (jak karuzela). Gest tylko obserwuje dotyk, niczego
 * nie zjada, więc szczypanie, przesuwanie powiększonego zdjęcia i dwukrotne dotknięcie działają jak dotąd; [allowed]
 * mówi, czy teraz wolno przełączać (np. nie przy powiększeniu).
 */
internal fun Modifier.photoSwipe(allowed: () -> Boolean, onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var multiTouch = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) multiTouch = true
            event.changes.firstOrNull { it.id == first.id }?.let { total += it.positionChange() }
        } while (event.changes.any { it.pressed })
        if (allowed()) {
            val step = swipeStep(total.x, total.y, size.width.toFloat(), multiTouch)
            if (step != 0) onStep(step)
        }
    }
}

/**
 * Przełączanie zdjęć serii na podglądzie: przesunięcie palcem ([photoSwipe]) i akcje „Następne” / „Poprzednie” dla
 * TalkBack. Dla pojedynczego zdjęcia ([series] = false) nic nie dodaje.
 */
internal fun Modifier.photoSeries(
    series: Boolean,
    allowed: () -> Boolean,
    onStep: (Int) -> Unit,
    nextLabel: String,
    previousLabel: String
): Modifier = if (!series) {
    this
} else {
    photoSwipe(allowed, onStep).semantics {
        customActions = listOf(
            CustomAccessibilityAction(nextLabel) {
                onStep(1)
                true
            },
            CustomAccessibilityAction(previousLabel) {
                onStep(-1)
                true
            }
        )
    }
}
