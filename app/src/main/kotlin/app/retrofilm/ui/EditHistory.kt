package app.retrofilm.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

private const val DEFAULT_LIMIT = 50

/**
 * Historia „Cofnij/Ponów” dla migawek edycji ([PhotoEdit]). [current] to stan, który widzi użytkownik; [record]
 * dopisuje nowy stan (identyczny jest pomijany), [undo] i [redo] zwracają stan do przywrócenia albo `null`.
 * Nowa zmiana po cofnięciu kasuje gałąź „Ponów”. Historia ma limit [limit] kroków (najstarsze są zapominane).
 * [version] jest obserwowalne w Compose, żeby przyciski odświeżały dostępność.
 */
class EditHistory<T>(initial: T, private val limit: Int = DEFAULT_LIMIT) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var current: T = initial
        private set

    var version by mutableIntStateOf(0)
        private set

    val canUndo: Boolean get() = version >= 0 && past.isNotEmpty()
    val canRedo: Boolean get() = version >= 0 && future.isNotEmpty()

    fun record(state: T) {
        if (state == current) return
        past.addLast(current)
        while (past.size > limit) past.removeFirst()
        future.clear()
        current = state
        version++
    }

    fun undo(): T? {
        val prev = past.removeLastOrNull() ?: return null
        future.addLast(current)
        current = prev
        version++
        return prev
    }

    fun redo(): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        current = next
        version++
        return next
    }

    fun reset(state: T) {
        past.clear()
        future.clear()
        current = state
        version++
    }
}
