package app.retrofilm.ui

import app.retrofilm.core.export.SignatureTooLongException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Wynik zapisu serii zdjęć: ile zapisano i ile się nie udało. */
data class BatchResult(val saved: Int, val failed: Int)

/**
 * Zapisuje kolejno [items] funkcją [one]. Błąd jednego zdjęcia nie przerywa serii; anulowanie – tak.
 * [onProgress] dostaje indeks (od 0) zdjęcia, które właśnie jest przetwarzane.
 */
suspend fun <T> exportBatch(items: List<T>, onProgress: (Int) -> Unit = {}, one: suspend (T) -> Unit): BatchResult {
    var saved = 0
    var failed = 0
    items.forEachIndexed { index, item ->
        onProgress(index)
        try {
            one(item)
            saved++
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            failed++
        } catch (_: IllegalStateException) {
            failed++
        } catch (_: SecurityException) {
            failed++
        } catch (_: SignatureTooLongException) {
            failed++
        }
    }
    return BatchResult(saved, failed)
}
