package app.retrofilm.core.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("core:frame", FrameModule.NAME)
    }
}
