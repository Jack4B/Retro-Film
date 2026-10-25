package app.retrofilm.ui.library

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * Gotowe miniatury JPG filmów w APK (D12): `assets/thumbs/<kategoria>/<nazwa>.jpg`,
 * generowane narzędziem `tools/thumbs/make_thumbs.py`. Brak pliku to zwykły stan (zwraca `null`).
 */
object FilmThumbAssets {
    fun path(id: String): String = "thumbs/$id.jpg"

    fun load(assets: AssetManager, id: String): Bitmap? = try {
        assets.open(path(id)).use { BitmapFactory.decodeStream(it) }
    } catch (_: IOException) {
        null
    }
}
