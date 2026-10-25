package app.retrofilm.engine

import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory
import app.retrofilm.core.engine.LutMemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineFilmEngineLruTest {
    @Test
    fun boundedLruEvictsAndReuses() {
        val ctx = RuntimeEnvironment.getApplication()
        val catalog = LutCatalog(ctx.assets)
        val ids = catalog.entries.map { it.id }.take(3)
        val opened = mutableListOf<String>()
        val small = Lut.expectedBytes(2, 1).toLong()
        val engine = EngineFilmEngine(
            catalog,
            LutEngineFactory.create(),
            open = { _, e ->
                opened += e.id
                Lut(2, ByteArray(small.toInt()))
            },
            luts = LutMemoryCache(maxBytes = 2 * small)
        )
        val a = engine.load(ids[0])
        engine.load(ids[1])
        assertSame(a, engine.load(ids[0]))
        engine.load(ids[2])
        assertEquals(listOf(ids[0], ids[2]), engine.luts.keys())
        engine.load(ids[1])
        assertEquals(listOf(ids[0], ids[1], ids[2], ids[1]), opened)
    }
}
