package app.retrofilm.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint

/**
 * Zasada barw eksportu: LUT-y liczone są w sRGB, więc każde zdjęcie spoza sRGB (np. Display P3)
 * konwertujemy do sRGB przed filmem, a wynik zapisujemy jako sRGB. Dzięki temu piksele
 * i profil ICC w pliku zawsze sobie odpowiadają.
 */
object ColorPolicy {
    /** Czysta decyzja: konwersja potrzebna, gdy źródło ma znaną przestrzeń inną niż sRGB. */
    fun needsSrgbConversion(sourceIsSrgb: Boolean?): Boolean = sourceIsSrgb == false

    val SRGB: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

    /**
     * Zwraca bitmapę w sRGB lub oryginał, gdy już jest sRGB. Display P3 przechodzi przez mapowanie gamutowe w OKLab
     * ([GamutMapper]), inne przestrzenie przez konwersję Skia ([toSrgbClipped]).
     */
    fun toSrgb(photo: Bitmap): Bitmap {
        if (!needsSrgbConversion(photo.colorSpace?.let { it == SRGB })) return photo
        return if (GamutMapper.supports(photo)) GamutMapper.toSrgb(photo) else toSrgbClipped(photo)
    }

    /** Konwersja Skia (kolory spoza sRGB są przycinane kanałami); ścieżka zapasowa i wzorzec w testach. */
    internal fun toSrgbClipped(photo: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888, photo.hasAlpha(), SRGB)
        Canvas(out).drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
