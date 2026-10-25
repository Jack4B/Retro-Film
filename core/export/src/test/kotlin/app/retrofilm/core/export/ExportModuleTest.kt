package app.retrofilm.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("core:export", ExportModule.NAME)
    }
}
