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
- LUT-y 13×13×13 → shader **musi** używać interpolacji trójliniowej lub tetraedrycznej.
- Ścieżka CPU jako referencja dla testów.
- ffmpeg-kit usunięty.

## Przepływ danych
UI (Compose) → zdarzenia → ViewModel → stan (StateFlow) → UI. Ciężka praca poza wątkiem głównym; podglądy zmniejszone, pełna rozdzielczość tylko przy eksporcie.

## Jakość
Testy jednostkowe; golden: identity LUT dE < 1, GPU vs CPU ≤ 1–2 poziomy/kanał (inaczej blocker); Roborazzi; Baseline Profiles; ktlint/detekt; GitHub Actions CI; podpisane APK w Releases (Obtainium); `versionCode` zawsze rośnie.
