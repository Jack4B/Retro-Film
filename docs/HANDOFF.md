# Handoff – dziennik

Nowe wpisy na górze. Szablon:

```
## RRRR-MM-DD – <autor/rola>
- Faza:
- Zrobione:
- PR / gałąź:
- Otwarte / P2 odłożone:
- Następny krok:
```

## 2026-10-26 – Claude (wydanie 1.1.2: podgląd maski krawędzi)
- Faza: poprawka po zgłoszeniu „podgląd krawędzi przy wyostrzaniu nie działa”, wydanie 1.1.2
- Zrobione: podgląd maski pokazuje obraz maski także przy suwaku Ilość = 0 (wcześniej wymagał Ilość > 0, więc przełącznik wyglądał na martwy); przełącznik stoi pod Maskowaniem krawędzi; przy Maskowaniu = 0 podpowiedź („maska jest cała biała”, bo bez maskowania wyostrzane jest wszędzie); `SharpenMaskPreviewTest`. Przyczyny nie udało się odtworzyć na urządzeniu – jeśli w 1.1.2 przy Ilość i Maskowanie > 0 podgląd nadal nie pokazuje krawędzi, szukać dalej w ścieżce `AdjustmentsApplier.applyLegacy` / `DetailFilter.apply` (podgląd idzie ścieżką legacy, po filmie).
- PR / gałąź: #112 (`f33/podglad-maski`); wersja 1.1.2 z taga `v1.1.2`.
- Otwarte / P2 odłożone: bez zmian względem 1.1.1.
- Następny krok: ocena 1.1.2 na telefonie (podgląd maski).

## 2026-10-26 – Claude (wydanie 1.1.1: poprawka zakładek)
- Faza: poprawka po ocenie 1.1.0 na telefonie, wydanie 1.1.1
- Zrobione: szerokość zakładek proporcjonalna do długości etykiety (pięć zakładek; „Wykończenie” przycinało się do „Wykończ…” na Realme GT7T). Podgląd maski krawędzi wyostrzania nie zniknął, tylko przeniósł się razem z wyostrzaniem do Wykończenie → Wyostrzanie (na dole panelu, pod przełącznikiem „Włosy i oczy mocniej, skóra słabiej”).
- Uwagi użytkownika (MyNameisMrBullet): (1) błąd – po włączeniu „Popraw światło i kolor” i „Przygotuj pod film” wyłączenie i ponowne włączenie pierwszej opcji nie wracało do oryginału (jedna kopia „przed” nadpisywana przez drugi przycisk); teraz to dwa niezależne przełączniki na wspólnej podstawie (ustawienia sprzed pierwszego włączenia), kolejność nakładania stała (Auto, potem Przygotuj), ręczna zmiana suwaka robi nową podstawę (`AutoToggleTest`); (2) ostrzeżenie „Zakończyć edycję?” przy wyjściu (przycisk wstecz i strzałka), tylko gdy są niezapisane zmiany (film, intensywność, korekty, kadr, ramka względem stanu z wczytania albo ostatniego zapisu); (3) Photo Picker nie otwiera się już sam przy starcie – widać ekran Start z przyciskami (pomysł „ekran główny”: rozbudowa o ostatnie zapisy i ostatnio użyte filmy w osobnym wydaniu).
- PR / gałąź: #111 (`f32/poprawki-ui`); wersja 1.1.1 z taga `v1.1.1`.
- Otwarte / P2 odłożone: rozbudowa ekranu Start (ostatnie zapisy, ostatnio użyte filmy); ostrzeżenie przy wyjściu nie obejmuje osobnych edycji innych zdjęć serii; bez zmian względem 1.1.0: zrzuty Roborazzi nowych paneli (wyłapałyby ucięty napis zakładki); szukanie funkcji po przeniesieniu (podgląd maski był w Korekty → Detal).
- Następny krok: ocena 1.1.1 na telefonie.

