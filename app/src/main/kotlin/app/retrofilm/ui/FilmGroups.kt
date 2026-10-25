package app.retrofilm.ui

/** Grupowanie filmów w Edytorze: marka (Kodak, Fuji...) → rodzaj (negatyw, slajd...) oraz ulubione. */
object FilmGroups {
    const val OTHER = "other"

    /** Marki z własną grupą, w kolejności wyświetlania; reszta trafia do [OTHER]. */
    val Brands = listOf("kodak", "fuji", "polaroid", "ilford")

    /** Marka z nazwy pliku w id (`negative_color/fuji_superia_200` → `fuji`). */
    fun brandOf(id: String): String {
        val token = id.substringAfter('/').substringBefore('_').lowercase()
        return if (token in Brands) token else OTHER
    }

    /** Marki, w których jest co najmniej jeden film, w stałej kolejności. */
    fun brandsPresent(films: List<FilmOption>): List<String> =
        (Brands + OTHER).filter { b -> films.any { brandOf(it.id) == b } }

    /** Rodzaje filmów (kategorie z id) w danej marce, w kolejności pierwszego wystąpienia. */
    fun typesIn(films: List<FilmOption>, brand: String): List<String> =
        films.filter { brandOf(it.id) == brand }.map { it.category }.distinct()

    /** Filmy do pokazania: ulubione albo marka (z opcjonalnym zawężeniem do rodzaju [type]). */
    fun filter(
        films: List<FilmOption>,
        brand: String,
        type: String?,
        onlyFavorites: Boolean,
        favorites: Set<String>
    ): List<FilmOption> = films.filter {
        if (onlyFavorites) {
            it.id in favorites
        } else {
            brandOf(it.id) == brand && (type == null || it.category == type)
        }
    }
}
