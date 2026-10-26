package app.retrofilm.pipeline

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Wyostrzanie świadome treści: mapa mnożnika siły wyostrzania w niskiej rozdzielczości. Włosy i inne struktury
 * kierunkowe (równoległe pasma o umiarkowanym kontraście) wykrywane są koherencją tensora struktury (Bigün i in.
 * 1991; Weickert 1999): λ1 − λ2 względem λ1 + λ2 dla uśrednionych gradientów jasności, więc kierunkowa tekstura
 * dostaje silniejsze wyostrzanie, a szum o losowej orientacji nie. Bardzo silne krawędzie (energia powyżej progu)
 * są pomijane, żeby proste linie nie dostawały aureoli. Oczy (miejsce wyliczone z ramki twarzy, [FaceBox] z
 * wykrywacza systemowego) dostają dodatkową siłę, a gładka skóra twarzy mniej (wyostrzanie wydobywałoby tylko
 * pory i szum). Mnożnik zawsze w [MIN_GAIN, MAX_GAIN], żeby żadna decyzja heurystyki nie zepsuła zdjęcia.
 */
internal object SemanticSharpen {
    const val TARGET = 1024
    private const val MIN_GAIN = 0.55f
    private const val MAX_GAIN = 1.8f
    private const val HAIR_BOOST = 0.5f
    private const val EYE_BOOST = 0.7f
    private const val SKIN_CUT = 0.45f
    private const val SMOOTH_RADIUS = 2
    private const val FINAL_RADIUS = 1
    private const val COH_FROM = 0.55f
    private const val COH_TO = 0.9f
    private const val ENERGY_FROM = 150f
    private const val ENERGY_TO = 500f
    private const val STRONG_FROM = 2500f
    private const val STRONG_TO = 6000f
    private const val SKIN_FLAT_FROM = 60f
    private const val SKIN_FLAT_TO = 350f
    private const val EPS = 1e-3f
    private const val EYE_SPAN = 2.4f
    private const val EYE_DROP = 0.35f
    private const val EYE_RADIUS = 0.3f
    private const val EYE_INNER = 0.6f
    private const val EYE_OUTER = 1.2f
    private const val HALF = 0.5f
    private const val FACE_INNER = 0.7f
    private const val FACE_OUTER = 1.0f
    private const val TWO = 2f
    private const val THREE = 3f
    private const val FOUR = 4f
    private const val SOBEL_SIDE = 2f

    /** Mapa mnożników; [at] daje wartość w pikselu pełnej rozdzielczości. */
    class Map(private val low: Lowres, private val gain: FloatArray) {
        fun at(x: Int, y: Int): Float = low.sample(gain, x, y)
    }

    /** [luma] to jasność w skali 0..255 o rozmiarze [Lowres.w]×[Lowres.h]; [faces] znormalizowane do obrazu. */
    fun build(low: Lowres, luma: FloatArray, faces: List<FaceBox>): Map {
        val w = low.w
        val h = low.h
        val energy = FloatArray(w * h)
        val coherence = FloatArray(w * h)
        structure(luma, w, h, energy, coherence)
        val gain = FloatArray(w * h) { 1f }
        for (i in gain.indices) {
            val rise = smoothstep(ENERGY_FROM, ENERGY_TO, energy[i])
            val fall = 1f - smoothstep(STRONG_FROM, STRONG_TO, energy[i])
            gain[i] += HAIR_BOOST * smoothstep(COH_FROM, COH_TO, coherence[i]) * rise * fall
        }
        for (face in faces) applyFace(face, w, h, energy, gain)
        for (i in gain.indices) gain[i] = gain[i].coerceIn(MIN_GAIN, MAX_GAIN)
        return Map(low, GuidedFilter.boxMean(gain, w, h, FINAL_RADIUS))
    }

