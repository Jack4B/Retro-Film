package app.retrofilm.pipeline

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Korekta perspektywy jak „Transform” w Lightroomie: zdjęcie traktowane jest jak obraz z kamery otworkowej, której
 * obracamy oś o kąt pionowy ([vertical], pochylenie w górę/dół, prostuje zbiegające się pionowe linie budynków)
 * i poziomy ([horizontal], obrót wokół pionu). Homografia H = K·R·K⁻¹ o ogniskowej f = przekątna kadru
 * (ok. 50 mm w przeliczeniu na małoobrazkowy), więc proporcje zmieniają się fizycznie poprawnie, a nie jak przy
 * zwykłym skosie. Współrzędne względem środka obrazu, piksele. Suwaki −1..1 odpowiadają ±[MAX_DEGREES].
 */
@Suppress("MagicNumber") // 4 narożniki, indeksy macierzy 3×3
object Perspective {
    const val MAX_DEGREES = 25.0
    private const val DEG_TO_RAD = Math.PI / 180.0
    const val SIZE = 9

    /** Macierz 3×3 (wierszami) źródło → wynik dla obrazu [w]×[h]; zerowe suwaki dają tożsamość. */
    fun matrix(w: Double, h: Double, vertical: Float, horizontal: Float): DoubleArray {
        val f = hypot(w, h)
        val tilt = vertical * MAX_DEGREES * DEG_TO_RAD
        val pan = horizontal * MAX_DEGREES * DEG_TO_RAD
        val ct = cos(tilt)
        val st = sin(tilt)
        val cp = cos(pan)
        val sp = sin(pan)
        // R = Ry(pan) · Rx(tilt)
        val r00 = cp
        val r01 = sp * st
        val r02 = sp * ct
        val r10 = 0.0
        val r11 = ct
        val r12 = -st
        val r20 = -sp
        val r21 = cp * st
        val r22 = cp * ct
        return doubleArrayOf(r00, r01, f * r02, r10, r11, f * r12, r20 / f, r21 / f, r22)
    }

    /** Obraz punktu ([x], [y]) w homografii [m] zapisany do [out] (x, y). */
    fun map(m: DoubleArray, x: Double, y: Double, out: DoubleArray) {
        val d = m[6] * x + m[7] * y + m[8]
        out[0] = (m[0] * x + m[1] * y + m[2]) / d
        out[1] = (m[3] * x + m[4] * y + m[5]) / d
    }

    /** Czworokąt, w który zamienia się prostokąt [w]×[h] (środek w zerze): x0,y0,…,x3,y3 w kolejności obiegu. */
    fun quad(w: Double, h: Double, m: DoubleArray): DoubleArray {
        val hw = w / 2.0
        val hh = h / 2.0
        val corners = doubleArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
        val out = DoubleArray(corners.size)
        val p = DoubleArray(2)
        for (i in 0 until 4) {
            map(m, corners[2 * i], corners[2 * i + 1], p)
            out[2 * i] = p[0]
            out[2 * i + 1] = p[1]
        }
        return out
    }
}
