package app.retrofilm.lut

import app.retrofilm.core.engine.pack.LutPackFiles
import app.retrofilm.core.engine.pack.LutRawCache
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pobrane filmy z samych plików, bez WorkManagera: lista i ładowanie LUT-a pobranego PNG 144³. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackDownloadedFilesTest {
    private val id = "negative_color/fuji_superia_200"
    private val localName = "negative_color~fuji_superia_200"

    private fun files(): PackDownloadedFiles {
        val ctx = RuntimeEnvironment.getApplication()
        val cacheDir = File(ctx.cacheDir, "raw-test").also { it.deleteRecursively() }
        return PackDownloadedFiles(ctx, LutRawCache(cacheDir))
    }

    @Test
    fun nothingDownloadedMeansNothingAvailableAndNoLut() {
        LutPackFiles.pngDir(RuntimeEnvironment.getApplication()).deleteRecursively()
        val f = files()
        assertTrue(f.available().isEmpty())
        assertNull(f.lutFor(id))
    }

    @Test
    fun downloadedPngIsListedAndDecodedToA144Lut() {
        val ctx = RuntimeEnvironment.getApplication()
        val dir = LutPackFiles.pngDir(ctx).also { it.mkdirs() }
        // PNG 144³ filmu startowego jako „pobrany” film spoza zestawu startowego.
        ctx.assets.open("luts/bw/kodak_tri_x_400.png").use { src ->
            File(dir, "$localName.png").outputStream().use { src.copyTo(it) }
        }
        val f = files()
        assertEquals(listOf(id), f.available().map { it.id })
        val lut = f.lutFor(id)
        assertNotNull(lut)
        assertEquals(144, lut?.size)
        assertNull(f.lutFor("negative_color/nie_pobrany"))
    }
}
