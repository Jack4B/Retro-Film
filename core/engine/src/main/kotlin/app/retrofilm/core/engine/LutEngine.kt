package app.retrofilm.core.engine

import android.graphics.Bitmap

/** Nakłada LUT 3D na bitmapę. Wynik to nowa bitmapa ARGB_8888; źródło nie jest modyfikowane. */
interface LutEngine {
    /** Nazwa backendu (do logów i raportów QA). */
    val name: String

    /** [intensity] 0..1: 0 = oryginał, 1 = pełny LUT (liniowe mieszanie). */
    suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap
}
