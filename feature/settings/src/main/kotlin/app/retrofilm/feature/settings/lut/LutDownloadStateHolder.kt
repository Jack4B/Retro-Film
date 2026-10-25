package app.retrofilm.feature.settings.lut

import app.retrofilm.core.ui.lut.LutCacheInfo
import app.retrofilm.core.ui.lut.LutDownloadSettings
import app.retrofilm.core.ui.lut.LutPackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Składa [LutDownloadUiState] z kontraktów D8/D9: [LutPackRepository] (pakiety),
 * [LutCacheInfo] (rozmiary na dysku, czyszczenie cache) i [LutDownloadSettings] („tylko Wi‑Fi”).
 * W aplikacji wszystkie trzy (poza ustawieniami) daje `WorkLutPackRepository`.
 */
class LutDownloadStateHolder(
    private val repository: LutPackRepository,
    private val cacheInfo: LutCacheInfo,
    private val settings: LutDownloadSettings,
    private val freeSpaceBytes: () -> Long,
    scope: CoroutineScope
) {
    private val dialog = MutableStateFlow(false)

    val state: StateFlow<LutDownloadUiState> = combine(
        repository.packs,
        settings.onlyWifi,
        cacheInfo.downloadedBytes,
        cacheInfo.cacheBytes,
        dialog
    ) { packs, onlyWifi, downloaded, cache, showDialog ->
        LutDownloadUiState(
            packs = packs,
            freeSpaceBytes = freeSpaceBytes(),
            onlyWifi = onlyWifi,
            downloadedBytes = downloaded,
            cacheBytes = cache,
            showClearCacheDialog = showDialog
        )
    }.stateIn(scope, SharingStarted.Eagerly, initial())

    private fun initial() = LutDownloadUiState(
        packs = repository.packs.value,
        freeSpaceBytes = freeSpaceBytes(),
        onlyWifi = settings.onlyWifi.value,
        downloadedBytes = cacheInfo.downloadedBytes.value,
        cacheBytes = cacheInfo.cacheBytes.value
    )

    fun download(id: String) = repository.download(id)

    fun cancel(id: String) = repository.cancel(id)

    fun delete(id: String) = repository.delete(id)

    fun setOnlyWifi(enabled: Boolean) = settings.setOnlyWifi(enabled)

    fun onClearCacheRequest() {
        dialog.value = true
    }

    fun onClearCacheDismiss() {
        dialog.value = false
    }

    fun onClearCacheConfirm() {
        dialog.value = false
        cacheInfo.clearCache()
    }
}
