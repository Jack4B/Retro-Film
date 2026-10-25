package app.retrofilm.core.engine.pack

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Katalog w APK musi zgadzać się z SHA256SUMS z Jack4B/Retro-Film-LUTs@0432c5a (kopia w test resources). */
class BundledCatalogTest {
    private val catalog = PackCatalog.parse(
        File("../../app/src/main/assets/${PackCatalog.ASSET}").readText()
    )
    private val sums: Map<String, String> = javaClass.getResourceAsStream("/SHA256SUMS")!!
        .bufferedReader().readLines().filter { it.isNotBlank() }
        .associate { line -> line.substringAfter("  ") to line.substringBefore("  ") }

    @Test
    fun has260Films() {
        assertEquals(260, catalog.size)
        assertEquals(260, catalog.map { it.id }.toSet().size)
    }

    @Test
    fun everyFileAndHashMatchesSha256Sums() {
        val pngs = sums.keys.filter { it.endsWith(".png") }.toSet()
        assertEquals(pngs, catalog.map { it.file }.toSet())
        for (e in catalog) {
            assertEquals(e.id, sums[e.file], e.sha256)
            assertTrue(e.id, e.downloadable)
        }
    }

    @Test
    fun unpackedLengthFollowsGrid() {
        val ektar = catalog.single { it.id == "negative_color/kodak_ektar_100" }
        assertEquals(256, ektar.grid)
        assertEquals(50_331_648L, ektar.unpackedLength)
        catalog.filter { it !== ektar }.forEach {
            assertEquals(it.id, 144, it.grid)
            assertEquals(it.id, 8_957_952L, it.unpackedLength)
        }
    }
}
