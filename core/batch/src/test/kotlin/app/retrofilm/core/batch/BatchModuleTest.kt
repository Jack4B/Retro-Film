package app.retrofilm.core.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("core:batch", BatchModule.NAME)
    }
}
