# Architektura

## Stos
Kotlin, Jetpack Compose, Material 3 Expressive. Domyślnie ciemny motyw marki (akcent `#F5A33B`), dynamic color opcjonalnie w Ustawieniach (domyślnie wył.). Tylko Android (iOS – nie teraz).

MVVM + UDF, Hilt, Coroutines/Flow, Room (projekty, LUT-y), Coil 3 (podglądy ~1080 px), Photo Picker, WorkManager (eksport serii z powiadomieniem postępu i anulowaniem).

## Moduły
| Moduł | Odpowiedzialność |
|---|---|
| `app` | Nawigacja, DI, motyw |
| `core:engine` | LUT na GPU: AGSL RuntimeShader (Android 13+), fallback OpenGL ES; parser `.cube`; korekty |
| `core:frame` | Ramka i podpis (render wg RAMKA-SPEC) |
| `core:batch` | Przetwarzanie serii (WorkManager) |
| `core:data` | Room, katalog LUT-ów |
| `core:export` | Zapis JPEG/PNG, EXIF bez GPS |
| `feature:*` | Po jednym na ekran (start, seria, edytor, lut, ramka, eksport, ustawienia) |

## Silnik
- Parser `.cube` i korekty przeniesione z oryginału (`SkiaImageProcessor`, `ImageProcessingShader`, `ImageAdjustments`).
- Docelowa siatka LUT: **33³**; interpolacja trójliniowa lub tetraedryczna (zob. docs/LUTS.md).
- Ścieżka CPU jako referencja dla testów.
- ffmpeg-kit usunięty.

## Silnik oryginału
Na podstawie `ImageProcessingShader.kt` z Film-Simulator:
- Jeden runtime shader SkSL.
- LUT jako tekstura 2D: szerokość `lutSize`, wysokość `lutSize²` (y = b·size + g); ręczna interpolacja trójliniowa (8 próbek).
- `lutIntensity` 0..2 – mieszanie z oryginałem.
- Następnie konwersja do światła liniowego: ekspozycja (`exp2`), kontrast wokół średniej szarości, cienie/światła z maską luminancji, nasycenie (Rec.709).
- Powrót do sRGB: temperatura (przesunięcie barw), ziarno (tani hash w podglądzie vs emulacja filmu przy eksporcie), aberracja chromatyczna.
- Podgląd i eksport używają tego samego shadera w różnych rozdzielczościach.

**U nas:** eksport używa tego samego shadera w pełnej rozdzielczości; jedynie ziarno przełącza się na ścieżkę jakości eksportowej.
- `.cube` parsowany w `SkiaImageProcessor.parseCubeLut`.

**Port:** przenosimy go do AGSL RuntimeShader (ten sam dialekt SkSL – praktycznie 1:1), z fallbackiem OpenGL ES dla Androida < 13.

## Przepływ danych
UI (Compose) → zdarzenia → ViewModel → stan (StateFlow) → UI. Ciężka praca poza wątkiem głównym; podglądy zmniejszone, pełna rozdzielczość tylko przy eksporcie.

## Jakość
Testy jednostkowe; golden: identity LUT dE < 1, GPU vs CPU ≤ 1–2 poziomy/kanał (inaczej blocker); Roborazzi; Baseline Profiles; ktlint/detekt; GitHub Actions CI; podpisane APK w Releases (Obtainium); `versionCode` zawsze rośnie.
