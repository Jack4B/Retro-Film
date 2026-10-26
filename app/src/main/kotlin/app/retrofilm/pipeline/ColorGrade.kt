package app.retrofilm.pipeline

import kotlin.math.atan2
import kotlin.math.cbrt
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
    private val toLinear = FloatArray(LUT + 1) { AdjustmentProcessor.srgbToLinear(it.toFloat() / LUT) }
    private val toGamma = FloatArray(LUT + 1) { AdjustmentProcessor.linearToSrgb(it.toFloat() / LUT) }
    private val lab = FloatArray(3)
    private val lin = FloatArray(3)

    /** Przetwarza kolor w kodowaniu gamma (0..1) w miejscu. */
    fun apply(rgb: FloatArray) {
        toOklab(lin(rgb[0]), lin(rgb[1]), lin(rgb[2]))
        val a = lab[1]
        val b = lab[2]
        val chroma = sqrt(a * a + b * b)
        if (chroma < MIN_CHROMA) return
        val l = lab[0]
        val wanted = gain(chroma, atan2(b, a))
        val k = fitToGamut(l, a, b, wanted)
        fromOklab(l, a * k, b * k)
        for (i in 0..2) rgb[i] = gamma(lin[i])
    }

    private fun gain(chroma: Float, hue: Float): Float {
        if (saturation < 0f) return 1f + saturation
        val muted = (1f - chroma / CHROMA_MAX).coerceIn(0f, 1f)
        return 1f + saturation * muted * skinGuard(hue)
    }

    /** 1 poza odcieniami skóry; w pomarańczowo-różowym zakresie (≈55° w OKLab) mniej o [SKIN_GUARD]. */
    private fun skinGuard(hue: Float): Float {
        var deg = hue * RAD_TO_DEG
        if (deg < 0f) deg += FULL_CIRCLE
        val d = (deg - SKIN_HUE) / SKIN_WIDTH
        return 1f - SKIN_GUARD * max(0f, 1f - d * d)
    }

    /** Największy mnożnik chromy ≤ [wanted], przy którym kolor mieści się w gamucie sRGB. */
    private fun fitToGamut(l: Float, a: Float, b: Float, wanted: Float): Float {
        if (wanted <= 1f || inGamut(l, a * wanted, b * wanted)) return wanted
        var lo = 1f
        var hi = wanted
        repeat(BISECTIONS) {
            val mid = (lo + hi) * HALF
            if (inGamut(l, a * mid, b * mid)) lo = mid else hi = mid
        }
        return lo
    }

    private fun inGamut(l: Float, a: Float, b: Float): Boolean {
        fromOklab(l, a, b)
        return lin.all { it in -GAMUT_EPS..1f + GAMUT_EPS }
    }

    private fun lin(v: Float): Float = toLinear[(v.coerceIn(0f, 1f) * LUT + HALF).toInt()]

    private fun gamma(v: Float): Float = toGamma[(v.coerceIn(0f, 1f) * LUT + HALF).toInt()]

    @Suppress("MagicNumber") // macierze OKLab z publikacji (Ottosson 2020)
    private fun toOklab(r: Float, g: Float, b: Float) {
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        lab[0] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        lab[1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        lab[2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    }

    @Suppress("MagicNumber") // macierze OKLab z publikacji (Ottosson 2020)
    private fun fromOklab(l: Float, a: Float, b: Float) {
        val l3 = cube(l + 0.3963377774f * a + 0.2158037573f * b)
        val m3 = cube(l - 0.1055613458f * a - 0.0638541728f * b)
        val s3 = cube(l - 0.0894841775f * a - 1.2914855480f * b)
        lin[0] = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        lin[1] = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        lin[2] = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
    }

    private fun cube(v: Float) = v * v * v

    private companion object {
        const val LUT = 8192
        const val HALF = 0.5f
        const val MIN_CHROMA = 1e-4f
        const val CHROMA_MAX = 0.32f
        const val SKIN_HUE = 55f
        const val SKIN_WIDTH = 40f
        const val SKIN_GUARD = 0.55f
        const val RAD_TO_DEG = 57.29578f
        const val FULL_CIRCLE = 360f
        const val BISECTIONS = 8
        const val GAMUT_EPS = 1e-3f
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
