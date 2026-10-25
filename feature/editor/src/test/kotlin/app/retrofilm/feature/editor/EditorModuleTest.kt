package app.retrofilm.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("feature:editor", EditorModule.NAME)
    }
}
