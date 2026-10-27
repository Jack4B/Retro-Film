package app.retrofilm.pipeline

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Model ziarna filmowego.
 *
 * Amplituda: film jest zbiorem kryształów srebra, z których każdy jest naświetlony albo nie. Przy gęstości
 * (jasności) p ułamek naświetlonych kryształów ma wariancję p(1−p) (rozkład dwumianowy, prawo Selwyna), więc
 * ziarno jest największe w półtonach i zanika w czerni i bieli; mała podłoga zostaje, bo w cieniach widać szum
 * skanu. Ziarno jest zmianą gęstości optycznej, czyli mnożnikiem 2^(a·g) światła liniowego ([stops]), a nie
 * dodawanym szumem – dlatego nie przesuwa średniej jasności ([gain] jest znormalizowany).
 *
 * Struktura: biały szum Gaussa przefiltrowany Gaussem (ziarno ma widmo dolnoprzepustowe, nie ostre piksele),
 * bez siatki wartości jak w szumie wartościowym; dwie oktawy: drobne ziarno i rzadsze „kłaczki” srebra. Tekstura
 * jest zamknięta w torusie, więc próbkowanie jest bez szwów, a kanały barwne biorą ją z różnych przesunięć
 * (chmury barwnika kolorowego negatywu są niezależne między warstwami). Deterministyczne, więc podgląd = zapis.
 */
@Suppress("TooManyFunctions") // prymitywy jednego modelu ziarna: szum, amplituda, mnożnik, udział barwy
internal object FilmGrain {
    private const val TILE = 512
    private const val TILE_MASK = TILE - 1
    private const val FINE_SIGMA = 0.85f
    private const val FINE_WEIGHT = 0.9f
    private const val CLUMP_WEIGHT = 0.436f
    private const val CLUMP_SCALE = 2.6f
    private const val KERNEL_RADIUS = 3
    private const val SALT_FINE = 11
    private const val SALT_CLUMP = 29
    private const val UNIFORMS = 3
    private const val CHANNEL_SHIFT_X = 211
    private const val CHANNEL_SHIFT_Y = 97
    private const val CLUMP_SHIFT = 301
    private const val LUMA_SHARE = 0.75f
    private const val CHROMA_SHARE = 0.66f
    private const val STOPS_MAX = 0.5f
    private const val FLOOR = 0.25f
    private const val PEAK = 4f
    private const val GAMMA_EXPONENT = 0.4167f
    private const val LN2 = 0.6931472f
    private const val HALF = 0.5f
    private const val P_MIN = 0.02f
    private const val NEUTRAL_SPREAD = 0.006f
    private const val COLOR_SPREAD = 0.04f
    private const val SMOOTH_A = 3f
    private const val SMOOTH_B = 2f

    private val fine: FloatArray by lazy { buildTile(SALT_FINE) }
    private val clump: FloatArray by lazy { buildTile(SALT_CLUMP) }

    /** Wspólny (jasnościowy) szum o jednostkowej wariancji w punkcie ([x], [y]) dla ziarna o wielkości [cell] px. */
    fun luma(x: Int, y: Int, cell: Float): Float = layers(x.toFloat(), y.toFloat(), cell, 0, 0)

    /** Szum warstwy barwnej [channel] (0..2), niezależny od pozostałych; jednostkowa wariancja. */
    fun chroma(x: Int, y: Int, cell: Float, channel: Int): Float =
        layers(x.toFloat(), y.toFloat(), cell, (channel + 1) * CHANNEL_SHIFT_X, (channel + 1) * CHANNEL_SHIFT_Y)

    /** Ziarno kanału [channel]: wspólna jasność i niezależny barwnik, wariancja nadal jednostkowa. */
    fun channel(x: Int, y: Int, cell: Float, channel: Int): Float = mix(luma(x, y, cell), chroma(x, y, cell, channel))

    /** Składa ziarno kanału ze wspólnej [luma] i szumu barwnego [chroma] (wspólna część liczona raz na piksel). */
    fun mix(luma: Float, chroma: Float): Float = LUMA_SHARE * luma + CHROMA_SHARE * chroma

    /**
     * Jak [mix], ale z udziałem barwy [colorful] 0..1: dla 1 identycznie jak [mix], a im piksel bardziej szary
     * (zdjęcie czarno-białe), tym mniej szumu barwnego; wariancja zostaje jednostkowa (ubytek barwy przechodzi w
     * ziarno jasnościowe), więc ziarno szarego piksela jest szare, a nie kolorowe.
     */
    fun mix(luma: Float, chroma: Float, colorful: Float): Float {
        if (colorful >= 1f) return mix(luma, chroma)
        val c = CHROMA_SHARE * colorful
        return sqrt(1f - c * c) * luma + c * chroma
    }

