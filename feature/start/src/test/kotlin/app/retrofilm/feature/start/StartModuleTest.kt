package app.retrofilm.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

class StartModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("feature:start", StartModule.NAME)
    }
}
