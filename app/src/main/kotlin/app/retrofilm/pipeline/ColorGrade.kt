package app.retrofilm.pipeline

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Nasycenie i vibrance w przestrzeni OKLab (Ottosson 2020), która jest percepcyjnie jednorodna: skalowanie
 * chromy C = √(a²+b²) przy stałej jasności L i odcieniu nie zmienia postrzeganej jasności ani barwy, w
 * przeciwieństwie do mieszania kanałów RGB. Plus (vibrance) wzmacnia głównie kolory mało nasycone i oszczędza
 * odcienie skóry, minus odbarwia równomiernie do szarości. Kolory wychodzące poza gamut sRGB są cofane
 * bisekcją chromy (zamiast przycinania kanałów, które zmienia odcień).
 */
internal class ChromaScaler(private val saturation: Float) {
    private val ok = OkLab()

    /** Przetwarza kolor w kodowaniu gamma (0..1) w miejscu. */
    fun apply(rgb: FloatArray) {
        ok.fromRgb(rgb)
        val l = ok.lab[0]
        val a = ok.lab[1]
        val b = ok.lab[2]
        val chroma = sqrt(a * a + b * b)
        if (chroma < MIN_CHROMA) return
        val k = gain(chroma, OkLab.hueDegrees(a, b))
        ok.toRgb(l, a * k, b * k, rgb)
    }

    private fun gain(chroma: Float, hueDeg: Float): Float {
        if (saturation < 0f) return 1f + saturation
        val muted = (1f - chroma / CHROMA_MAX).coerceIn(0f, 1f)
        return 1f + saturation * muted * skinGuard(hueDeg)
    }

    /** 1 poza odcieniami skóry; w pomarańczowo-różowym zakresie (≈55° w OKLab) mniej o [SKIN_GUARD]. */
    private fun skinGuard(deg: Float): Float {
        val d = (deg - SKIN_HUE) / SKIN_WIDTH
        return 1f - SKIN_GUARD * max(0f, 1f - d * d)
    }

    private companion object {
        const val MIN_CHROMA = 1e-4f
        const val CHROMA_MAX = 0.32f
        const val SKIN_HUE = 55f
        const val SKIN_WIDTH = 40f
        const val SKIN_GUARD = 0.55f
    }
}

/**
 * Kontrast zachowujący barwę: krzywa (sigmoida wokół szarości) działa na jasności (Rec. 709), a kanały są skalowane
 * tym samym stosunkiem Y'/Y, więc odcień i proporcje kolorów zostają (krzywa na każdym kanale osobno podbijałaby
 * nasycenie w cieniach i światłach). Nadmiar powyżej bieli cofa kolor ku szarości o tej samej jasności.
 */
internal class LuminanceContrast(contrast: Float) {
    private val lut = FloatArray(SIZE + 1) { AdjustmentProcessor.contrastCurve(it.toFloat() / SIZE, contrast) }

    fun apply(rgb: FloatArray) {
        val y = LUM_R * rgb[0] + LUM_G * rgb[1] + LUM_B * rgb[2]
        if (y < MIN_Y) {
            rgb.fill(0f)
            return
        }
        val target = lut[(y.coerceIn(0f, 1f) * SIZE + HALF).toInt()]
        val ratio = (target / y).coerceAtMost(MAX_RATIO)
        for (i in 0..2) rgb[i] *= ratio
        val peak = max(rgb[0], max(rgb[1], rgb[2]))
        if (peak > 1f) {
            val s = (1f - target) / (peak - target)
            for (i in 0..2) rgb[i] = target + (rgb[i] - target) * s
        }
    }

    private companion object {
        const val SIZE = 1023
        const val HALF = 0.5f
        const val MIN_Y = 1e-5f
        const val MAX_RATIO = 4f
        const val LUM_R = 0.2126f
        const val LUM_G = 0.7152f
        const val LUM_B = 0.0722f
    }
}

/**
 * Naturalna winieta obiektywu: spadek jasności cos⁴θ (prawo cosinusów), przy kącie połowy pola widzenia 45° daje
 * w rogu −2 EV: f_lin = (1 + d²)^(−2·siła), gdzie d to odległość od środka w jednostkach połowy przekątnej.
 * Przeliczone na kodowanie gamma i zapisane w tablicy po d², więc koszt na piksel to jeden odczyt.
 */
internal class VignetteLut(strength: Float) {
    private val lut = FloatArray(SIZE + 1) {
        val d2 = it.toFloat() / SIZE
        exp(-EXPONENT * strength * ln(1f + d2) * GAMMA_INV)
    }

    /** Mnożnik (gamma) dla d² ∈ 0..1. */
    fun gain(d2: Float): Float = lut[(d2.coerceIn(0f, 1f) * SIZE + HALF).toInt()]

    private companion object {
        const val SIZE = 1023
        const val HALF = 0.5f
        const val EXPONENT = 2f
        const val GAMMA_INV = 0.4545f
    }
}
