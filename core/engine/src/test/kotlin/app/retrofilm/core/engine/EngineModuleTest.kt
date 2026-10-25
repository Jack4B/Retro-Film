package app.retrofilm.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("core:engine", EngineModule.NAME)
    }
}
