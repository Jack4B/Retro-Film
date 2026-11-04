# Ocena automatycznej korekty („Popraw światło i kolor” / „Przygotuj pod film”)
Branch `test` @ 9e4ee37, przegląd tylko do odczytu. Ścieżki względem `app/src/main/kotlin/app/retrofilm/`.

## 1. Jak to działa teraz
- **Wywołanie:** `ui/MainState.kt:663-688`. Analiza idzie na podglądzie (`previewBase`, `:702`) po geometrii, przed filmem (D15), więc nie zależy od LUT-u. `forFilm()` (`pipeline/AutoEnhance.kt:22-27`) zostawia balans bieli i ekspozycję, kontrast i nasycenie tylko w dół (≤0), a światła i cienie zeruje.
- **Wejście:** 8-bitowy bitmap sRGB, czyli obraz już po krzywej tonalnej telefonu (display-referred). Próbkowanie punktowe co `step` pikseli do ok. 256 px, bez uśredniania (`AutoEnhance.kt:102-117`). Linearyzacja to tylko odwrotność sRGB (`:111-113`), więc przestrzeń jest „liniowa”, ale **nie scene-referred**. HDR: analizowany jest tylko obraz bazowy SDR, gain mapy się nie używa (`pipeline/UltraHdrJpeg.kt`). DNG: dekoder platformy do bitmapy (`pipeline/RawLoader.kt:10-30`) z jego WB i krzywą. Nie ma jawnej obsługi BaselineExposure ani analizy w float.
- **Balans bieli** (`pipeline/AutoWhiteBalance.kt`):
  - Metoda shades-of-gray p=2 (`:180,205-215`), liczona tylko z pikseli `usable`: bez nieba, zieleni, chromy OKLab ≥0,12 i skóry, gdy są twarze (`pipeline/SceneAnalysis.kt:92-94`).
  - Odrzuca piksele bardzo ciemne (Y<0,01) i przepalone (max>0,9) (`:217-222`). To dobrze.
  - Siła 50% (`:181`), mniejsza przy dominującej barwie albo małej liczbie pikseli neutralnych (`:197-199`).
  - Wynik jest rzutowany na suwaki temp/tint, które leżą na krzywej światła dziennego + tint, z limitem ±0,4 (ok. ±28 miredów) i przeszukiwaniem siatki (`:266-294`). Potem idzie adaptacja Bradforda w XYZ (`pipeline/WhiteBalance.kt:49-63`). To jest poprawny CAT, a nie mnożniki kanałów RGB.
  - Ochrona skóry: korektę zmniejsza się w krokach, gdy odcień skóry (OKLab, ok. 55°) by się oddalał (`:228-240`).
- **Ekspozycja** (`AutoEnhance.kt:123-139`):
  - Mediana Y (Rec.709, po WB) dąży do 0,18. Przy skórze cel to 0,30 lin. (ok. 58% sRGB), z wagą 0,7 z twarzami i 0,4 bez nich.
  - Siła 0,6, limit ±1,5 EV, martwa strefa 0,05 EV.
  - Ochrona świateł: p99·gain ≤ 1,6, co celowo wpycha światła w miękkie kolano (`pipeline/Adjustments.kt:154`).
  - Jasne niebo ogranicza rozjaśnienie do +0,3 EV.
  - Przepalone piksele (255) **wchodzą** do mediany i do p99 jako 1,0.
- **Kontrast, cienie, światła** (`AutoEnhance.kt:81-89`) liczone są z percentyli po zakodowaniu sRGB, z twardym `coerceIn(0,1)`. Ten model nie odpowiada kolanu z procesora. W trybie Prep zostaje tylko kontrast ≤0.
- **Nasycenie** zależy od średniej chromy OKLab w półtonach (`:147-164`).
- **Kolejność w renderze:** WB i ekspozycja jako macierz w świetle liniowym, potem miękkie kolano, kodowanie sRGB, krzywe, LUT (`pipeline/Adjustments.kt:150-195`).

## 2. Werdykt
Algorytm jest dobry, wyraźnie lepszy od typowych „auto” w aplikacjach z filtrami: działa w świetle liniowym, ma CAT Bradforda, robust shades-of-gray z maskami, ochronę skóry i nieba, miękkie kolano i jest deterministyczny i offline.
**Da się go jednak poprawić**, głównie w trzech miejscach:
- (a) ekspozycja nie wie, jakiej szarości oczekuje dany LUT;
- (b) wejście to JPEG po krzywej telefonu, a nie obraz scene-referred, więc „18%” mierzy się na złej skali;
- (c) metering i estymacja oświetlenia mają proste słabości: przepały w medianie, próbkowanie punktowe, jeden estymator, korekta ograniczona do krzywej dziennej.

