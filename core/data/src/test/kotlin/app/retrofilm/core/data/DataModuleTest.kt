package app.retrofilm.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("core:data", DataModule.NAME)
    }
}
