package app.retrofilm.core.ui.lut

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Atrapa do podglądów i testów (tylko debug). Zmienia stany natychmiast, bez sieci. */
class FakeLutPackRepository(initial: List<LutPack>) :
    LutPackRepository,
    LutDownloadSettings,
    LutCacheInfo {
    private val _packs = MutableStateFlow(initial)
    override val packs: StateFlow<List<LutPack>> = _packs
    private val _onlyWifi = MutableStateFlow(true)
    override val onlyWifi: StateFlow<Boolean> = _onlyWifi
    private val _downloadedBytes = MutableStateFlow(0L)
    override val downloadedBytes: StateFlow<Long> = _downloadedBytes
    private val _cacheBytes = MutableStateFlow(0L)
    override val cacheBytes: StateFlow<Long> = _cacheBytes

    override fun clearCache() {
        _cacheBytes.value = 0L
    }

    override fun download(id: String) = set(id, PackState.Ready)

    override fun cancel(id: String) = set(id, PackState.NotDownloaded)

    override fun delete(id: String) = set(id, PackState.NotDownloaded)

    override fun setOnlyWifi(enabled: Boolean) {
        _onlyWifi.value = enabled
    }

    private fun set(id: String, state: PackState) = _packs.update { list ->
        list.map { if (it.id == id && !it.bundled) it.copy(state = state) else it }
    }
}