    /** Energia (λ1 + λ2) i koherencja uśrednionego tensora struktury z gradientów Sobela. */
    private fun structure(luma: FloatArray, w: Int, h: Int, energy: FloatArray, coherence: FloatArray) {
        val jxx = FloatArray(w * h)
        val jyy = FloatArray(w * h)
        val jxy = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val gx = sobel(luma, w, h, x, y, true)
                val gy = sobel(luma, w, h, x, y, false)
                val i = y * w + x
                jxx[i] = gx * gx
                jyy[i] = gy * gy
                jxy[i] = gx * gy
            }
        }
        val sxx = GuidedFilter.boxMean(jxx, w, h, SMOOTH_RADIUS)
        val syy = GuidedFilter.boxMean(jyy, w, h, SMOOTH_RADIUS)
        val sxy = GuidedFilter.boxMean(jxy, w, h, SMOOTH_RADIUS)
        for (i in energy.indices) {
            energy[i] = sxx[i] + syy[i]
            val d = sxx[i] - syy[i]
            coherence[i] = sqrt(d * d + FOUR * sxy[i] * sxy[i]) / (energy[i] + EPS)
        }
    }

    @Suppress("LongParameterList") // gorąca pętla: współrzędne i wymiary
    private fun sobel(l: FloatArray, w: Int, h: Int, x: Int, y: Int, horizontal: Boolean): Float {
        fun v(dx: Int, dy: Int) = l[min(max(y + dy, 0), h - 1) * w + min(max(x + dx, 0), w - 1)]
        return if (horizontal) {
            (v(1, -1) + SOBEL_SIDE * v(1, 0) + v(1, 1)) - (v(-1, -1) + SOBEL_SIDE * v(-1, 0) + v(-1, 1))
        } else {
            (v(-1, 1) + SOBEL_SIDE * v(0, 1) + v(1, 1)) - (v(-1, -1) + SOBEL_SIDE * v(0, -1) + v(1, -1))
        }
    }

    /**
     * Twarz z [FaceBox] (konwencja [FaceFinder]: ramka ±1,2 odległości oczu w poziomie, środek przesunięty o
     * 0,35 odległości oczu poniżej linii oczu): gładka skóra wewnątrz elipsy dostaje mniejszy mnożnik, a okolice oczu
     * większy.
     */
    private fun applyFace(face: FaceBox, w: Int, h: Int, energy: FloatArray, gain: FloatArray) {
        val boxW = (face.right - face.left) * w
        val boxH = (face.bottom - face.top) * h
        if (boxW < 1f || boxH < 1f) return
        val cx = (face.left + face.right) * HALF * w
        val cy = (face.top + face.bottom) * HALF * h
        val eyes = boxW / EYE_SPAN
        val eyeY = cy - EYE_DROP * eyes
        val reach = EYE_RADIUS * eyes
        for (y in max(0, ((face.top * h).toInt())) until min(h, ceilInt(face.bottom * h))) {
            for (x in max(0, ((face.left * w).toInt())) until min(w, ceilInt(face.right * w))) {
                val i = y * w + x
                val nx = (x - cx) / (boxW * HALF)
                val ny = (y - cy) / (boxH * HALF)
                val inside = 1f - smoothstep(FACE_INNER, FACE_OUTER, sqrt(nx * nx + ny * ny))
                val flat = 1f - smoothstep(SKIN_FLAT_FROM, SKIN_FLAT_TO, energy[i])
                gain[i] -= SKIN_CUT * inside * flat
                gain[i] += EYE_BOOST * eyeWeight(x.toFloat(), y.toFloat(), cx, eyeY, eyes, reach)
            }
        }
    }

    @Suppress("LongParameterList") // geometria dwóch oczu
    private fun eyeWeight(x: Float, y: Float, cx: Float, eyeY: Float, eyes: Float, reach: Float): Float {
        var best = 0f
        for (side in intArrayOf(-1, 1)) {
            val dx = x - (cx + side * eyes * HALF)
            val dy = y - eyeY
            val d = sqrt(dx * dx + dy * dy)
            best = max(best, 1f - smoothstep(EYE_INNER * reach, EYE_OUTER * reach, d))
        }
        return best
    }

    private fun ceilInt(v: Float): Int = kotlin.math.ceil(v).toInt()

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (THREE - TWO * t)
    }
}
