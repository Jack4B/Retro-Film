package app.retrofilm.lut

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.pack.DownloadFailure
import app.retrofilm.core.engine.pack.HaldClutDecoder
import app.retrofilm.core.engine.pack.LutDownloadFeature
import app.retrofilm.core.engine.pack.LutDownloadWorker
import app.retrofilm.core.engine.pack.LutPackFiles
import app.retrofilm.core.engine.pack.LutRawCache
import app.retrofilm.core.engine.pack.PackDownloader
import app.retrofilm.core.engine.pack.PackEntry
import app.retrofilm.core.ui.lut.FailReason
import app.retrofilm.core.ui.lut.LutCacheInfo
import app.retrofilm.core.ui.lut.LutDownloadSettings
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.LutPackRepository
import app.retrofilm.core.ui.lut.PackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Implementacja D8: pobieranie przez WorkManager, PNG w filesDir, rozpakowane LUT-y w [LutRawCache]. */
@Suppress("TooManyFunctions")
class WorkLutPackRepository(context: Context, private val settings: LutDownloadSettings, scope: CoroutineScope) :
    LutPackRepository,
    LutCacheInfo {
    private val app = context.applicationContext
    private val workManager = WorkManager.getInstance(app)

    // Flaga wyłączona ⇒ brak wpisów do pobrania (UI nic nie pokazuje, download() nic nie robi).
    private val entries: List<PackEntry> = if (LutDownloadFeature.ENABLED) LutPackFiles.catalog(app) else emptyList()
    private val workStates = MutableStateFlow<Map<String, PackState>>(emptyMap())

    private val packsState = MutableStateFlow(buildPacks())
    override val packs: StateFlow<List<LutPack>> = packsState.asStateFlow()

    private val downloadedState = MutableStateFlow(downloadedSize())
    override val downloadedBytes: StateFlow<Long> = downloadedState.asStateFlow()

    private val cacheState = MutableStateFlow(0L)
    private val cache = LutRawCache(LutPackFiles.cacheDir(app)) { cacheState.value = it }
    override val cacheBytes: StateFlow<Long> = cacheState.asStateFlow()

    init {
        cacheState.value = cache.sizeBytes()
        scope.launch {
            workManager.getWorkInfosByTagFlow(LutDownloadWorker.TAG).collect { infos ->
                workStates.value = infos.mapNotNull { info -> idOf(info)?.let { it to stateOf(info) } }.toMap()
                refresh()
            }
        }
    }

    override fun download(id: String) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        if (!entry.downloadable) return
        LutDownloadWorker.enqueue(app, id, settings.onlyWifi.value)
    }

    override fun cancel(id: String) {
        workManager.cancelUniqueWork(LutDownloadWorker.uniqueName(id))
    }

    override fun delete(id: String) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        if (entry.bundled) return
        cancel(id)
        LutPackFiles.pngFile(app, entry).delete()
        java.io.File(LutPackFiles.pngDir(app), "${entry.localName}.png${PackDownloader.PART_SUFFIX}").delete()
        cache.remove(entry.localName)
        refresh()
    }

    /** Usuwa tylko rozpakowany cache; pobrane PNG zostają. */
    override fun clearCache() {
        cache.clear()
    }

    /** LUT pobranego filmu; rozpakowanie PNG → raw przy pierwszym użyciu. `null` gdy niepobrany. */
    @Suppress("ReturnCount")
    fun lutFor(id: String): Lut? {
        val entry = entries.firstOrNull { it.id == id && !it.bundled } ?: return null
        val png = LutPackFiles.pngFile(app, entry)
        if (!png.exists()) return null
        return cache.get(entry.localName, entry.grid, entry.unpackedLength) {
            png.inputStream().use { HaldClutDecoder.decode(it, entry.grid) }
        }
    }

    private fun refresh() {
        packsState.value = buildPacks()
        downloadedState.value = downloadedSize()
    }

    private fun buildPacks(): List<LutPack> = entries.map { e ->
        val ready = e.bundled || LutPackFiles.pngFile(app, e).exists()
        val state = when {
            ready -> PackState.Ready
            else -> workStates.value[e.id] ?: PackState.NotDownloaded
        }
        LutPack(e.id, e.displayName, e.bytes, e.bundled, state)
    }

    private fun downloadedSize(): Long =
        LutPackFiles.pngDir(app).listFiles { f -> f.name.endsWith(".png") }?.sumOf { it.length() } ?: 0L

    private fun idOf(info: WorkInfo): String? =
        info.tags.firstOrNull { it.startsWith(ID_TAG_PREFIX) }?.removePrefix(ID_TAG_PREFIX)
            ?: info.outputData.getString(LutDownloadWorker.KEY_ID)

    private fun stateOf(info: WorkInfo): PackState = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> PackState.Downloading(0f)
        WorkInfo.State.RUNNING -> PackState.Downloading(info.progress.getFloat(LutDownloadWorker.KEY_PROGRESS, 0f))
        WorkInfo.State.FAILED -> PackState.Failed(failReason(info.outputData.getString(LutDownloadWorker.KEY_FAILURE)))
        WorkInfo.State.SUCCEEDED -> PackState.Ready
        WorkInfo.State.CANCELLED -> PackState.NotDownloaded
    }

    private fun failReason(name: String?): FailReason = when (name) {
        DownloadFailure.CHECKSUM.name -> FailReason.CHECKSUM
        DownloadFailure.STORAGE.name -> FailReason.STORAGE
        else -> FailReason.NETWORK
    }

    companion object {
        const val ID_TAG_PREFIX = LutDownloadWorker.ID_TAG_PREFIX
    }
}
