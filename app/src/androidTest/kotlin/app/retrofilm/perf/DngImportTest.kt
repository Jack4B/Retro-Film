package app.retrofilm.perf

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.retrofilm.pipeline.PhotoLoader
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Import DNG jako pełnoprawna ścieżka: syntetyczny (wygenerowany, nie prywatny) DNG 384×256 Bayer 10-bit
 * jak z aparatu realme GT 7T — bez osadzonego podglądu, Orientation = 9 (niepoprawna, ma być „normalna”),
 * AsShotNeutral jak w oryginale; scena szara, więc po WB kamery kanały mają wyjść neutralne.
 */
@RunWith(AndroidJUnit4::class)
class DngImportTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val open = { instr.context.assets.open("synthetic-orient9.dng") }

    @Test
    fun opensFlatNeutralLandscape() {
        assertTrue(PhotoLoader.isTiffRaw(open))
        assertNull(PhotoLoader.thumbnail(open))
        val t = System.nanoTime()
        val loaded = PhotoLoader.open(open, PhotoLoader.PREVIEW_MAX_SIDE)
        val ms = (System.nanoTime() - t) / NS
        Log.i(TAG, "dng_open_synthetic_ms=$ms")
        instr.sendStatus(0, Bundle().apply { putString("dng_open_synthetic_ms", ms.toString()) })
        assertEquals(W, loaded.bitmap.width)
        assertEquals(H, loaded.bitmap.height)
        val c = loaded.bitmap.getPixel(W / 2, H / 2)
        val g = Color.green(c)
        Log.i(TAG, "dng_center_rgb=${Color.red(c)},$g,${Color.blue(c)}")
        assertTrue(abs(Color.red(c) - g) < TOL && abs(Color.blue(c) - g) < TOL)
    }

    private companion object {
        const val TAG = "RetroPerf"
        const val W = 384
        const val H = 256
        const val TOL = 40
        const val NS = 1_000_000L
    }
}