## 3. Ulepszenia (od największego zysku)
| # | Zmiana | Efekt wizualny | Koszt (Mali-G720, 12 MP) | Ryzyko |
|---|---|---|---|---|
| 1 | **Docelowa szarość per film.** Offline (przy budowie paczki albo przy pierwszym użyciu) przepuścić rampę szarości przez LUT i zapisać, przy jakim wejściu wyjście daje ok. 18% lin. i gdzie zaczyna się ramię. Auto celuje w tę wartość zamiast w stałe 0,18 (`AutoEnhance.kt:57`). | Duży: mniej filmów „za ciemnych/za jasnych” i prawidłowe roll-offy | ~0 w runtime (jedna liczba na LUT, kalibracja ms) | Niskie. Wymaga, żeby auto znało wybrany film (dziś analiza jest celowo niezależna od filmu, więc trzeba dodać parametr) |
| 2 | **Metering bez przepałów, log-średnia.** Wykluczyć piksele z kanałem ≥0,98 lin. z mediany i p99. Liczyć kluczową jasność z log-średniej i ważenia środka i twarzy (np. 60/40). Warunek świateł: p99,5 nieprzepalonych pod kolanem, a nie 1,6. | Średni–duży: koniec niedoświetlania kadrów ze słońcem lub lampą, stabilniejsze serie | <1 ms (256 px) | Niskie |
| 3 | **Próbkowanie z uśrednianiem bloków** (box/area average zamiast punktu, `AutoEnhance.kt:109`) | Mniejszy wpływ szumu i aliasingu na WB w nocy, wynik stabilny między podglądem a eksportem | <2 ms | Bardzo niskie |
| 4 | **Zespół estymatorów oświetlenia:** shades-of-gray p≈4–6 + gray-edge 1. rzędu (Weijer 2007) + white-patch z odrzuceniem przepałów (np. 95. percentyl). Mediana w chromatyczności i pewność z rozrzutu estymatorów zamiast stałych 0,5/0,4. | Średni: mniej błędów przy scenach z dużymi kolorowymi płaszczyznami i przy LED/świetlówkach (odchyłka od krzywej dziennej) | 2–5 ms | Średnie (strojenie progów) |
| 5 | **Korekta poza krzywą dzienną:** liczyć macierz Bradforda/CAT16 wprost z oszacowanego xy, zamiast rzutować na temp/tint z limitem ±0,4 (`AutoWhiteBalance.kt:182,266-294`). Wtedy suwaki pokazują tylko przybliżenie. | Mały–średni (świetlówki, sód, mieszane światło) | ~0 | Niskie–średnie (UI musi pokazać wartość spoza suwaków) |
| 6 | **Przybliżona linearyzacja krzywej telefonu** w trybie Prep: odwrócić typowy S-kształt (stała krzywa parametryczna, np. odwrotny filmic z odcięciem cieni), zamiast kontrastu ≤0. LUT dostaje obraz bliższy scene-referred (w duchu ACES), a ekspozycja liczy się na tej skali. | Duży dla JPEG z telefonu (ramię filmu zadziała jak na negatywie) | <1 ms (LUT 1D) | Średnie: różni producenci mają różne krzywe, więc lepiej dać przełącznik lub siłę |
| 7 | **DNG/HDR w float:** dekodować RAW do `RGBA_F16` (liniowy, z BaselineExposure z tagu), a dla Ultra HDR policzyć headroom z gain mapy. Auto widzi wtedy prawdziwe światła powyżej 1,0 i może ustawić ekspozycję pod ramię filmu. | Duży dla RAW/HDR (odzyskane chmury i lampy) | Pamięć: F16 12 MP ≈ 96 MB, więc trzeba analizować na zmniejszonej kopii | Średnie (zależność od dekodera platformy, API) |
| 8 | Kontrast i percentyle liczyć przez **ten sam model kolana** co render, zamiast `coerceIn` (`AutoEnhance.kt:81`) | Mały | ~0 | Niskie |
| 9 | Siła ekspozycji 0,6 (`:59`) zależna od pewności (np. 0,8, gdy brak przepałów i są twarze) | Mały–średni | 0 | Niskie |

Bez ML i sieci: wszystkie zmiany to deterministyczna matematyka, offline, bez telemetrii.

## 4. Plan testów
- **Syntetyczna ColorChecker:** sRGB/lin. wartości referencyjne (BabelColor), oświetlona Planckiem 2856 K / 4000 K / F2 / LED / D50 / 7500 K, ±2 EV, opcjonalnie z „krzywą telefonu” i szumem.
  - Metryki: średni ΔE2000 na 24 polach i osobno na 6 szarych po auto WB; błąd kątowy oświetlenia (°); błąd ekspozycji szarości 18% w EV (|log2(Y/0,18)|).
  - Progi: ΔE00 szarości <3, kąt <3°, EV <0,25.
- **Sceny trudne:** duża czerwona płaszczyzna, zachód słońca (WB nie może zgasić koloru), portret na zielonym tle (skóra w 55±12°), kadr ze słońcem (5% przepałów; ekspozycja nie może spaść >0,3 EV względem kadru bez słońca), noc z szumem (stabilność wyniku przy różnych seedach szumu <0,1 EV).
- **Per-film:** dla każdego LUT-u szara rampa przez auto + LUT; szarość wyjściowa w tolerancji celu filmu.
- **Regresja:** obecne `AutoEnhanceTest`, `AutoResultForFilmTest`, `SceneAnalysisTest`, `WhiteBalanceTest` (JVM). Opcjonalnie zbiór Gehler-Shi/NUS lokalnie jako fixture (offline, nie w APK).
- **Roborazzi:** zrzuty ekranu edytora z włączonym Auto/Prep na fixture'ach (portret, zachód, noc) + złote obrazy pipeline'u z porównaniem progowym.
- **Wydajność:** microbenchmark `suggest()` na 256 px <10 ms na urządzeniu; brak wpływu na 12 MP eksport (analiza tylko na podglądzie).
