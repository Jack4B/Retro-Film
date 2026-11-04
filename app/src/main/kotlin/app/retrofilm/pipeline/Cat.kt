package app.retrofilm.pipeline

/**
 * Adaptacja chromatyczna Bradforda na liniowym sRGB (D65) między dowolnymi bielami podanymi jako xy – wspólna dla
 * balansu bieli z suwaków ([WhiteBalance]) i dla automatycznej korekty poza krzywą dzienną ([AutoWhiteBalance]).
 * Wzory publiczne (CIE, Bradford, sRGB).
 */
@Suppress("MagicNumber") // macierze 3×3 publicznych wzorów
internal object Cat {
    val D65_XY: Pair<Double, Double> = 0.3127 to 0.3290

    private val BRADFORD = doubleArrayOf(0.8951, 0.2664, -0.1614, -0.7502, 1.7135, 0.0367, 0.0389, -0.0685, 1.0296)
    private val BRADFORD_INV = doubleArrayOf(
        0.9869929, -0.1470543, 0.1599627,
        0.4323053, 0.5183603, 0.0492912,
        -0.0085287, 0.0400428, 0.9684867
    )
    private val RGB_TO_XYZ = doubleArrayOf(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041
    )
    private val XYZ_TO_RGB = doubleArrayOf(
        3.2404542, -1.5371385, -0.4985314,
        -0.9692660, 1.8760108, 0.0415560,
        0.0556434, -0.2040259, 1.0572252
    )

    /** Macierz liniowego sRGB: kolory oglądane przy bieli [from] → jak przy bieli [to] (Bradford). */
    fun adapt(from: Pair<Double, Double>, to: Pair<Double, Double>): DoubleArray {
        val rhoS = mul3(BRADFORD, xyz(from))
        val rhoD = mul3(BRADFORD, xyz(to))
        val diag = doubleArrayOf(rhoD[0] / rhoS[0], 0.0, 0.0, 0.0, rhoD[1] / rhoS[1], 0.0, 0.0, 0.0, rhoD[2] / rhoS[2])
        return mulM(XYZ_TO_RGB, mulM(mulM(BRADFORD_INV, mulM(diag, BRADFORD)), RGB_TO_XYZ))
    }

    /** Aparat bez balansu bieli pod światłem [xy]: biel D65 sceny staje się bielą oświetlenia (testy, symulacje). */
    fun toIlluminant(xy: Pair<Double, Double>): DoubleArray = adapt(D65_XY, xy)

    /** Chromatyczność xy koloru w liniowym sRGB. */
    fun xyOf(rgb: DoubleArray): Pair<Double, Double> {
        val v = mul3(RGB_TO_XYZ, rgb)
        val s = v[0] + v[1] + v[2]
        return v[0] / s to v[1] / s
    }

    /** Luminancja Rec.709 liniowego sRGB. */
    fun luma(rgb: DoubleArray): Double = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
    fun luma(r: Double, g: Double, b: Double): Double = 0.2126 * r + 0.7152 * g + 0.0722 * b

    private fun xyz(xy: Pair<Double, Double>) = doubleArrayOf(
        xy.first / xy.second,
        1.0,
        (1 - xy.first - xy.second) / xy.second
    )

    fun mul3(m: DoubleArray, v: DoubleArray) = DoubleArray(3) { r ->
        m[r * 3] * v[0] + m[r * 3 + 1] * v[1] +
            m[r * 3 + 2] * v[2]
    }

    private fun mulM(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        val r = i / 3
        val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }
}
