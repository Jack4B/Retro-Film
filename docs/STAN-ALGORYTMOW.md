# Stan algorytmów (do aktualizacji po każdej zmianie)

Legenda: ✅ gotowe (metoda z literatury lub standard branżowy, testy, sprawdzone na zdjęciach testowych) ·
🟡 działa, ale do poprawy · ❌ brak. Szczegóły metod: docs/ALGORYTMY.md. Raport liczbowy: gałąź `quality-report`
(workflow „Quality report”). Zadania do poprawy są też w liście zadań sesji.

| Moduł | Plik | Stan | Co zostało |
|---|---|---|---|
| Silnik filmów (LUT 3D) | core/engine | ✅ | golden testy (identity dE < 1, GPU vs CPU) |
| Ekspozycja i kolano świateł | pipeline/Adjustments.kt | ✅ | mnożnik w świetle liniowym, miękkie kolano |
| Balans bieli (temperatura, tint) | pipeline/WhiteBalance.kt | ✅ | adaptacja Bradforda, stała luminancja bieli |
| Cienie i światła | Adjustments.kt, ToneMask.kt | ✅ v4 | baza z filtru prowadzonego + detal; ocenić na telefonie |
| Kontrast | ColorGrade.kt (LuminanceContrast) | ✅ | opcja krzywej filmowej z pivotem 18% |
| Nasycenie / vibrance | ColorGrade.kt (ChromaScaler) | ✅ | OKLab, ochrona skóry, gamut bisekcją |
| Wyrazistość | DetailFilter.kt | ✅ | baza z filtru prowadzonego (bez halo) |
| Wyostrzanie | Sharpener.kt, SharpenPlanner.kt | ✅ | USM / dekonwolucja RL, maska krawędzi z podglądem, Auto; brak wyostrzania wyjściowego |
| Dehaze | Dehaze.kt | 🟡 | ochrona nieba, korekta jasności po dehaze (zadanie #5) |
| Odszumianie kolorów | ChromaDenoise.kt | 🟡 | brak odszumiania jasności (zadanie #2) |
| HSL | HslMixer.kt | 🟡 | przejście na OKLCH (zadanie #1) |
| Tonowanie | SplitToning.kt | 🟡 | OKLab, balans (zadanie #3) |
| Winieta | ColorGrade.kt (VignetteLut) | ✅ | cos⁴; ewentualnie środek/zaokrąglenie |
| Ziarno | FilmGrain.kt | 🟡 | rozmiar, gęstość, liniowe światło (zadanie #4) |
| Blaknięcie | Adjustments.kt | 🟡 | proste (podniesiona czerń, przytłumiona biel) |
| Dithering 8 bit | Dither.kt | ✅ | TPDF ±0,5 |
| Geometria i kadr | Geometry.kt | 🟡 | resampling Lanczos, perspektywa, kadr gestem (zadanie #6) |
| Ramka i podpis | core/frame, FrameRenderer.kt | ✅ | podpis zachowuje rozmiar z suwaka, zawijanie |
| Znak wodny QR | Watermark.kt | ✅ | ikona + nazwa + QR (macierz z góry, zweryfikowana) |
| Presety użytkownika | Presets.kt | ✅ | brak kadru/ramki w presecie (świadomie) |
| Eksport | ExportPipeline.kt | 🟡 | wybór jakości/rozmiaru (zadanie #8); GPS usuwany ✅ |
| Auto-ulepszenie, krzywe tonalne, maski lokalne | — | ❌ | zadanie #7 |
