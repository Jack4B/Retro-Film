# Wydanie J (v-test-2026-11-04-J)

| # | Zadanie | Stan |
|---|---------|------|
| 1 | Pełna ścieżka zapisu na GPU (AGSL F16 + GLES3, kafle z halo, testy pikselowe) | **Nie zrobione** – patrz niżej |
| 2 | Etap ramki 300–575 ms | Zrobione: płótno rośnie wokół zdjęcia, zdjęcie kopiowane 1:1 (bez Lanczosa 12 MP); Robolectric 12 MP: 44 ms; `FrameNativeTest` |
| 3 | Analiza DNG F16 + BaselineExposure | Zrobione: `RawLoader.analyseHeadroom` (dekod 512 px, LINEAR_EXTENDED_SRGB F16), `HdrAnalysis.rawHeadroom`; `RawHeadroomTest`, `DngImportTest.analysesHeadroomFromF16Decode` |
| 4 | Przepalone próbki | Zrobione: wykluczone tylko z p99,5 (zapas); klucz ekspozycji z cenzurą (ogon bez pamięci, limit 0,1 ln); ColorChecker −2…+1 EV ≤ 0,18 EV; `AutoKeyCensoredTest` |
| 5 | Pełny podgląd ciepły 302 vs 230 ms | Przyczyna: pomiar zapasu HDR z gain mapy (I) szeregowo przed dekodem podglądu; teraz równolegle (`async`), czekamy dopiero po dekodzie |
| 6 | Etap 6 – testy | `Stage6RegressionTest` (crash/save_error w PerfLog i CSV, kopia zapasowa encode→decode→restore), chip HDR: `RedesignScreenshotTest.hdrChipToggles` |
| 7 | Postęp zapisu per sesja | Zrobione: `SaveProgress` jako klasa w `SaveTrace`, `StripIo.progress`; `SaveProgressTest` |

## Dlaczego nie GPU (1)
Po naprawie ramki i H/I zapis CPU jest kaflowy i równoległy; pełna ścieżka AGSL F16 z halo dla 5 efektów sąsiedztwa to
osobny, duży zakres. CI emulatora renderuje GPU programowo (SwiftShader), więc nie potwierdzi zysku – wymaga pomiaru na
telefonie. Zostaje na wydanie K, z CPU jako ścieżką referencyjną/zapasową.

## Uwagi
- Zmiana ramki: plik z ramką jest większy o margines (np. 4000×3000 → 4333×3333), zdjęcie w nim ma natywną ostrość.
- DNG: analiza zapasu działa równolegle z dekodem podglądu.
