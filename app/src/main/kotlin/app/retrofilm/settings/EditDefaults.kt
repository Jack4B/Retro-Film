package app.retrofilm.settings

import app.retrofilm.core.export.JpegExporter

/**
 * Ustawienia domyślne nowej edycji (ustawiane przez „Zapisz jako domyślne”): jakość i rozmiar zapisu oraz ramka
 * z podpisem. Same typy proste (nazwy enumów jako tekst), żeby repozytorium nie zależało od klas potoku.
 * `null` w [frameAspect], [frameMargin] i [signatureFont] = wartość fabryczna.
 */
data class EditDefaults(
    val quality: Int = FACTORY_QUALITY,
    val maxSide: Int = 0,
    val chroma444: Boolean = true,
    val keepExif: Boolean = true,
    val displayP3: Boolean = false,
    val frameEnabled: Boolean = false,
    val frameAspect: String? = null,
    val frameMargin: Float? = null,
    val signatureEnabled: Boolean = false,
    val signature: String = "",
    val signatureFont: String? = null
) {
    val isFactory: Boolean get() = this == EditDefaults()

    companion object {
        const val FACTORY_QUALITY = JpegExporter.DEFAULT_QUALITY
        const val MIN_QUALITY = 70
        const val MAX_QUALITY = 100

        /** Wartości spoza zakresu (np. ręcznie zmieniony plik) wracają do bezpiecznych granic. */
        fun sanitized(d: EditDefaults): EditDefaults = d.copy(
            quality = d.quality.coerceIn(MIN_QUALITY, MAX_QUALITY),
            maxSide = d.maxSide.coerceAtLeast(0),
            frameMargin = d.frameMargin?.takeIf { it.isFinite() && it >= 0f }
        )
    }
}
