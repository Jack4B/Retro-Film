package app.retrofilm.engine

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory
import app.retrofilm.core.engine.pack.LutPackFiles
import app.retrofilm.core.engine.pack.LutRawCache
import app.retrofilm.core.ui.photo.PhotoRenderer
import app.retrofilm.feature.settings.lut.LutDownloadStateHolder
import app.retrofilm.lut.PrefsLutDownloadSettings
import app.retrofilm.lut.WorkLutPackRepository
import app.retrofilm.settings.AppSettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Prosta fabryka zależności aplikacji (bez frameworka DI). Jedna instancja na proces. */
class AppGraph(context: Context) {
    private val photos = ConcurrentHashMap<Int, ImageBitmap>()

    val filmEngine: EngineFilmEngine by lazy {
        // D9: startowe PNG 144³ dekodowane raz i trzymane w tym samym LRU co pakiety pobierane.
        EngineFilmEngine(
            LutCatalog(context.applicationContext.assets, lutRawCache),
            LutEngineFactory.create(),
            downloaded = { lutPackRepository }
        )
    }

    private val lutCacheBytes = MutableStateFlow(0L)

    /** Jeden [LutRawCache] na proces (limit LRU wspólny dla filmów startowych i pobranych). */
    private val lutRawCache: LutRawCache by lazy {
        LutRawCache(LutPackFiles.cacheDir(context.applicationContext)) { lutCacheBytes.value = it }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Ustawienia aplikacji w DataStore (dynamiczne kolory). */
    val appSettings: AppSettingsRepository by lazy { AppSettingsRepository.create(context) }

    val lutDownloadSettings: PrefsLutDownloadSettings by lazy { PrefsLutDownloadSettings(context) }

    /** Pakiety pobierane (D8); implementuje też LutCacheInfo. */
    val lutPackRepository: WorkLutPackRepository by lazy {
        WorkLutPackRepository(context, lutDownloadSettings, appScope, lutRawCache, lutCacheBytes)
    }

    /** Stan ekranu pobierania (UI9): pakiety + LutCacheInfo z [lutPackRepository]. */
    val lutDownloadStateHolder: LutDownloadStateHolder by lazy {
        val files = context.applicationContext.filesDir
        LutDownloadStateHolder(
            repository = lutPackRepository,
            cacheInfo = lutPackRepository,
            settings = lutDownloadSettings,
            freeSpaceBytes = { files.usableSpace },
            scope = appScope
        )
    }

    val photoRenderer: PhotoRenderer by lazy { BitmapPhotoRenderer { photos[it] } }

    /** Rejestruje zdjęcie (np. zdekodowaną miniaturę z galerii) pod kluczem używanym przez ekrany. */
    fun putPhoto(key: Int, bitmap: ImageBitmap) {
        photos[key] = bitmap
    }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) { instance ?: AppGraph(context).also { instance = it } }
    }
}
