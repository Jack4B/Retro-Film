package app.retrofilm.pipeline

import app.retrofilm.core.engine.Lut

/**
 * Kalibracja filmu (LUT) rampą szarości: przechodzimy przekątną LUT (wejście szare r = g = b, interpolacja jest tam
 * dokładna) w [STEPS] krokach i szukamy liniowej jasności wejścia, dla której luminancja wyjścia filmu wynosi 18%.
 * To jest [targetGrey] dla auto-ekspozycji – film, który przyciemnia półtony, dostaje jaśniejsze wejście i na
 * odwrót. [shoulder] to jasność wejścia, przy której wyjście osiąga 90% swojego maksimum (gdzie film „ściska”
 * światła). Wynik jest ograniczony do 0,09..0,36 (±1 EV), żeby dziwny LUT nie rozregulował ekspozycji.
 */
data class FilmCalibration(val targetGrey: Float, val shoulder: Float) {
    companion object {
        const val STEPS = 256
        const val MID_GREY = 0.18f
        const val MIN_TARGET = 0.09f
        const val MAX_TARGET = 0.36f
        private const val SHOULDER_FRACTION = 0.9f
        private const val BYTE_MAX = 255f
        private const val EPS = 1e-6f
        private const val CHANNELS = 3
        val NEUTRAL = FilmCalibration(MID_GREY, 1f)

        fun of(lut: Lut): FilmCalibration {
            val out = FloatArray(STEPS + 1) { greyOut(lut, it / STEPS.toFloat()) }
            val max = out.max()
            var target = MAX_TARGET
            for (i in 1..STEPS) {
                if (out[i] >= MID_GREY) {
                    val t = (MID_GREY - out[i - 1]) / (out[i] - out[i - 1]).coerceAtLeast(EPS)
                    target = input(i - 1 + t.coerceIn(0f, 1f))
                    break
                }
            }
            val s = (0..STEPS).firstOrNull { out[it] >= SHOULDER_FRACTION * max } ?: STEPS
            return FilmCalibration(target.coerceIn(MIN_TARGET, MAX_TARGET), input(s.toFloat()))
        }

        /** Liniowa jasność wejścia dla kroku rampy (rampa równomierna w kodowaniu sRGB, jak wejście LUT). */
        private fun input(step: Float) = AdjustmentProcessor.srgbToLinear(step / STEPS)

        /** Luminancja (liniowa) wyjścia LUT dla szarego wejścia [v] (sRGB 0..1). */
        private fun greyOut(lut: Lut, v: Float): Float {
            val p = v * (lut.size - 1)
            val i = p.toInt().coerceAtMost(lut.size - 2)
            val f = p - i
            val rgb = DoubleArray(CHANNELS) { c ->
                val a = lut.nodeF(i, i, i, c)
                val b = lut.nodeF(i + 1, i + 1, i + 1, c)
                AdjustmentProcessor.srgbToLinear(((a + (b - a) * f) / BYTE_MAX).coerceIn(0f, 1f)).toDouble()
            }
            return Cat.luma(rgb).toFloat()
        }
    }
}
