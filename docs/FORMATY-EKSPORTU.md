# Formaty eksportu (Batch C)

| Format | Status | Uwagi |
|---|---|---|
| JPEG 8-bit | domyślny | 4:4:4 (koder Kotlin, pasma równoległe) lub 4:2:0 (`Bitmap.compress`), EXIF z białej listy, ICC |
| JPEG Ultra HDR | API 34+, przełącznik „HDR ze zdjęcia” (domyślnie WYŁ.) | gain map ze źródła (dekodowana z inSampleSize 8), przeskalowana do 1/4 wyjścia, ta sama geometria; wymusza 4:2:0 i wyłącza ramkę |
| PNG 16-bit | gotowy | własny zapis (`Png16Writer`, filtr Up, iCCP/sRGB). Ok. 10 bitów realnej precyzji (8 bit + 2 bity ułamka z potoku float) bez ramki i zmiany rozmiaru; z ramką/skalowaniem 8 bit w kontenerze 16. Bez EXIF |
| HEIC 8-bit | gdy urządzenie ma koder HEVC (API 28+) | `androidx.heifwriter` 1.0.0, tryb bitmapy. Bez EXIF |
| RAW/DNG import | Batch B | wskaźnik „Wczytywanie RAW…” dla DNG bez podglądu, Orientation 9 = normalna, WB aparatu + płaski profil |

## Odłożone: AVIF i 10-bit
- **AVIF**: `Bitmap.CompressFormat` nie ma AVIF; koder AV1 w MediaCodec jest rzadki na telefonach (realme GT 7T – brak sprzętowego kodera), a programowy libavif/libaom to kilka MB natywnych bibliotek i sekundy na 12 MP. Wrócimy, gdy Android udostępni AVIF w `compress`.
- **10-bit HEIC/HEIF (P010)**: HeifWriter 1.0 przyjmuje tylko bitmapy 8-bit lub Surface; 10-bit wymaga ręcznej konfiguracji MediaCodec HEVC Main10 + własnego muxera HEIF – niestabilne między producentami. **JPEG 10-bit** nie jest obsługiwany przez przeglądarki/galerie. Na precyzję >8 bit służy PNG 16, a na HDR – Ultra HDR (gain map).
