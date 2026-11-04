package app.retrofilm.core.engine

import android.graphics.Bitmap

/** Nakłada LUT 3D na bitmapę. Wynik to nowa bitmapa ARGB_8888; źródło nie jest modyfikowane. */
interface LutEngine {
    /** Nazwa backendu (do logów i raportów QA). */
    val name: String

    /** Faktycznie używany backend (np. „agsl-tetra” albo „cpu-tetra” po awarii GPU) – do logu czasów. */
    val activeBackend: String get() = name

    /** Powód ostatniego spadku do CPU (backend + wyjątek); null = bez awarii. */
    val fallbackReason: String? get() = null

    /** [intensity] 0..1: 0 = oryginał, 1 = pełny LUT (liniowe mieszanie). */
    suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap

    /** Przygotowuje [lut] z wyprzedzeniem (np. tekstura sąsiedniego filmu); domyślnie nic. */
    fun warm(lut: Lut) = Unit
}
