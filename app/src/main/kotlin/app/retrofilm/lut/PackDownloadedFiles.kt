package app.retrofilm.lut

import android.content.Context
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.pack.HaldClutDecoder
import app.retrofilm.core.engine.pack.LutDownloadFeature
import app.retrofilm.core.engine.pack.LutPackFiles
import app.retrofilm.core.engine.pack.LutRawCache
import app.retrofilm.core.ui.engine.FilmLut
import app.retrofilm.engine.DownloadedFilms

/**
 * Pobrane filmy (D8) widziane przez silnik: wyłącznie z plików na urządzeniu i katalogu z APK.
 * Nie dotyka WorkManagera ani jego obserwatorów (to robi tylko [WorkLutPackRepository] dla ekranów
 * pobierania), więc listowanie i ładowanie filmów w silniku jest tanie i bezpieczne w testach.
 */
class PackDownloadedFiles(context: Context, private val cache: LutRawCache) : DownloadedFilms {
    private val app = context.applicationContext
    private val entries by lazy { if (LutDownloadFeature.ENABLED) LutPackFiles.catalog(app) else emptyList() }

    /** Pobrane i gotowe filmy (PNG na urządzeniu); startowe z APK są w katalogu silnika. */
    override fun available(): List<FilmLut> = entries
        .filter { !it.bundled && LutPackFiles.pngFile(app, it).exists() }
        .map { FilmLut(it.id, it.displayName) }

    /** LUT pobranego filmu; rozpakowanie PNG → raw przy pierwszym użyciu. `null` gdy niepobrany. */
    override fun lutFor(id: String): Lut? {
        val entry = entries.firstOrNull { it.id == id && !it.bundled } ?: return null
        val png = LutPackFiles.pngFile(app, entry)
        return if (png.exists()) {
            cache.get(entry.localName, entry.grid, entry.unpackedLength) {
                png.inputStream().use { HaldClutDecoder.decode(it, entry.grid) }
            }
        } else {
            null
        }
    }
}
