package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Kolejność zakładek jest kolejnością pracy (D15): kadr, korekty, film, wykończenie, ramka. */
class EditorTabsTest {
    @Test fun tabsFollowTheWorkflow() {
        assertEquals(
            listOf("Crop", "Adjust", "Film", "Finish", "Frame"),
            EditorTab.entries.map { it.name }
        )
    }

    @Test fun everyTabHasItsOwnLabel() {
        assertEquals(EditorTab.entries.size, EditorTab.entries.map { it.label }.toSet().size)
    }
}
