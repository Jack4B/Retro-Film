package app.retrofilm.feature.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("feature:batch", BatchModule.NAME)
    }
}
