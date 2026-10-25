package app.retrofilm.core.engine.pack

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filmy startowe z APK nigdy nie są na liście do pobrania (catalog-144.json pozostaje niezmieniony). */
class BundledExclusionTest {
    private val all = PackCatalog.parse(File("../../app/src/main/assets/${PackCatalog.ASSET}").readText())
    private val bundled = PackCatalog.bundledIds(File("src/main/assets/luts/catalog.json").readText())

    @Test
    fun startersNeverDownloadable() {
        val films = bundled - "identity"
        assertEquals(10, films.size)
        assertTrue(all.map { it.id }.containsAll(films))
        val list = PackCatalog.excludeBundled(all, bundled)
        assertEquals(all.size - films.size, list.size)
        for (e in list) assertFalse(e.id, e.id in bundled)
        assertTrue(list.none { it.bundled })
    }

    @Test
    fun entryFlaggedBundledIsExcludedToo() {
        val e = all.first().copy(bundled = true)
        assertTrue(PackCatalog.excludeBundled(listOf(e), emptySet()).isEmpty())
    }
}
