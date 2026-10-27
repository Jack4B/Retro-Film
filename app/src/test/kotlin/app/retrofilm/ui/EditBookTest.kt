package app.retrofilm.ui

import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBookTest {
    private val warm = PhotoEdit("kodak/portra_400", 0.8f, Adjustments(exposure = 0.5f), Geometry())
    private val bw = PhotoEdit("ilford/hp5", 1f, Adjustments(contrast = 0.4f), Geometry(zoom = 2f))

    @Test fun byDefaultAllPhotosShareTheSameEdit() {
        val book = EditBook()
        book.reset(warm)
        for (i in 0..3) assertEquals(warm, book.editFor(i))
        assertEquals(0, book.individualCount)
    }

    @Test fun sharedChangesOnOnePhotoReachTheOthersAfterCommit() {
        val book = EditBook()
        book.reset(PhotoEdit())
        book.commit(0, warm)
        assertEquals(warm, book.editFor(1))
        assertEquals(warm, book.editFor(2))
    }

    @Test fun inSeriesAChangeStaysWithTheEditedPhotoOnly() {
        val book = EditBook()
        book.reset(PhotoEdit())
        book.commit(1, warm, series = true)
        assertTrue(book.isIndividual(1))
        assertEquals(warm, book.editFor(1))
        assertEquals("pozostałe bez zmian", PhotoEdit(), book.editFor(0))
        assertEquals(PhotoEdit(), book.editFor(2))
        book.commit(2, bw, series = true)
        assertEquals(warm, book.editFor(1))
        assertEquals(bw, book.editFor(2))
        assertEquals(PhotoEdit(), book.editFor(0))
    }

    @Test fun inSeriesUntouchedPhotoStaysShared() {
        val book = EditBook()
        book.reset(warm)
        book.commit(0, warm, series = true)
        assertFalse(book.isIndividual(0))
        assertEquals(0, book.individualCount)
    }

    @Test fun individualPhotoKeepsItsOwnEditAndSharedStaysUntouched() {
        val book = EditBook()
        book.reset(warm)
        book.makeIndividual(1, warm)
        assertTrue(book.isIndividual(1))
        book.commit(1, bw)
        assertEquals(bw, book.editFor(1))
        assertEquals("pozostałe bez zmian", warm, book.editFor(0))
        assertEquals(warm, book.editFor(2))
        book.commit(0, warm.copy(intensity = 0.3f))
        assertEquals("wspólne idą do zdjęć bez osobnej edycji", 0.3f, book.editFor(2).intensity, 0f)
        assertEquals("zdjęcie osobne nie zmienia się od wspólnych", bw, book.editFor(1))
    }

    @Test fun makingIndividualKeepsTheEditJustMadeInSharedMode() {
        val book = EditBook()
        book.reset(PhotoEdit())
        book.makeIndividual(0, warm)
        assertEquals("zmiana zrobiona tuż przed przełączeniem zostaje wspólna", warm, book.editFor(5))
    }

    @Test fun returningToSharedDropsTheIndividualEdit() {
        val book = EditBook()
        book.reset(warm)
        book.makeIndividual(2, warm)
        book.commit(2, bw)
        assertEquals(bw, book.editFor(2))
        val back = book.makeShared(2)
        assertEquals(warm, back)
        assertFalse(book.isIndividual(2))
        assertEquals(warm, book.editFor(2))
    }

    @Test fun applyToAllReplacesEverything() {
        val book = EditBook()
        book.reset(warm)
        book.makeIndividual(0, bw)
        book.makeIndividual(3, bw.copy(film = "x"))
        book.applyToAll(bw)
        assertEquals(0, book.individualCount)
        for (i in 0..4) assertEquals(bw, book.editFor(i))
    }

    @Test fun commitOfAnIndividualPhotoDoesNotLeakIntoShared() {
        val book = EditBook()
        book.reset(warm)
        book.makeIndividual(1, warm)
        book.commit(1, bw)
        book.commit(0, warm)
        assertEquals(warm, book.editFor(0))
        assertEquals(bw, book.editFor(1))
    }
}
