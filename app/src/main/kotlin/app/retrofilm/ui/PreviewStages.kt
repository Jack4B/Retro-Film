package app.retrofilm.ui

import android.graphics.Bitmap
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.Geometry

/**
 * Pamięć etapów podglądu: każdy etap trzyma jeden ostatni wynik razem z tym, od czego zależy (wejście porównywane
 * tożsamością bitmapy, parametry – równością). Zmiana filmu nie unieważnia kadru ani korekt, zmiana ramki nie
 * unieważnia niczego poza samym złożeniem. Nowe zdjęcie to nowa bitmapa bazowa, więc wszystko liczy się od nowa.
 */
internal class PreviewStages {
    /** Od czego zależy wynik po filmie i wykończeniu (bez ramki). */
    data class LookKey(val film: String, val intensity: Float, val finishing: Adjustments)

    private class Slot<K>(val input: Bitmap, val key: K, val output: Bitmap)

    private var crop: Slot<Geometry>? = null
    private var srgb: Slot<Unit>? = null
    private var corrected: Slot<Pair<Adjustments, Boolean>>? = null
    private var look: Slot<LookKey>? = null

    /** Ile razy etap korekt był faktycznie liczony (do testów). */
    var correctRuns = 0
        private set

    fun cropped(base: Bitmap, g: Geometry, make: () -> Bitmap): Bitmap =
        crop.hit(base, g) ?: make().also { crop = Slot(base, g, it) }

    fun srgb(small: Bitmap, make: () -> Bitmap): Bitmap =
        srgb.hit(small, Unit) ?: make().also { srgb = Slot(small, Unit, it) }

    fun corrected(src: Bitmap, adj: Adjustments, deband: Boolean, make: () -> Bitmap): Bitmap {
        val key = adj.corrections() to deband
        return corrected.hit(src, key) ?: make().also {
            correctRuns++
            corrected = Slot(src, key, it)
        }
    }

    fun clear() {
        crop = null
        srgb = null
        corrected = null
        look = null
    }

    fun hasLook(corrected: Bitmap, key: LookKey): Boolean = look.hit(corrected, key) != null

    suspend fun look(corrected: Bitmap, key: LookKey, make: suspend () -> Bitmap): Bitmap =
        look.hit(corrected, key) ?: make().also { look = Slot(corrected, key, it) }

    private fun <K> Slot<K>?.hit(input: Bitmap, key: K): Bitmap? =
        this?.takeIf { it.input === input && it.key == key }?.output
}
