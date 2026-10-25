package app.retrofilm.ui.library

import app.retrofilm.core.ui.lut.FailReason
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.PackState
import app.retrofilm.ui.FilmOption
import app.retrofilm.ui.IDENTITY_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelTest {
    private fun pack(id: String, name: String, state: PackState, bytes: Long = 9_000_000L) =
        LutPack(id, name, bytes, bundled = false, state = state)

    private val packs = listOf(
        pack("bw/agfa_apx_100", "Agfa APX 100", PackState.NotDownloaded),
        pack("negative_new/fuji_160c", "Fuji 160C", PackState.Ready),
        pack("negative_new/fuji_160c_plus", "Fuji 160C +", PackState.Downloading(0.4f)),
        pack("colorslide/fuji_astia_100f", "Fuji Astia 100F", PackState.Failed(FailReason.NETWORK))
    )
    private val films = listOf(
        FilmOption(IDENTITY_ID, null),
        FilmOption("negative_new/kodak_portra_400", "Kodak Portra 400"),
        FilmOption("negative_new/fuji_160c", "Fuji 160C") // pobrany: jest też w pakietach
    )

    private val items = LibraryModel.items(films, packs)

    @Test
    fun mergesPacksAndStarterFilmsWithoutDuplicatesOrIdentity() {
        assertEquals(5, items.size)
        assertEquals(1, items.count { it.id == "negative_new/fuji_160c" })
        assertTrue(items.none { it.id == IDENTITY_ID })
    }

    @Test
    fun mapsPackStatesAndProgress() {
        val byId = items.associateBy { it.id }
        assertEquals(FilmStatus.OnDevice, byId.getValue("negative_new/fuji_160c").status)
        assertEquals(FilmStatus.OnDevice, byId.getValue("negative_new/kodak_portra_400").status)
        assertEquals(FilmStatus.NotDownloaded, byId.getValue("bw/agfa_apx_100").status)
        assertEquals(FilmStatus.Failed, byId.getValue("colorslide/fuji_astia_100f").status)
        val downloading = byId.getValue("negative_new/fuji_160c_plus")
        assertEquals(FilmStatus.Downloading, downloading.status)
        assertEquals(0.4f, downloading.progress, 0f)
    }

    @Test
    fun filmsOnDeviceComeFirstThenAlphabetically() {
        val onDeviceCount = items.count { it.status == FilmStatus.OnDevice }
        assertTrue(items.take(onDeviceCount).all { it.status == FilmStatus.OnDevice })
        assertEquals(listOf("Fuji 160C", "Kodak Portra 400"), items.take(onDeviceCount).map { it.name })
    }

    @Test
    fun filtersByQueryCategoryFavoritesAndAvailability() {
        fun ids(f: LibraryFilter, fav: Set<String> = emptySet()) =
            LibraryModel.apply(items, f, fav).map { it.id }.toSet()
        assertEquals(
            setOf("negative_new/fuji_160c", "negative_new/fuji_160c_plus"),
            ids(LibraryFilter(query = " 160c "))
        )
        assertEquals(setOf("bw/agfa_apx_100"), ids(LibraryFilter(category = "bw")))
        assertEquals(setOf("bw/agfa_apx_100"), ids(LibraryFilter(onlyFavorites = true), setOf("bw/agfa_apx_100")))
        assertEquals(2, ids(LibraryFilter(onlyOnDevice = true)).size)
        assertTrue(ids(LibraryFilter(query = "nie ma takiego")).isEmpty())
    }

    @Test
    fun countsCategoriesAndListsDownloadable() {
        assertEquals(
            listOf("bw" to 1, "colorslide" to 1, "negative_new" to 3),
            LibraryModel.categoryCounts(items)
        )
        val ids = LibraryModel.downloadable(items).map { it.id }.toSet()
        assertEquals(setOf("bw/agfa_apx_100", "colorslide/fuji_astia_100f"), ids)
        assertFalse("negative_new/fuji_160c_plus" in ids)
    }
}
