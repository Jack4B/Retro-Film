package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Typeface
import app.retrofilm.core.frame.FrameAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** J: zdjęcie w ramce jest kopiowane 1:1 (bez Lanczosa) – płótno rośnie o ramkę na zewnątrz. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameNativeTest {
    private val w = 900
    private val h = 600
    private val px = IntArray(w * h) { i -> if ((i % w + i / w) % 2 == 0) -0x1000000 else -0x7f7f80 }
    private val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    private fun containsPhotoOneToOne(out: Bitmap) {
        assertTrue("${out.width}x${out.height}", out.width >= w && out.height >= h)
        // szachownica 1-px przetrwa tylko kopię 1:1 – każde skalowanie rozmyje ją w szarość
        val row = IntArray(out.width)
        out.getPixels(row, 0, out.width, 0, out.height / 2, out.width, 1)
        val blacks = row.count { it == -0x1000000 }
        assertTrue("czarnych=$blacks", blacks >= w / 2 - 2)
    }

    @Test fun originalAspectKeepsPhotoPixels() = containsPhotoOneToOne(
        FrameRenderer.render(src, FrameOptions(enabled = true), Typeface.DEFAULT)
    )

    @Test fun squareAspectKeepsPhotoPixels() {
        val out = FrameRenderer.render(
            src,
            FrameOptions(enabled = true, aspect = FrameAspect.entries.last()),
            Typeface.DEFAULT
        )
        containsPhotoOneToOne(out)
    }

    @Test fun twelveMegapixelFrameIsFast() {
        val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        FrameRenderer.render(big, FrameOptions(enabled = true), Typeface.DEFAULT).recycle()
        val t = System.nanoTime()
        val out = FrameRenderer.render(big, FrameOptions(enabled = true), Typeface.DEFAULT)
        val ms = (System.nanoTime() - t) / 1_000_000
        println("frame_12mp_ms=$ms ${out.width}x${out.height}")
        assertEquals(true, ms < 400)
    }
}
