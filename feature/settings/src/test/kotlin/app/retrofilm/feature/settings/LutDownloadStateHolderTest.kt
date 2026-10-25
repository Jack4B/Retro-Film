package app.retrofilm.feature.settings

import app.retrofilm.core.ui.lut.LutCacheInfo
import app.retrofilm.core.ui.lut.LutDownloadSettings
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.LutPackRepository
import app.retrofilm.core.ui.lut.PackState
import app.retrofilm.feature.settings.lut.LutDownloadStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LutDownloadStateHolderTest {
    private class Repo : LutPackRepository {
        override val packs = MutableStateFlow(listOf(LutPack("a", "A", 1L, false, PackState.Ready)))

        override fun download(id: String) = Unit

        override fun cancel(id: String) = Unit

        override fun delete(id: String) = Unit
    }

    private class Cache : LutCacheInfo {
        override val downloadedBytes = MutableStateFlow(7L)
        override val cacheBytes = MutableStateFlow(ULTRA)
        var cleared = 0

        override fun clearCache() {
            cleared++
            cacheBytes.value = 0L
        }
    }

    private class Settings : LutDownloadSettings {
        override val onlyWifi = MutableStateFlow(true)

        override fun setOnlyWifi(enabled: Boolean) {
            onlyWifi.value = enabled
        }
    }

    @Test
    fun mapsCacheInfoAndClearsOnConfirm() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val cache = Cache()
        val holder = LutDownloadStateHolder(Repo(), cache, Settings(), { FREE }, scope)
        assertEquals(7L, holder.state.value.downloadedBytes)
        assertEquals(ULTRA, holder.state.value.cacheBytes)
        holder.onClearCacheRequest()
        assertTrue(holder.state.value.showClearCacheDialog)
        holder.onClearCacheConfirm()
        assertFalse(holder.state.value.showClearCacheDialog)
        assertEquals(1, cache.cleared)
        assertEquals(0L, holder.state.value.cacheBytes)
        holder.setOnlyWifi(false)
        assertFalse(holder.state.value.onlyWifi)
        scope.cancel()
    }

    private companion object {
        const val ULTRA = 50_331_648L
        const val FREE = 1_000_000_000L
    }
}
