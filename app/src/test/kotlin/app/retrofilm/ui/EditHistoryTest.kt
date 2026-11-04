package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test fun freshHistoryHasNothingToUndoOrRedo() {
        val h = EditHistory("a")
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test fun undoReturnsPreviousStatesInReverseOrder() {
        val h = EditHistory("a")
        h.record("b")
        h.record("c")
        assertEquals("b", h.undo())
        assertEquals("a", h.undo())
        assertNull(h.undo())
        assertEquals("a", h.current)
    }

    @Test fun redoReappliesUndoneStates() {
        val h = EditHistory("a")
        h.record("b")
        h.record("c")
        h.undo()
        h.undo()
        assertEquals("b", h.redo())
        assertEquals("c", h.redo())
        assertFalse(h.canRedo)
    }

    @Test fun newEditAfterUndoDropsRedoBranch() {
        val h = EditHistory("a")
        h.record("b")
        h.undo()
        h.record("x")
        assertFalse(h.canRedo)
        assertEquals("a", h.undo())
    }

    @Test fun recordingTheSameStateIsIgnored() {
        val h = EditHistory("a")
        h.record("a")
        assertFalse(h.canUndo)
        h.record("b")
        h.record("b")
        assertEquals("a", h.undo())
        assertFalse(h.canUndo)
    }

    @Test fun historyIsBoundedAndForgetsOldestStates() {
        val h = EditHistory(0, limit = 3)
        for (i in 1..10) h.record(i)
        assertEquals(9, h.undo())
        assertEquals(8, h.undo())
        assertEquals(7, h.undo())
        assertNull(h.undo())
    }

    @Test fun resetStartsFromNewStateWithEmptyStacks() {
        val h = EditHistory("a")
        h.record("b")
        h.reset("z")
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("z", h.current)
    }

    @Test fun observableVersionChangesOnEveryMutation() {
        val h = EditHistory("a")
        val v0 = h.version
        h.record("b")
        assertTrue(h.version > v0)
        val v1 = h.version
        h.undo()
        assertTrue(h.version > v1)
    }
}
