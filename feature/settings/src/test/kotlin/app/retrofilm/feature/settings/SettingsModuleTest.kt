package app.retrofilm.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsModuleTest {
    @Test
    fun nameIsSet() {
        assertEquals("feature:settings", SettingsModule.NAME)
    }
}

class ReflowLicenseTest {
    @Test
    fun joinsLinesKeepsParagraphs() {
        assertEquals("a b\n\nc", reflowLicense("a\nb\n\nc\n"))
    }
}