    /**
     * Udział barwy 0..1 dla piksela o rozrzucie kanałów [spread] (największy minus najmniejszy, kodowanie 0..1):
     * piksele szare (rozrzut poniżej [NEUTRAL_SPREAD]) dostają ziarno szare, od [COLOR_SPREAD] pełne barwne,
     * pomiędzy gładkie przejście.
     */
    fun colorfulness(spread: Float): Float {
        val t = ((spread - NEUTRAL_SPREAD) / (COLOR_SPREAD - NEUTRAL_SPREAD)).coerceIn(0f, 1f)
        return t * t * (SMOOTH_A - SMOOTH_B * t)
    }

    /** Odchylenie ziarna w stopniach ekspozycji dla jasności [luma] 0..1 (kodowanej) i siły [amount] 0..1. */
    fun stops(luma: Float, amount: Float): Float {
        val p = luma.coerceIn(P_MIN, 1f - P_MIN)
        return amount * STOPS_MAX * (FLOOR + (1f - FLOOR) * sqrt(PEAK * p * (1f - p)))
    }

    /**
     * Mnożnik wartości kodowanej odpowiadający zmianie ekspozycji o [stops]·[g] EV w świetle liniowym
     * (kodowanie ≈ x^(1/2,4)), rozwinięty do drugiego rzędu i znormalizowany tak, żeby średnia wynosiła 1.
     */
    fun gain(stops: Float, g: Float): Float {
        val s = GAMMA_EXPONENT * LN2 * stops
        return 1f + s * g + HALF * s * s * (g * g - 1f)
    }

    private fun layers(x: Float, y: Float, cell: Float, shiftX: Int, shiftY: Int): Float {
        val a = sample(fine, x / cell + shiftX, y / cell + shiftY)
        val c = sample(clump, x / (cell * CLUMP_SCALE) + shiftX + CLUMP_SHIFT, y / (cell * CLUMP_SCALE) + shiftY)
        return FINE_WEIGHT * a + CLUMP_WEIGHT * c
    }

    private fun sample(tile: FloatArray, fx: Float, fy: Float): Float {
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val xa = x0 and TILE_MASK
        val xb = (x0 + 1) and TILE_MASK
        val ya = (y0 and TILE_MASK) * TILE
        val yb = ((y0 + 1) and TILE_MASK) * TILE
        val top = tile[ya + xa] + (tile[ya + xb] - tile[ya + xa]) * tx
        val bottom = tile[yb + xa] + (tile[yb + xb] - tile[yb + xa]) * tx
        return top + (bottom - top) * ty
    }

    /** Biały szum Gaussa (suma równomiernych), przefiltrowany Gaussem po torusie i znormalizowany do wariancji 1. */
    private fun buildTile(salt: Int): FloatArray {
        val white = FloatArray(TILE * TILE) { i ->
            var s = 0f
            for (k in 0 until UNIFORMS) s += AdjustmentProcessor.noise(i and TILE_MASK, i / TILE, salt + k)
            s
        }
        val kernel = FloatArray(2 * KERNEL_RADIUS + 1) {
            val d = (it - KERNEL_RADIUS).toFloat()
            exp(-d * d / (2f * FINE_SIGMA * FINE_SIGMA))
        }
        val tmp = FloatArray(white.size)
        val out = FloatArray(white.size)
        convolve(white, tmp, kernel, 1, TILE)
        convolve(tmp, out, kernel, TILE, 1)
        var sum = 0.0
        var sq = 0.0
        for (v in out) {
            sum += v
            sq += v.toDouble() * v
        }
        val mean = (sum / out.size).toFloat()
        val inv = (1.0 / sqrt(sq / out.size - mean.toDouble() * mean)).toFloat()
        for (i in out.indices) out[i] = (out[i] - mean) * inv
        return out
    }

    /** Splot po torusie wzdłuż osi o kroku [step]; [other] to krok drugiej osi. */
    private fun convolve(src: FloatArray, dst: FloatArray, kernel: FloatArray, step: Int, other: Int) {
        val norm = kernel.sum()
        for (line in 0 until TILE) {
            for (pos in 0 until TILE) {
                var acc = 0f
                for (k in kernel.indices) {
                    val p = (pos + k - KERNEL_RADIUS) and TILE_MASK
                    acc += src[line * other + p * step] * kernel[k]
                }
                dst[line * other + pos * step] = acc / norm
            }
        }
    }
}
