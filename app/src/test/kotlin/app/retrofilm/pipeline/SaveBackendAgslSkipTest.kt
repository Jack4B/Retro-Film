package app.retrofilm.pipeline

import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.gpu.PlaneLutKernel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** O: po błędzie formatu 22 AGSL nie wraca do kandydatów w tym procesie. */
class SaveBackendAgslSkipTest {
    @Before fun setUp() {
        SaveBackend.reset()
        SaveBackend.skipAgsl = false
        SaveBackend.gpuEnabled = true
    }

    @After fun tearDown() {
        SaveBackend.reset()
        SaveBackend.skipAgsl = false
        SaveBackend.gpuEnabled = false
        SaveBackend.candidates = { emptyList() }
    }

    @Test fun format22MarksAgslSkipped() {
        assertFalse(SaveBackend.skipAgsl)
        SaveBackend.noteAgslRejected("UnsupportedOperationException: Invalid format specified 22")
        assertTrue(SaveBackend.skipAgsl)
        var persisted = false
        SaveBackend.onSkipAgsl = { persisted = it }
        SaveBackend.skipAgsl = false
        SaveBackend.noteAgslRejected("Invalid format specified 22")
        assertTrue(persisted)
    }
}
