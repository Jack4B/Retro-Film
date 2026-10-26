package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSheetTest {
    @Test fun dragUpExpandsStepByStep() {
        assertEquals(SheetMode.Compact, moved(SheetMode.Collapsed, -80f))
        assertEquals(SheetMode.Full, moved(SheetMode.Compact, -80f))
        assertEquals(SheetMode.Full, moved(SheetMode.Full, -80f))
    }

    @Test fun dragDownCollapsesStepByStep() {
        assertEquals(SheetMode.Compact, moved(SheetMode.Full, 80f))
        assertEquals(SheetMode.Collapsed, moved(SheetMode.Compact, 80f))
        assertEquals(SheetMode.Collapsed, moved(SheetMode.Collapsed, 80f))
    }

    @Test fun smallMovesAreIgnored() {
        for (m in SheetMode.entries) {
            assertEquals(m, moved(m, 10f))
            assertEquals(m, moved(m, -10f))
            assertEquals(m, moved(m, 0f))
        }
    }
}
