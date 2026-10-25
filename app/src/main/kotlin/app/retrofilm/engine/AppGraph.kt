package app.retrofilm.engine

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory
import app.retrofilm.core.ui.photo.PhotoRenderer
import app.retrofilm.feature.settings.lut.LutDownloadStateHolder
import app.retrofilm.lut.PrefsLutDownloadSettings
import app.retrofilm.lut.WorkLutPackRepository
import app.retrofilm.settings.AppSettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Prosta fabryka zależności aplikacji (bez frameworka DI). Jedna instancja na proces. */
class AppGraph(context: Context) {
    private val photos = ConcurrentHashMap<Int, ImageBitmap>()

    val filmEngine: EngineFilmEngine by lazy {
        EngineFilmEngine(LutCatalog(context.applicationContext.assets), LutEngineFactory.create())
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Ustawienia aplikacji w DataStore (dynamiczne kolory). */
    val appSettings: AppSettingsRepository by lazy { AppSettingsRepository.create(context) }

    val lutDownloadSettings: PrefsLutDownloadSettings by lazy { PrefsLutDownloadSettings(context) }

    /** Pakiety pobierane (D8); implementuje też LutCacheInfo. */
    val lutPackRepository: WorkLutPackRepository by lazy {
        WorkLutPackRepository(context, lutDownloadSettings, appScope)
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