## 2026-10-26 – Claude (wydanie 1.1.0: faza C)
- Faza: redesign UX (faza C planu), wydanie 1.1.0
- Zrobione: pięć zakładek w kolejności pracy (Kadr · Korekty · Film · Wykończenie · Ramka; edytor otwiera się na Korektach), `FinishPanel` (klisza, wyostrzanie, tonowanie, krzywe), `AdjustPanel` tylko z korektami (Światło, Kolor, Kolory, Detal, Maski), osobne „Wyzeruj korekty” i „Wyzeruj wykończenie” (`corrections()` / `finishing()`), test kolejności zakładek (#109). Domknięcie P2 z 1.0.5: `DETAIL_MID` 1,6 / `DETAIL_FINE` 3,0 (kontrast w jasnych obszarach) i `SHADOW_DETAIL` 0,1 (szum w cieniach), dobrane offline w numpy (#108); raport jakości na 9 zdjęciach stockowych po #108: niezgodności brak; kontrast w jasnych obszarach po „Światła −100%” ×0,97–1,50 (beagles ×0,97), szum w ciemnej ćwiartce po „Cienie +100%” ×0,98–1,30 (portrait_stadium ×1,30).
- Decyzje: makiet osobno nie robiono (Jack4B: „rób C”; układ wynika z zatwierdzonych zakładek); DNG zawieszone.
- PR / gałąź: #108, #109; wersja 1.1.0 z taga `v1.1.0`.
- Otwarte / P2 odłożone: czas „wszystko naraz” 12 Mpx na runnerze CI 2886 ms wobec 2501 ms dawnego potoku (×1,15; było ×1,06–1,11; dochodzi mapa wyostrzania świadomego treści, analiza 565 ms) – do optymalizacji; brak makiet i zrzutów Roborazzi nowych paneli; `docs/EKRANY.md` opisuje ekrany sprzed v2 (dopisana nota o v3); pomiar pamięci z wymuszonym GC; halo wyostrzania na drobnym tekście; job „compile only” na PR-ach; B3 (odszumianie z DQT/NLM) odłożone; DNG zawieszone.
- Następny krok: ocena 1.1.0 na telefonie (układ zakładek, czy Korekty/Wykończenie są tam, gdzie się ich szuka).

## 2026-10-26 – Claude (wydanie 1.0.5: faza B)
- Faza: korekty bez niszczenia pikseli (faza B planu), wydanie 1.0.5
- Zrobione: B1 światła v5 (kompresja bazy w logarytmie, detal w dwóch skalach z `ToneMask`, chroma, ochrona wypaleń; stałe dobrane offline w numpy na 9 zdjęciach stockowych: kontrast w jasnych obszarach ×0,90–1,26; #103, #104, #106); B2 Biele, Czernie (krzywa ze stałymi 0 i 1) i Tekstura z bramką szumu (#104); B4 Ostrość koloru (`ChromaEdge`: chroma z jednolitej jasnościowo strony krawędzi po 4:2:0; #105); B5 wyostrzanie świadome treści (`SemanticSharpen`: włosy przez koherencję tensora struktury, oczy i skóra twarzy z `FaceBox`; przełącznik, domyślnie włączony; #105); B6 straż regresji suwaków (`SliderRegressionTest`, `sliders.md` w raporcie jakości) i cienie bez wzmacniania szumu (szum w ciemnej ćwiartce ×0,98–1,42; #106).
- Decyzje: B3 (odszumianie z tablic kwantyzacji JPEG i NLM) odłożone – na zdjęciach z telefonów (wysoka jakość) zysk znikomy, szum mierzony z obrazu; DNG zawieszone (decyzja Jack4B); faza C jako 1.1.0.
- PR / gałąź: #103–#106; wersja 1.0.5 z taga `v1.0.5`.
- Otwarte / P2 odłożone: szum w cieniach na portrait_stadium ×1,42 (cel ×1,3); kontrast w jasnych obszarach beagles ×0,90 (cel ≥ 0,95); metryka halo w `sliders.md` poprawiona dopiero w 1.0.5 (wcześniejsze tabele zawyżały halo dla suwaków tonalnych); pomiar pamięci z wymuszonym GC; halo wyostrzania na drobnym tekście; job „compile only” na PR-ach; zrzuty Roborazzi; faza C (redesign UX).
- Następny krok: ocena 1.0.5 na telefonie (światła, cienie, biele, czernie, tekstura, ostrość koloru, włosy i oczy), potem faza C (makiety → zatwierdzenie → kod).

## 2026-10-26 – Claude (wydanie 1.0.4: faza A)
- Faza: wierność barwy filmów (faza A planu), wydanie 1.0.4
- Zrobione: A0 pomiar wierności (`tools/fidelity/a0_fidelity.py`, #99); A1 kolejność korekty → film → wykończenie (D15; #100); A2 odbandowanie wejścia filmu (`Deband`, poprawka ≤ ±3 poziomy tylko na płaskich gradientach; #101); A3 przycisk „Przygotuj pod film” (balans bieli, ekspozycja, zdjęcie krzywej telefonu; tylko na żądanie; #101); A4 Display P3 → sRGB z mapowaniem gamutowym w OKLab zamiast przycinania kanałów (`GamutMapper`; #101).
- Decyzja: wydanie 1.0.4 zawiera tylko fazę A, bo Jack4B ocenia wierność filmów na telefonie (bramka po fazie A). Faza B (korekty bez niszczenia pikseli) idzie w kolejnym wydaniu, DNG po niej.
- PR / gałąź: #99, #100, #101; wersja 1.0.4 z taga `v1.0.4`.
- Otwarte / P2 odłożone: film w pełnej precyzji float odłożony (A0 pokazał małą wartość numeryczną: odchylenie od planu); faza B (B1 światła v5 … B6 straż regresji); DNG; pomiar pamięci z wymuszonym GC; halo wyostrzania na drobnym tekście; job „compile only” na PR-ach; zrzuty Roborazzi panelu Film; faza C (redesign UX).
- Następny krok: ocena filmów na telefonie (kolor, bandy na niebie, odcień nasyconych kolorów), potem B1.

## 2026-10-26 – Claude (wydanie 1.0.3)
- Faza: potok pasmowy (jeden bufor float), wydanie 1.0.3
- Zrobione: `StripPipeline` + `StripAnalysis` (#95): obraz przetwarzany pasami wierszy na kanałach float (`Planes`), jedna kwantyzacja z ditheringiem na końcu (koniec zaokrągleń po każdym etapie); mapy globalne w niskiej rozdzielczości (odszumianie koloru i dehaze ze źródła, maska tonalna i baza wyrazistości z podpróbkowania); ziarno i winieta po wyostrzaniu; maski lokalne po wyostrzaniu. Dawny potok zostaje jako `AdjustmentsApplier.applyLegacy` (podgląd maski ostrości, wzorzec w testach). Zgodność z dawnym na 9 zdjęciach stockowych: średnia różnica 0,5–1,2 poziomu, p99 ≤ 7.
- PR / gałąź: #95 (`f20/bufor`); wersja 1.0.3 z taga `v1.0.3`.
- Otwarte / P2 odłożone: czas „wszystko naraz” 12 Mpx na CI nowy ≈ 1,06–1,11× dawnego (cel ≤ 2,5 s dotrzymany; analiza ≈ 280–440 ms); szczyt pamięci NIE zmierzony (pomiar sterty w raporcie był nasycony śmieciami: 426 vs 429 MB) – zmierzyć z wymuszonym GC, przed twierdzeniem o oszczędności; test na telefonie zdjęcia z dehaze, odszumianiem i ziarnem (różnice vs 1.0.2 najbardziej prawdopodobne); halo wyostrzania na drobnym tekście; job „compile only” na PR-ach; zrzuty Roborazzi panelu Film.
- Następny krok: opinie z testów 1.0.3 na telefonie; pomiar pamięci.

## 2026-10-26 – Claude (wydanie 1.0.2)
- Faza: poprawki po testach na telefonie i wydajność, wydanie 1.0.2
- Zrobione: obrót ekranu nie kasuje edycji (`MainStateHolder` w ViewModelu, `configChanges` aktywności; #91), lupa i „+” zakładki Film jako przezroczyste przyciski na zdjęciu zamiast kolumny zasłaniającej przewijanie kategorii (#92), równoległe przetwarzanie obrazu (`Parallel`, do 4 wątków, wynik niezależny od liczby wątków; #90), szybsze ziarno, splot Gaussa i dehaze bez pełnowymiarowych tablic (#93), raport wydajności z rozgrzewką, medianą i rozbiciem na etapy (`quality-report.yml`).
- PR / gałąź: #90–#93 (`f14/wydajnosc`, `f15/obrot`, `f16/film-przyciski`, `f18/szybciej`); wersja 1.0.2 z taga `v1.0.2`.
- Otwarte / P2 odłożone: jeden bufor float zamiast kilku przebiegów 8-bit i mniejsze zużycie pamięci przy 12 Mpx; podział `AdjustmentProcessor` na etapy; halo wyostrzania na drobnym tekście (`street_bus`: 1,2% pikseli z przestrzeleniem > 12); zrzuty Roborazzi panelu Film i szuflady; job „compile only” na PR-ach; wybór narzędzia w zakładce Korekty; test obrotu na urządzeniu (czeka na opinię Jack4B).
- Następny krok: opinie z testów 1.0.2 na telefonie.

## 2026-10-26 – Claude (wydanie 1.0.1)
- Faza: jakość algorytmów i UI, wydanie 1.0.1
- Zrobione: edytor z szufladą (3 stany, uchwyt, podgląd zdjęcia na całą dostępną wysokość, pasek zdjęć i chip zakresu edycji jako półprzezroczysty overlay; poziomo panel boczny), edycja indywidualna zdjęcia w serii (EditBook), światła i cienie z zachowaniem detalu i barwy (pochmurne niebo nie szarzeje), dehaze bezpieczny na jasnych, szarych scenach (większa łatka, ω 0,85, T_MIN 0,35), raport jakości na 9 zdjęciach stockowych dobrej jakości (`quality-report.yml`, w tym metryka halo wyostrzania).
- PR / gałąź: #84–#88 (`f11/swiatlo`, `f12/dehaze`), wersja 1.0.1 z taga `v1.0.1`.
- Otwarte / P2 odłożone: wydajność (wszystko naraz ≈ 2,9–5,4 s przy 12 Mpx na runnerze CI, cel ≤ 2,5 s; jeden bufor float zamiast wielu przebiegów 8-bit, podział `AdjustmentProcessor`, ziarno i winieta po wyostrzaniu) – wydanie 1.0.2; wybór narzędzia w zakładce Korekty; halo wyostrzania do zbadania na zdjęciach z drobnym tekstem (`street_bus`).
- Następny krok: 1.0.2 – wydajność potoku, pomiary w raporcie jakości.

## 2026-10-26 – Claude (algorytmy pro, wydanie 1.0)
- Faza: jakość algorytmów, wydanie 1.0.0
- Zrobione: przepisane/nowe algorytmy (stan: docs/STAN-ALGORYTMOW.md, wszystko ✅): kolor i ton w OKLab/OKLCH, cienie i światła z maską tonalną (filtr prowadzony), wyostrzanie (USM, dekonwolucja, maska krawędzi, Auto), dehaze, odszumianie jasności i kolorów, ziarno (model kryształów srebra), geometria (bikubiczny obrót, perspektywa z auto-kadrem, kadr gestem, Lanczos), krzywe tonalne (PCHIP), maski lokalne (liniowa/radialna, cienie/światła z maską tonalną), auto-ulepszenie świadome sceny (twarze, skóra, niebo, dominanta), edycja serii (wspólna albo osobna dla zdjęcia), presety, znak wodny QR.
- PR / gałąź: #69–#83 (ostatnie: `f8/maski-ton-auto`, `f9/wersja-1-0`); wersja 1.0.0 z taga `v1.0.0`.
- Otwarte / P2 odłożone: automatyczne prostowanie horyzontu (Hough), maski pędzlem/luminancji, NLM dla silnego szumu, profil obiektywu, osobny podpis/ramka na zdjęcie w serii; testy na urządzeniach (kadr gestem, krzywe, maski) czekają na opinię Jack4B.
- Następny krok: opinie z testów na telefonie; poprawki P1/P2 według zgłoszeń.

## 2026-10-25 – Claude (Korekty)
- Faza: redesign – zakładka Korekty
- Zrobione: `Adjustments` (ekspozycja ±2 EV, kontrast, nasycenie, temperatura) i `AdjustmentMatrix` (czysta macierz 4×5) + `AdjustmentsApplier` (ColorMatrix) wpięte w `ExportPipeline.render` po nałożeniu filmu; zakładka Korekty w Edytorze (cztery suwaki zatwierdzane po puszczeniu, „Wyzeruj”); testy `AdjustmentsTest`. Wartości neutralne omijają krok (wynik filmu i golden bez zmian).
- PR / gałąź: `f5/korekty` (baza: `f5/biblioteka-filmow`, #32).
- Otwarte / P2 odłożone: korekty liczone w kodowaniu sRGB (nie w świetle liniowym) – prosty model; ziarno; cienie/światła; kopiuj/wklej ustawienia; zrzuty z telefonu (390); golden dla korekt na GPU, jeśli silnik dostanie je kiedyś natywnie.
- Następny krok: review Lumen → Gauge → Critic.

## 2026-10-25 – Claude (Biblioteka filmów)
- Faza: D12 – Biblioteka filmów z miniaturami
- Zrobione: ekran „Filmy” (wyszukiwarka, kategorie z licznikami, ★ ulubione, „Na telefonie”, „Pobierz” wszystkie, siatka kafelków z gotowych JPG z APK, stany: na telefonie / ↓ rozmiar / pobieranie % / błąd; dotknięcie wybiera lub pobiera, przytrzymanie = ulubione, akcje TalkBack); dodatkowy widok „Na moim zdjęciu” z `LutMemoryCache` (nie zastępuje JPG); logika w `LibraryModel` z testami JVM; wejście z zakładki Film (ikona lupy); miniatury JPG (260 plików) pochodzą z #31 (`app/src/main/assets/thumbs/`) – ten PR ich nie zawiera; do czasu scalenia #31 kafelki mają puste tło z nazwą.
- PR / gałąź: `f5/biblioteka-filmow` (baza: `f5/pobieranie-filmow`, #30).
- Otwarte / P2 odłożone: scalić #31 (komplet 260 miniatur od Forge) przed lub razem z tym PR; zrzuty z telefonu (390); test pobierania na urządzeniu.
- Następny krok: review Lumen → Gauge → Critic.

## 2026-10-25 – Claude (włączenie pobierania filmów)
- Faza: D8/D9 – pobieranie filmów
- Zrobione: `LutDownloadFeature.ENABLED = true`; `EngineFilmEngine` widzi filmy pobrane (`DownloadedFilms`, implementuje `PackDownloadedFiles` – same pliki, bez WorkManagera); lista filmów w Edytorze odświeża się po pobraniu (`FilmLibrary`); testy `EngineFilmEngineDownloadedTest` i `PackDownloadedFilesTest`.
- PR / gałąź: `f5/pobieranie-filmow` (baza: `f5/edytor-krok-1`, #27).
- Otwarte / P2 odłożone: nie sprawdziłem, czy tag `luts-v1` istnieje w Jack4B/Retro-Film-LUTs (poza moim zakresem) – bez niego pobieranie się nie uda; miniatury w APK dla filmów do pobrania i Biblioteka filmów (D12); test pobierania na urządzeniu/emulatorze.
- Następny krok: @Forge: emulator.yml i test pobierania na telefonie; potwierdzić tag `luts-v1`.

## 2026-10-25 – Claude
- Faza: redesign UI/UX, Edytor krok 1 (wg docs/OCENA-I-REDESIGN.md, PR #26)
- Zrobione: `app` – Start (jedno CTA), Edytor z zakładkami Film / Ramka, przytrzymanie zdjęcia = oryginał, moc filmu 0–100%, kategorie z id katalogu, miniatury filmów na zdjęciu użytkownika (`FilmThumbnails`, z `LutMemoryCache`, bez osobnej pamięci podręcznej), ulubione (DataStore), zapis jednym dotknięciem; `ExportPipeline.render` przyjmuje `intensity` (domyślnie 1f). Usunięty `GalleryScreen` z `feature:start` (decyzja Jack4B: zostaje Photo Picker z wyborem wielu zdjęć) razem z testem i zrzutami.
- PR / gałąź: `f5/edytor-krok-1`.
- Otwarte / P2 odłożone: pomiar miniatur na emulatorze (emulator.yml), zrzuty z telefonu (390) do porównania z makietami, seria (pasek miniatur, jeden film i jedna ramka na serię, podpis per zdjęcie), Korekty, miniatury w APK dla filmów do pobrania, Biblioteka filmów, wielokrotny wybór w pickerze – kolejne PR-y.
- Następny krok: CI → review Gauge → Critic.

## 2026-10-25 – Claude (narzędzie miniatur filmów)
- Faza: D12 – miniatury filmów do pobrania
- Zrobione: `tools/thumbs/make_thumbs.py` (HaldCLUT PNG → JPG 160 px + `thumbs.json`, kontrola budżetu), opis w `tools/README.md`; sprawdzone na 10 filmach startowych (≈3 KB/szt. na scenie testowej).
- PR / gałąź: `f5/miniatury-jpg`.
- Otwarte / P2 odłożone: wygenerować komplet 260 miniatur ze zdjęciem CC0 (wymaga kopii Retro-Film-LUTs@luts-v1 i sieci; nie mam do tego dostępu), wrzucić do `app/src/main/assets/thumbs/`, a po stronie aplikacji pokazać je w Bibliotece filmów (osobny PR).
- Następny krok: @Forge / Jack4B: uruchomić narzędzie na pełnej paczce.

## 2026-10-25 – Claude (przegląd koncepcji, makiety v2)
- Faza: poza fazami (ocena + redesign UI/UX)
- Zrobione: `docs/OCENA-I-REDESIGN.md` (ocena koncepcji, uwagi T1–T12), `docs/makiety/index.html` (8 makiet v2, nazwy filmów prawdziwe), decyzje D11 i D12 w `DECYZJE.md`, zmiany w `AGENTS.md`, `docs/LUTS.md` i `docs/EKRANY.md` (zgodnie z odpowiedziami Jack4B).
- PR / gałąź: `ccr-0dd79b3e-ffwulp`.
- Otwarte / P2 odłożone: README i PLAN nadal opisują stan sprzed kodu (T2); EKRANY.md ma notę o v2, ale treść sprzed v2; informacja o braku powiązania z producentami w „Licencje i autorzy” (do zatwierdzenia); kod UI w #27 („Edytor krok 1”), potem seria, Korekty, miniatury, Biblioteka filmów.
- Następny krok: review Gauge → Critic.
- Dopisane: D13 (motyw jasny/ciemny) i makiety w obu motywach (przycisk „Motyw” w `docs/makiety/index.html`).

## 2026-10-25 – Forge
- Faza: F3 (spięcie aplikacji)
- Zrobione: `MainActivity` – ścieżka testowa: Photo Picker → film (identity + katalog `assets/luts`) → ramka + podpis (`SignatureFitCheck`) → eksport JPEG (`JpegExporter`) do MediaStore `Pictures/RetroFilm` (API 26–28: SAF). Orientacja EXIF na pikselach (`ExifOrientation`, testy JVM), ICC zachowany (P3 → ramka → JPEG), trasa `settings` (placeholder).
- PR / gałąź: `f3/app-wiring`.
- Otwarte / P2 odłożone: 10 filmów startowych pojawi się po merge #11 (`luts/33`); LUT na zdjęciu P3 liczony w sRGB (silnik) – wynik wraca do P3 przez kanwę.
- Następny krok: review Gauge → Critic; podpiąć ekran Ustawień Lumena w `RetroFilmNavHost`.

## 2026-10-25 – Forge
- Faza: F1 (silnik LUT)
- Zrobione: `core:engine` – `LutEngine`, `Lut` (format binarny RGB8 33³, docs/LUT-BIN.md), backendy AGSL / GLES 3.0 (tekstura 3D) / CPU, fabryka z fallbackiem do CPU, testy JVM CPU, test instrumentalny GPU vs CPU (`diff.json`).
- PR / gałąź: `f1/engine` (draft, baza `f0/szkielet`; po squash F0 – rebase na `main`).
- Otwarte / P2 odłożone: potwierdzić format binarny z Python Programista Master; uruchomić `GpuVsCpuTest` na urządzeniach (API 26–32 i 33+).
- Następny krok: review Gauge → Critic.


## 2026-10-25 – Claude (alpha.2 → alpha.7: edytor, korekty, wiele zdjęć)
- Faza: poza fazami (rozwój aplikacji po testach na telefonie)
- Zrobione: grupy filmów (marka, rodzaj, ulubione), galeria na starcie, wiele zdjęć i zapis serii, podpis (4 kroje, wielkość, kolor), ramka `TightFrame`, korekty v3 (światło liniowe, Bradford, lokalne cienie i światła, ostrość, wyrazistość, HSL, tonowanie, blaknięcie, ziarno, winieta), kadr (obrót, prostowanie, proporcje), zoom podglądu, miniatury Biblioteki z wczytanego zdjęcia, ręczny Release z polem `tag`, ręczne nagrywanie zrzutów Roborazzi (`record-screenshots.yml`).
- Dokumenty: `docs/ALGORYTMY.md` (źródła i licencje algorytmów; kodu GPL/AGPL nie kopiujemy, aplikacja jest na MIT).
- PR / gałąź: #34–#53 (m.in. `f5/kolor-hsl`).
- Otwarte / P2 odłożone: dehaze i CLAHE (kandydat: kod MIT z `awxkee/aire` z notą), krzywe tonalne, przesuwanie kadru palcem, miniatury zdjęć w pasku serii, test wydajności korekt na 12 Mpx na urządzeniu.
- Następny krok: sprawdzić alpha.7 na telefonie (HSL, tonowanie, zoom, zapis serii).
