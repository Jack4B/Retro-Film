package app.retrofilm.engine

import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory
import app.retrofilm.core.ui.engine.FilmLut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pobrane filmy (D8) są widoczne w silniku obok filmów startowych i da się je załadować. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineFilmEngineDownloadedTest {
    private val downloadedLut = Lut(2, ByteArray(Lut.expectedBytes(2, 1)))

    private class FakeDownloaded(val films: List<FilmLut>, val lut: Lut) : DownloadedFilms {
        var calls = 0
        override fun available() = films

        override fun lutFor(id: String): Lut? = lut.takeIf { films.any { it.id == id } }.also { calls++ }
    }

    private fun engine(downloaded: DownloadedFilms?): EngineFilmEngine {
        val catalog = LutCatalog(RuntimeEnvironment.getApplication().assets)
        return EngineFilmEngine(catalog, LutEngineFactory.create(), downloaded = { downloaded })
    }

    @Test
    fun withoutDownloadsOnlyBundledFilmsAreListed() {
        val e = engine(null)
        assertTrue(e.availableLuts().isNotEmpty())
        assertNull(e.load("negative_new/fuji_400h"))
    }

    @Test
    fun downloadedFilmIsListedAndLoadable() {
        val film = FilmLut("negative_new/fuji_400h", "Fuji 400H")
        val e = engine(FakeDownloaded(listOf(film), downloadedLut))
        assertTrue(film in e.availableLuts())
        assertSame(downloadedLut, e.load(film.id))
        assertNull(e.load("negative_new/nie_pobrany"))
    }

    @Test
    fun bundledFilmIsNotDuplicatedAndStillOpensFromApk() {
        val bundled = engine(null).availableLuts().first { it.id != "identity" }
        val fake = FakeDownloaded(listOf(FilmLut(bundled.id, "Inna nazwa")), downloadedLut)
        val e = engine(fake)
        assertEquals(1, e.availableLuts().count { it.id == bundled.id })
        assertNotNull(e.load(bundled.id))
        assertEquals(0, fake.calls)
    }
}
