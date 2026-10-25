package app.retrofilm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmGroupsTest {
    private val films = listOf(
        FilmOption("negative_color/kodak_portra_160", "Kodak Portra 160"),
        FilmOption("colorslide/kodak_ektachrome_100", "Kodak Ektachrome 100"),
        FilmOption("negative_color/fuji_superia_200", "Fuji Superia 200"),
        FilmOption("bw/ilford_hp5", "Ilford HP5"),
        FilmOption("bw/agfa_apx_100", "Agfa APX 100"),
        FilmOption("instant_consumer/polaroid_600", "Polaroid 600")
    )

    @Test fun brandsFromFileName() {
        assertEquals("kodak", FilmGroups.brandOf("negative_color/kodak_portra_160"))
        assertEquals("fuji", FilmGroups.brandOf("negative_color/fuji_superia_200"))
        assertEquals(FilmGroups.OTHER, FilmGroups.brandOf("bw/agfa_apx_100"))
    }

    @Test fun brandsPresentKeepFixedOrder() {
        assertEquals(listOf("kodak", "fuji", "polaroid", "ilford", "other"), FilmGroups.brandsPresent(films))
        assertEquals(listOf("fuji"), FilmGroups.brandsPresent(films.filter { "fuji" in it.id }))
    }

    @Test fun typesInBrand() {
        assertEquals(listOf("negative_color", "colorslide"), FilmGroups.typesIn(films, "kodak"))
    }

    @Test fun filterByBrandTypeAndFavorites() {
        val favs = setOf("bw/ilford_hp5", "negative_color/fuji_superia_200")
        assertEquals(2, FilmGroups.filter(films, "kodak", null, false, favs).size)
        assertEquals(
            listOf("colorslide/kodak_ektachrome_100"),
            FilmGroups.filter(films, "kodak", "colorslide", false, favs).map { it.id }
        )
        assertEquals(
            setOf("negative_color/fuji_superia_200", "bw/ilford_hp5"),
            FilmGroups.filter(films, "kodak", null, true, favs).map { it.id }.toSet()
        )
    }
}
