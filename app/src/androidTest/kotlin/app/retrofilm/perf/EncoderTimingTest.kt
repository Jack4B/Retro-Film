package app.retrofilm.perf

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.core.export.ChromaSubsampling
import app.retrofilm.core.export.JpegExporter
import app.retrofilm.core.export.JpegOptions
import app.retrofilm.core.frame.SignatureFitStatus
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Czas kodowania JPEG: własny koder 4:4:4 ([JpegExporter], Full444) vs `Bitmap.compress` (4:2:0, Skia/libjpeg-turbo)
 * dla 12 MP i 48 MP, jakość 95. Wyniki w logcat (`RetroPerf`) i w podsumowaniu CI; brak progu w asercji.
 */
@RunWith(AndroidJUnit4::class)
class EncoderTimingTest {
    private fun photo(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                row[x] =
                    (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or ((x / 7 xor y / 5) and 0xFF)
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
        return bmp
    }

    private fun report(name: String, ms: Long) {
        Log.i(TAG, "$name=$ms ms")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putLong(name, ms) })
    }

    private fun measure(label: String, w: Int, h: Int) {
        val bmp = photo(w, h)
        // rozgrzewka JIT na małym wycinku
        JpegExporter.encode(Bitmap.createBitmap(bmp, 0, 0, 512, 512), JpegOptions(), SignatureFitStatus.None)
        var t = System.nanoTime()
        val own = JpegExporter.encode(
            bmp,
            JpegOptions(subsampling = ChromaSubsampling.Full444),
            SignatureFitStatus.None
        )
        report("jpeg_kotlin_444_$label", (System.nanoTime() - t) / NS)
        t = System.nanoTime()
        val buf = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, JpegExporter.DEFAULT_QUALITY, buf)
        report("jpeg_compress_420_$label", (System.nanoTime() - t) / NS)
        assertTrue(own.size > 0 && buf.size() > 0)
        bmp.recycle()
    }

    @Test
    fun encode12mp() = measure("12mp", 4000, 3000)

    @Test
    fun encode48mp() = measure("48mp", 8000, 6000)

    private companion object {
        const val TAG = "RetroPerf"
        const val NS = 1_000_000L
    }
}
