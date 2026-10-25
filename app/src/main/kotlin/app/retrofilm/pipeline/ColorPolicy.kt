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

    /** Zwraca bitmapę w sRGB (z konwersją kolorów przez Skia) lub oryginał, gdy już jest sRGB. */
    fun toSrgb(photo: Bitmap): Bitmap {
        if (!needsSrgbConversion(photo.colorSpace?.let { it == SRGB })) return photo
        val out = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888, photo.hasAlpha(), SRGB)
        Canvas(out).drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
