package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.ColorSpace
import java.nio.ByteBuffer
import kotlin.math.cbrt
import kotlin.math.min

/**
 * Konwersja Display P3 → sRGB z mapowaniem gamutowym zamiast przycinania kanałów. Kolor spoza sRGB (ujemna lub > 1
 * składowa po przeliczeniu) wraca do gamutu przez zmniejszenie chromy w OKLab przy stałej jasności i odcieniu
 * ([OkLab.toRgb]); przycinanie kanałów przesuwa odcień (pomiar A0: p95 1,7°, p99 3,7°; po mapowaniu 0°). Kolory
 * wewnątrz sRGB są przeliczane zwykłą macierzą (jak w Skia). Surowe wartości P3 czyta `copyPixelsToBuffer`, bo
 * `Bitmap.getPixels` zwraca kolory już przycięte do sRGB.
 */
internal object GamutMapper {
    private const val ROWS = 64
    private const val LEVELS = 256
    private const val BYTE_MAX = 255f
    private const val BYTES_PER_PIXEL = 4
    private const val ENCODE_SIZE = 8192
    private const val EPS = 1e-4f
    private const val HALF = 0.5f
    private const val CHANNELS = 3
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_A = 24
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val OFFSET_G = 1
    private const val OFFSET_B = 2
    private const val OFFSET_A = 3

    // macierz Display P3 (liniowe RGB) → sRGB (liniowe RGB), D65
    private const val M00 = 1.2249401f
    private const val M01 = -0.2249402f
    private const val M02 = 0f
    private const val M10 = -0.0420569f
    private const val M11 = 1.0420571f
    private const val M12 = 0f
    private const val M20 = -0.0196376f
    private const val M21 = -0.0786361f
    private const val M22 = 1.0982735f

    // liniowe sRGB → LMS (OKLab, Ottosson)
    private const val L0 = 0.4122214708f
    private const val L1 = 0.5363325363f
    private const val L2 = 0.0514459929f
    private const val S0 = 0.2119034982f
    private const val S1 = 0.6806995451f
    private const val S2 = 0.1073969566f
    private const val T0 = 0.0883024619f
    private const val T1 = 0.2817188376f
    private const val T2 = 0.6299787005f

    // LMS' → OKLab
    private const val A0 = 0.2104542553f
    private const val A1 = 0.7936177850f
    private const val A2 = -0.0040720468f
    private const val B0 = 1.9779984951f
    private const val B1 = -2.4285922050f
    private const val B2 = 0.4505937099f
    private const val C0 = 0.0259040371f
    private const val C1 = 0.7827717662f
    private const val C2 = -0.8086757660f

    private val toLinear = FloatArray(LEVELS) { AdjustmentProcessor.srgbToLinear(it / BYTE_MAX) }
    private val encode = FloatArray(ENCODE_SIZE + 1) { AdjustmentProcessor.linearToSrgb(it.toFloat() / ENCODE_SIZE) }

    /** Czy [photo] da się przekonwertować tą ścieżką (Display P3, 8 bitów na kanał). */
    fun supports(photo: Bitmap): Boolean =
        photo.config == Bitmap.Config.ARGB_8888 && photo.colorSpace == ColorSpace.get(ColorSpace.Named.DISPLAY_P3)

    fun toSrgb(photo: Bitmap): Bitmap {
        val w = photo.width
        val h = photo.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, photo.hasAlpha(), ColorPolicy.SRGB)
        Parallel.forEach((h + ROWS - 1) / ROWS, { OkLab() }) { ok, chunk ->
            val y = chunk * ROWS
            val rows = min(ROWS, h - y)
            val strip = Bitmap.createBitmap(photo, 0, y, w, rows)
            val raw = ByteBuffer.allocate(w * rows * BYTES_PER_PIXEL)
            strip.copyPixelsToBuffer(raw)
            val px = convert(raw.array(), w * rows, ok)
            synchronized(out) { out.setPixels(px, 0, w, 0, y, w, rows) }
        }
        return out
    }

    private fun convert(raw: ByteArray, count: Int, ok: OkLab): IntArray {
        val rgb = FloatArray(CHANNELS)
        return IntArray(count) { i ->
            val o = i * BYTES_PER_PIXEL
            val lr = toLinear[raw[o].toInt() and BYTE_MASK]
            val lg = toLinear[raw[o + OFFSET_G].toInt() and BYTE_MASK]
            val lb = toLinear[raw[o + OFFSET_B].toInt() and BYTE_MASK]
            val r = M00 * lr + M01 * lg + M02 * lb
            val g = M10 * lr + M11 * lg + M12 * lb
            val b = M20 * lr + M21 * lg + M22 * lb
            if (r in -EPS..1f + EPS && g in -EPS..1f + EPS && b in -EPS..1f + EPS) {
                rgb[0] = encodeOf(r)
                rgb[1] = encodeOf(g)
                rgb[2] = encodeOf(b)
            } else {
                mapOutOfGamut(r, g, b, ok, rgb)
            }
            pack(raw[o + OFFSET_A].toInt() and BYTE_MASK, rgb)
        }
    }

    /** Kolor poza gamutem: OKLab z liniowego sRGB (wartości spoza 0..1 dozwolone), chroma cofnięta do gamutu. */
    private fun mapOutOfGamut(r: Float, g: Float, b: Float, ok: OkLab, out: FloatArray) {
        val l = cbrt(L0 * r + L1 * g + L2 * b)
        val m = cbrt(S0 * r + S1 * g + S2 * b)
        val s = cbrt(T0 * r + T1 * g + T2 * b)
        ok.toRgb(
            A0 * l + A1 * m + A2 * s,
            B0 * l + B1 * m + B2 * s,
            C0 * l + C1 * m + C2 * s,
            out
        )
    }

    private fun encodeOf(v: Float): Float {
        val p = v.coerceIn(0f, 1f) * ENCODE_SIZE
        val i = min(p.toInt(), ENCODE_SIZE - 1)
        return encode[i] + (encode[i + 1] - encode[i]) * (p - i)
    }

    private fun pack(alpha: Int, rgb: FloatArray): Int =
        (alpha shl SHIFT_A) or (to8(rgb[0]) shl SHIFT_R) or (to8(rgb[1]) shl SHIFT_G) or to8(rgb[2])

    private fun to8(v: Float): Int = (v * BYTE_MAX + HALF).toInt().coerceIn(0, LEVELS - 1)
}
