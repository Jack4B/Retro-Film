package app.retrofilm.feature.luts

import org.junit.Assert.assertEquals
import org.junit.Test

class LutsModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("feature:luts", LutsModule.NAME)
    }
}
