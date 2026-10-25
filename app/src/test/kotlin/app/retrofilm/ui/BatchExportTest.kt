package app.retrofilm.ui

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchExportTest {
    @Test fun savesAllAndReportsProgress() = runBlocking {
        val progress = mutableListOf<Int>()
        val done = mutableListOf<Int>()
        val r = exportBatch(listOf(10, 20, 30), { progress += it }) { done += it }
        assertEquals(BatchResult(3, 0), r)
        assertEquals(listOf(0, 1, 2), progress)
        assertEquals(listOf(10, 20, 30), done)
    }

    @Test fun oneFailureDoesNotStopTheRest() = runBlocking {
        val done = mutableListOf<Int>()
        val r = exportBatch(listOf(1, 2, 3)) { if (it == 2) throw IOException("brak pliku") else done += it }
        assertEquals(BatchResult(2, 1), r)
        assertEquals(listOf(1, 3), done)
    }

    @Test fun cancellationIsNotSwallowed() {
        assertThrows(CancellationException::class.java) {
            runBlocking { exportBatch(listOf(1, 2)) { throw CancellationException("stop") } }
        }
    }

    @Test fun emptyListSavesNothing() = runBlocking {
        assertEquals(BatchResult(0, 0), exportBatch(emptyList<Int>()) { error("nie powinno być wywołane") })
    }
}
