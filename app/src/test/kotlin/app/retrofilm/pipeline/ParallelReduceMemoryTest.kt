package app.retrofilm.pipeline

import org.junit.Assert.assertTrue
import org.junit.Test

/** R: peak akumulatorów przy semanticMap ≤ ~25 MB (nie 8×9.8 MB). */
class ParallelReduceMemoryTest {
    @Test fun accumulatorPeakBytesStayUnderBudget() {
        val low = Lowres.forSize(2560, 1920, SemanticSharpen.TARGET)
        val cells = low.w.toLong() * low.h
        val perAcc = 3L * cells * 8 // DoubleArray
        val groups = Parallel.REDUCE_GROUPS_HEAVY
        val peak = perAcc * groups
        assertTrue(
            "cells=$cells groups=$groups peak=${peak / 1_000_000}MB",
            peak <= 25L * 1_000_000
        )
        assertTrue(groups <= 2)
        assertTrue(cells <= Lowres.MAX_CELLS.toLong())
    }
}
