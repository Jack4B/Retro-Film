package app.retrofilm.pipeline

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * Równoległe przetwarzanie niezależnych kawałków obrazu (paski wierszy). Wynik nie zależy od liczby wątków, bo
 * każdy kawałek zapisuje tylko swoje dane, a kolejność wykonania nie ma znaczenia. Wywołanie z wątku roboczego (np.
 * etap wywołany z kawałka) biegnie sekwencyjnie, więc pula nie może się zakleszczyć. Liczba wątków jest ograniczona
 * ([MAX_WORKERS]), bo każdy kawałek ma własne bufory robocze (pamięć na telefonie).
 */
internal object Parallel {
    private const val MAX_WORKERS = 8
    private const val CHUNK = 1 shl 18
    internal const val REDUCE_GROUPS = 8

    val workers: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_WORKERS)

    private val insideWorker: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    private val pool: ExecutorService by lazy {
        Executors.newFixedThreadPool(workers - 1) { task ->
            Thread(task, "retrofilm-worker").apply { isDaemon = true }
        }
    }

    /** Wywołuje [body] dla kawałków 0 until [count]; kawałki rozdzielane dynamicznie między wątki. */
    fun forEach(count: Int, body: (Int) -> Unit): Unit = forEach(count, { }, workers) { _, i -> body(i) }

    /**
     * Jak [forEach], ale każdy wątek dostaje własny stan z [newState] (np. [AdjustmentProcessor], który ma bufory
     * robocze i nie jest bezpieczny wątkowo).
     */
    fun <S> forEach(count: Int, newState: () -> S, maxThreads: Int = workers, body: (S, Int) -> Unit) {
        val threads = minOf(workers, count, maxThreads)
        if (threads <= 1 || insideWorker.get()) {
            val state = newState()
            val serial = { for (i in 0 until count) body(state, i) }
            // limit wątków z budżetu pamięci (I): zagnieżdżone pętle też zostają w tym wątku
            if (maxThreads < workers && !insideWorker.get()) asWorker(serial) else serial()
            return
        }
        val next = AtomicInteger()
        val run = { drain(next, count, newState, body) }
        val helpers = List(threads - 1) { pool.submit { asWorker(run) } }
        val own = runCatching { asWorker(run) }
        // czekamy na wszystkich, także po błędzie, żeby żaden wątek nie pisał do danych po powrocie
        val failure = helpers.mapNotNull { awaitFailure(it) }.firstOrNull() ?: own.exceptionOrNull()
        if (failure != null) throw failure
    }

    private fun <S> drain(next: AtomicInteger, count: Int, newState: () -> S, body: (S, Int) -> Unit) {
        val state = newState()
        var i = next.getAndIncrement()
        while (i < count) {
            body(state, i)
            i = next.getAndIncrement()
        }
    }

    private fun asWorker(block: () -> Unit) {
        insideWorker.set(true)
        try {
            block()
        } finally {
            insideWorker.set(false)
        }
    }

    private fun awaitFailure(f: Future<*>): Throwable? = try {
        f.get()
        null
    } catch (e: ExecutionException) {
        e.cause ?: e
    }

    /**
     * Redukcja deterministyczna: kawałki 0 until [count] dzielone są na stałe grupy ([REDUCE_GROUPS], niezależnie od
     * liczby wątków i harmonogramu), każda grupa ma własny akumulator z [newAcc] i przetwarza swoje kawałki po kolei,
     * a akumulatory łączone są przez [merge] w kolejności grup. Dzięki temu suma zmiennoprzecinkowa jest bit w bit
     * taka sama przy każdym uruchomieniu i na każdym telefonie (dynamiczne [forEach] z akumulatorem na wątek dawało
     * różny skład sum).
     */
    fun <A> reduce(count: Int, newAcc: () -> A, merge: (into: A, other: A) -> Unit, body: (A, Int) -> Unit): A {
        val groups = minOf(REDUCE_GROUPS, count).coerceAtLeast(1)
        val accs = List(groups) { newAcc() }
        forEach(groups) { g ->
            val from = g * count / groups
            val to = (g + 1) * count / groups
            for (i in from until to) body(accs[g], i)
        }
        val first = accs[0]
        for (g in 1 until groups) merge(first, accs[g])
        return first
    }

    /** Dzieli indeksy 0 until [count] na zakresy po [CHUNK] elementów i wywołuje [body] z zakresem [from, to). */
    fun range(count: Int, body: (from: Int, to: Int) -> Unit) = rows(count, CHUNK, body)

    /** Dzieli wiersze 0 until [height] na paski po [rowsPerChunk] i wywołuje [body] z zakresem [y0, y1). */
    fun rows(height: Int, rowsPerChunk: Int, body: (y0: Int, y1: Int) -> Unit) {
        val chunks = (height + rowsPerChunk - 1) / rowsPerChunk
        forEach(chunks) { c -> body(c * rowsPerChunk, minOf(height, (c + 1) * rowsPerChunk)) }
    }
}
