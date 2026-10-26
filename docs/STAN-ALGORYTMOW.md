# Stan algorytmów (do aktualizacji po każdej zmianie)

Legenda: ✅ gotowe (metoda z literatury lub standard branżowy, testy, sprawdzone na zdjęciach testowych) ·
🟡 działa, ale do poprawy · ❌ brak. Szczegóły metod: docs/ALGORYTMY.md. Raport liczbowy: gałąź `quality-report`
(workflow „Quality report”: 5 zdjęć testowych, sztuczna mgła i szum o znanych parametrach, arkusze przed/po).

| Moduł | Plik | Stan | Co zostało |
|---|---|---|---|
| Silnik filmów (LUT 3D) | core/engine | ✅ | golden testy (identity dE < 1, GPU vs CPU) |
| Ekspozycja i kolano świateł | pipeline/Adjustments.kt | ✅ | mnożnik w świetle liniowym, miękkie kolano |
| Balans bieli (temperatura, tint) | pipeline/WhiteBalance.kt | ✅ | adaptacja Bradforda, stała luminancja bieli |
| Cienie i światła | Adjustments.kt, ToneMask.kt | ✅ v4 | baza z filtru prowadzonego + detal, ochrona czerni; przecieki ≈ 0 |
| Kontrast | ColorGrade.kt (LuminanceContrast) | ✅ | opcja krzywej filmowej z pivotem 18% |
| Nasycenie / vibrance | ColorGrade.kt (ChromaScaler) | ✅ | OKLab, ochrona skóry, gamut bisekcją |
| HSL | HslMixer.kt, OkLab.kt | ✅ | OKLCH, zakresy wg kątów pierwotnych sRGB |
| Tonowanie | SplitToning.kt | ✅ | OKLab, bez zmiany L |
| Wyrazistość | DetailFilter.kt | ✅ | baza z filtru prowadzonego (bez halo) |
| Wyostrzanie | Sharpener.kt, SharpenPlanner.kt | ✅ | USM / dekonwolucja RL, maska krawędzi z podglądem, Auto |
| Dehaze | Dehaze.kt | ✅ | DCP + filtr prowadzony, ochrona nieba, kompensacja jasności (0,25) |
| Odszumianie kolorów | ChromaDenoise.kt | ✅ | Cb/Cr z jasnością jako przewodnikiem |
| Odszumianie jasności | LumaDenoise.kt | ✅ | filtr prowadzony sterowany szumem; do rozważenia NLM/wavelet dla silnego szumu |
| Winieta | ColorGrade.kt (VignetteLut) | ✅ | cos⁴; ewentualnie środek/zaokrąglenie |
| Ziarno | FilmGrain.kt | ✅ | model kryształów srebra, mnożnik w świetle liniowym, tekstura Gaussa |
| Blaknięcie | Adjustments.kt | ✅ | podniesiona czerń, przytłumiona biel, odbarwienie |
| Dithering 8 bit | Dither.kt | ✅ | TPDF ±0,5 |
| Geometria: obrót, kadr, perspektywa | Geometry.kt, Perspective.kt, CropFit.kt, BicubicWarp.kt | ✅ | perspektywa pion/poziom z auto-kadrem, kadr gestem (przeciągnięcie, szczypnięcie), siatka trójpodziału |
| Zmiana rozmiaru | Resampler.kt | ✅ | Lanczos 3 w eksporcie i podglądzie |
| Ramka i podpis | core/frame, FrameRenderer.kt | ✅ | podpis zachowuje rozmiar z suwaka, zawijanie |
| Znak wodny QR | Watermark.kt | ✅ | ikona + nazwa + QR |
| Presety użytkownika | Presets.kt | ✅ | bez kadru/ramki (świadomie) |
| Eksport | ExportPipeline.kt | ✅ | jakość JPEG, maks. rozmiar, wyostrzanie wyjściowe; GPS usuwany |
| Auto-ulepszenie, krzywe tonalne, maski lokalne | — | ❌ | do zrobienia |

## Raport końcowy (alpha.16, CI, 5 zdjęć testowych)

- Cienie +100%: ciemna ćwiartka +20…+28 poziomów (na bardzo jasnym zdjęciu +7), przeciek na jasne partie 0…+7, zmiana barwy < 0,4°.
- Światła −100%: jasna ćwiartka −9…−75 poziomów, przeciek na ciemne partie 0…−6.
- Wyrazistość +80%: energia detalu +3…+35%, średnia jasność ±1.
- Dehaze (sztuczna mgła, PSNR vs oryginał): +2…+7 dB na 3 z 5 zdjęć; na ciemnych (kwiat, owoce) kompensacja jasności obniżała PSNR – zmniejszona do 0,25.
- Odszumianie kolorów: błąd chrominancji −50…−80%, dryf jasności ≈ 1,5 poziomu.
- Nasycenie +60%: barwność +13…+32% bez przycinania kanałów.
- Wydajność (12 Mpx, runner CI): wszystko naraz ≈ 4,3 s (telefon kilka razy wolniej); OKLab i wyostrzanie zoptymalizowane tablicami i prostszym soft-clipem.
