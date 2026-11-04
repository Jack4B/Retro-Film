package app.retrofilm.pipeline

import kotlin.math.max

/**
 * Postęp jednej sesji zapisu 0..1 (I; J: obiekt per sesja zamiast globalnego – równoległy podgląd lub druga
 * sesja nie miesza się z paskiem): etapy [ExportJob] ustawiają znaczniki, a [StripPipeline] w zakresie
 * [range] raportuje ukończone kafle – pasek jest określony zamiast „kręciołka”. Tylko odczyt z UI (polling).
 */
class SaveProgress {
    @Volatile var value = 0f
        private set

    @Volatile private var from = 0f

    @Volatile private var to = 0f

    /** Ustawia postęp (nie cofa). */
    fun mark(v: Float) {
        value = max(value, v.coerceIn(0f, 1f))
    }

    /** Kafle potoku od teraz wypełniają przedział [a, b]; [close] kończy. */
    fun range(a: Float, b: Float) {
        mark(a)
        from = a
        to = b
    }

    fun close() {
        mark(to)
        from = 0f
        to = 0f
    }

    internal fun tiles(done: Int, total: Int) {
        if (to > from && total > 0) mark(from + (to - from) * done / total)
    }
}
