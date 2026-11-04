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
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val SHIFT_A = 24
    private const val PROBE_R = 0x12
    private const val PROBE_G = 0x34
    private const val PROBE_B = 0x57
    private const val PROBE = -0xedcba9 // 0xFF123457: różne bajty w każdym kanale
    private const val CACHE_BITS = 14
    private const val CACHE = 1 shl CACHE_BITS
    private const val HASH = -0x61c88647 // złoty podział (Fibonacci hashing)

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

    /**
     * Całość jednym `copyPixelsToBuffer`, konwersja w miejscu (równolegle po paskach) i jednym
     * `copyPixelsFromBuffer` – bez kopii paska przez `Bitmap.createBitmap` i bez `synchronized setPixels` na każdy
     * pasek (na telefonie te kopie i blokada dominowały zapis zdjęcia P3). Wynik bit w bit jak wcześniej.
     */
    fun toSrgb(photo: Bitmap): Bitmap {
        val w = photo.width
        val h = photo.height
        val raw = ByteBuffer.allocate(w * h * BYTES_PER_PIXEL)
        photo.copyPixelsToBuffer(raw)
        val bytes = raw.array()
        Parallel.forEach((h + ROWS - 1) / ROWS, { Worker() }) { worker, chunk ->
            val y = chunk * ROWS
            val rows = min(ROWS, h - y)
            convert(bytes, y * w, rows * w, worker)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, photo.hasAlpha(), ColorPolicy.SRGB)
        if (bufferRoundTrip) {
            raw.rewind()
            out.copyPixelsFromBuffer(raw)
        } else {
            writeInts(bytes, out)
        }
        return out
    }

    /** Położenie bajtów R, G, B, A w pikselu bufora `copyPixelsToBuffer` (zależy od platformy), z jednego piksela. */
    internal val layout: IntArray by lazy {
        val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        probe.setPixel(0, 0, PROBE)
        val buf = ByteBuffer.allocate(BYTES_PER_PIXEL)
        probe.copyPixelsToBuffer(buf)
        val b = buf.array().map { it.toInt() and BYTE_MASK }
        intArrayOf(b.indexOf(PROBE_R), b.indexOf(PROBE_G), b.indexOf(PROBE_B), b.indexOf(BYTE_MASK))
    }

    /** Czy `copyPixelsFromBuffer` czyta bajty w tej samej kolejności, w jakiej `copyPixelsToBuffer` je oddaje. */
    private val bufferRoundTrip: Boolean by lazy {
        val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        probe.setPixel(0, 0, PROBE)
        val buf = ByteBuffer.allocate(BYTES_PER_PIXEL)
        probe.copyPixelsToBuffer(buf)
        val back = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        buf.rewind()
        back.copyPixelsFromBuffer(buf)
        back.getPixel(0, 0) == PROBE
    }

    /** Zapas dla platform bez symetrycznego bufora (Skia hosta w Robolectric): paskami przez `setPixels`. */
    private fun writeInts(bytes: ByteArray, out: Bitmap) {
        val w = out.width
        val ro = layout[0]
        val go = layout[1]
        val bo = layout[2]
        for (y in 0 until out.height step ROWS) {
            val rows = min(ROWS, out.height - y)
            val px = IntArray(w * rows) { i ->
                val o = (y * w + i) * BYTES_PER_PIXEL
                (BYTE_MASK shl SHIFT_A) or ((bytes[o + ro].toInt() and BYTE_MASK) shl SHIFT_R) or
                    ((bytes[o + go].toInt() and BYTE_MASK) shl SHIFT_G) or (bytes[o + bo].toInt() and BYTE_MASK)
            }
            out.setPixels(px, 0, w, 0, y, w, rows)
        }
    }

    /** Stan wątku: [OkLab] i pamięć podręczna kolorów spoza gamutu (dokładne wyniki, klucz = pełne 24 bity RGB). */
    private class Worker {
        val ok = OkLab()
        val rgb = FloatArray(CHANNELS)
        val keys = IntArray(CACHE) { -1 }
        val values = IntArray(CACHE)
    }

    /** Konwertuje [count] pikseli od [first] w miejscu (bajty w tej samej kolejności co bitmapa). */
    private fun convert(raw: ByteArray, first: Int, count: Int, wk: Worker) {
        val rgb = wk.rgb
        val ro = layout[0]
        val go = layout[1]
        val bo = layout[2]
        for (i in first until first + count) {
            val o = i * BYTES_PER_PIXEL
            val r8 = raw[o + ro].toInt() and BYTE_MASK
            val g8 = raw[o + go].toInt() and BYTE_MASK
            val b8 = raw[o + bo].toInt() and BYTE_MASK
            val lr = toLinear[r8]
            val lg = toLinear[g8]
            val lb = toLinear[b8]
            val r = M00 * lr + M01 * lg + M02 * lb
            val g = M10 * lr + M11 * lg + M12 * lb
            val b = M20 * lr + M21 * lg + M22 * lb
            val packed = if (r in -EPS..1f + EPS && g in -EPS..1f + EPS && b in -EPS..1f + EPS) {
                rgb[0] = encodeOf(r)
                rgb[1] = encodeOf(g)
                rgb[2] = encodeOf(b)
                pack(rgb)
            } else {
                val key = (r8 shl SHIFT_R) or (g8 shl SHIFT_G) or b8
                val slot = (key * HASH) ushr (Int.SIZE_BITS - CACHE_BITS)
                if (wk.keys[slot] == key) {
                    wk.values[slot]
                } else {
                    mapOutOfGamut(r, g, b, wk.ok, rgb)
                    pack(rgb).also {
                        wk.keys[slot] = key
                        wk.values[slot] = it
                    }
                }
            }
            raw[o + ro] = (packed ushr SHIFT_R).toByte()
            raw[o + go] = (packed ushr SHIFT_G).toByte()
            raw[o + bo] = packed.toByte()
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

    private fun pack(rgb: FloatArray): Int = (to8(rgb[0]) shl SHIFT_R) or (to8(rgb[1]) shl SHIFT_G) or to8(rgb[2])

    private fun to8(v: Float): Int = (v * BYTE_MAX + HALF).toInt().coerceIn(0, LEVELS - 1)
}
