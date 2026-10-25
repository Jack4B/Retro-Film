package app.retrofilm.engine

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory
import app.retrofilm.core.ui.photo.PhotoRenderer
import java.util.concurrent.ConcurrentHashMap

/** Prosta fabryka zależności aplikacji (bez frameworka DI). Jedna instancja na proces. */
class AppGraph(context: Context) {
    private val photos = ConcurrentHashMap<Int, ImageBitmap>()

    val filmEngine: EngineFilmEngine by lazy {
        EngineFilmEngine(LutCatalog(context.applicationContext.assets), LutEngineFactory.create())
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
