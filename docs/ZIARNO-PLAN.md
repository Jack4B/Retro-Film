# Plan: ziarno zgodne z kartami technicznymi filmów

Status: **wdrożone** (wydanie S, PR [#151](https://github.com/Jack4B/Retro-Film/pull/151), tag `v-test-2026-11-06-S`, 6 lis 2026, Europe/Warsaw).
Dokumentacja użytkownika i dewelopera: [ZIARNO.md](ZIARNO.md). Ten plik zostaje jako zapis audytu, kart i decyzji Q1–Q6 (faza 1: PR #150, commity weszły do `test` razem z #151).

Zakres wdrożenia (faza 2, po decyzjach Jack4B): wyłącznie ziarno. **Nie ruszać** LUT-ów, ekspozycji, krzywej, zoomu, ramki, GPU ani innych algorytmów.

Powiązane: [PRZEGLAD-ALGORYTMOW.md](PRZEGLAD-ALGORYTMOW.md) §3a H, [STAN-ALGORYTMOW.md](STAN-ALGORYTMOW.md) („kształty ziarna z profili filmów / RMS”).

---

## 1. Stan obecny (audyt `origin/test` @ c594cf2)

### 1.1 Pliki

| Plik | Rola |
|------|------|
| `app/src/main/kotlin/app/retrofilm/pipeline/FilmGrain.kt` | Model: tekstura, amplituda Selwyna, mnożnik w świetle liniowym, mieszanka luma/chroma |
| `app/src/main/kotlin/app/retrofilm/pipeline/Adjustments.kt` | Suwaki `grain` i `grainSize`, `grainCell`, wywołanie po wyostrzeniu (`effects` / `effectsPlanes`) |
| `app/src/main/kotlin/app/retrofilm/pipeline/Planes.kt` | `grainChroma[]` — udział barwy liczony **przed** tonowaniem |
| `app/src/main/kotlin/app/retrofilm/ui/FinishPanel.kt` | Suwaki „Ziarno” i „Rozmiar ziarna” (wykończenie, po filmie) |
| `app/src/test/kotlin/app/retrofilm/pipeline/FilmGrainPixelTest.kt` | `pixel()` ≡ `luma()`+`chroma()` bit w bit |
| `app/src/test/kotlin/app/retrofilm/pipeline/AdjustmentsTest.kt` | Selwyn (półtony najmocniej), podłoga w cieniach, `gain` średnia ≈ 1, `mix`/`colorfulness` dla B&W |

`FilmCalibration.kt` kalibruje **tylko** szarość 18% i ramię LUT pod auto-ekspozycję — **nie ma** parametrów ziarna.

W katalogu (`catalog-144.json`, 260 LUT-ów; 10 startowych w `catalog.json`) **nie ma** pól `rms`, `grain` ani profilu emulsji.

### 1.2 Model matematyczny (wszystkie filmy takie same)

Tekstura: biały szum Gaussa (suma 3 próbek równomiernych) na torusie 512², splot Gaussa (`FINE_SIGMA = 0,85`, promień 3), dwie oktawy (drobne ×0,9 + „kłaczki” ×0,436 w skali ×2,6). Deterministyczny hash (`AdjustmentProcessor.noise` + sole 11/29) — podgląd = zapis.

Amplituda (`FilmGrain.stops`):

`amount * 0,5 * (0,25 + 0,75 * sqrt(4 * p * (1−p)))`, `p` = jasność kodowana ograniczona do `[0,02; 0,98]`.

W półtonach obwiednia = 1, więc przy suwaku 1,0 odchylenie wynosi **0,5 EV** — to jest skala artystyczna, nie RMS z karty (dla Tri-X RMS 17 teoretyczne σ przy aperturze 48 µm to ≈ 0,056 EV; patrz §3).

Zastosowanie: mnożnik `2^(stops·g)` w świetle liniowym, rozwinięty do 2. rzędu i znormalizowany (`gain`), więc średnia jasność się nie przesuwa.

Barwa: `mix = 0,9165·luma + 0,4·chroma` (wariancja jednostkowa; udział barwny ≈ 16% mocy). Udział barwy zanika, gdy rozrzut kanałów piksela < 0,02…0,30 — B&W i odcień (Polaroid/sepia) dostają ziarno szare (1.1.12).

### 1.3 Skalowanie z rozdzielczością (podgląd vs zapis)

```
cell = max(0,7, max(w, h) / 1200 * (0,6 + grainSize * 2))
```

Przy domyślnym `grainSize = 0,5`: `cell = max(w,h) / 750`.
Dla kadru traktowanego jako 36 mm (szerokość klatki 35 mm) odpowiada to plamce **48 µm** — przypadkowo tej samej, której używają karty Kodak/Fuji do RMS. Struktura jest więc już w przybliżeniu niezależna od rozdzielczości (ziarno pokrywa ten sam ułamek kadru).

Bufory: podgląd 1280 (szybki 512, detal/ROI źródło do 2560), zapis = pełna rozdzielczość zdjęcia. `PixelSpan.totalCols/totalRows` to wymiary całego obrazu (kafel/ROI liczy komórkę względem pełnego kadru) — przy poprawnym `span` ziarno na wycinku 1:1 ma tę samą skalę filmową co eksport.

Amplituda **nie** zależy od rozdzielczości (brak prawa Selwyna „σ ∝ 1/√a” na piksel). Przy oglądaniu całego kadru podgląd ≈ zapis; przy pixel-peepingu eksportu widać tę samą strukturę, tylko drobniej spróbkowaną. To jest pożądane dla „tego samego wyglądu odbitki”.

### 1.4 Ustawienia per film — brak

- Domyślne `grain = 0` (ziarno **wyłączone**, dopóki użytkownik nie ruszy suwaka).
- `grainSize = 0,5` dla wszystkich.
- Wybór filmu zmienia wyłącznie LUT (+ kalibrację ekspozycji), nie ziarno.
- Warianty LUT (`+`/`−`, Cold/Warm, Alt) też nie różnicują ziarna.

### 1.5 Co zostawiamy (jakość już jest, nie przepisujemy)

Istniejący model kryształów (Selwyn `p(1−p)`, dwie oktawy Gaussa, korelacja kanałów, `gain` w świetle liniowym, determinizm) jest właściwą bazą. Model Boolean (Newson i in., 2017) pozostaje odłożony — koszt na 12 Mpx. Nie dokładamy skanów-nakładek (licencje, wzór „zamrożony”). Faza 2 **parametryzuje** ten model kartą filmu, nie wymienia algorytmu.

---

## 2. Metryki z kart technicznych

### 2.1 Diffuse RMS granularity × 1000

Kodak (B&W) i Fuji: odchylenie standardowe gęstości optycznej × 1000, apertura **48 µm**, zwykle przy gęstości netto 1,00 (Kodak B&W: „net diffuse density 1.00, 12×”; Fuji B&W/negatyw: „1.0 above minimum density”; slajdy Kodak: „gross diffuse visual density of 1.0”). Niższa liczba = drobniejsze ziarno.

Związek z EV (przy tej samej aperturze): `σ_D = RMS/1000`, `σ_EV = σ_D · log2(10) ≈ RMS · 0,003322`.
Przykład: Tri-X 17 → σ_EV,48µm ≈ 0,056 EV; T-Max 100 / E100: RMS 8 → ≈ 0,027 EV.

**Nie wolno porównywać surowego RMS między rodzinami.** Fuji na kartach Superia X-TRA 400 i PRO 400H pisze wprost: pomiar własny, warunki inne niż dla slajdu, **porównanie z filmem odwracalnym niemożliwe**. Superia 400 ma RMS 4, Velvia 50 ma RMS 9 — to nie znaczy, że Superia jest drobniejsza „na odbitce” w skali slajdu.

### 2.2 Print Grain Index (Kodak, negatyw kolorowy)

Od ok. połowy lat 2000 Kodak **zastąpił RMS przez PGI** na kartach Portra/Ektar/Gold/UltraMax. Cytat z E-4040/E-4050/E-4051/E-4046: indeks „replaces rms granularity and has a different scale which cannot be compared to rms granularity”. 25 ≈ próg widoczności ziarna z 14 cali; 4 jednostki ≈ JND dla 90% obserwatorów. W planie i w kodzie trzymamy PGI **osobno** i sprowadzamy do wewnętrznej skali `gEq` (robocza, nieoficjalna — §3.3).

### 2.3 Ilford / Harman

Aktualne karty HP5 Plus, FP4 Plus, Delta, Pan F, XP2 Super **nie podają RMS** (potwierdza to też niezależne zestawienie kart z 2026-10-21: [fernsphoto.com/films](https://fernsphoto.com/films/)). Ziarno opisują jakościowo i przez wybór wywoływacza. W tabeli: `źródło = szacunek-rówieśnik` (kalibracja do Kodak T-Max / Tri-X tej samej klasy technologicznej), nigdy jako „RMS z karty Ilford”.

---

## 3. Proponowany model (fizyczny, na obecnym `FilmGrain`)

### 3.1 Profil emulsji

Nowy czysty model (np. `core` / tabela JSON + `FilmGrainProfiles`), **klucz = bazowa emulsja**, nie każdy z 260 id LUT:

```
FilmGrainProfile(
  baseId,            // np. kodak_portra_400
  displayName,
  iso: Int,
  family: SILVER_BW | DYE_COLOR_NEG | DYE_SLIDE | CHROMOGENIC_BW | INSTANT,
  rmsOfficial: Int?,          // ×1000, apertura 48 µm — tylko gdy karta podaje
  pgi35: (p4x6, p8x10, p16x20)?,  // tylko Kodak CN
  gEq: Float,                 // wewnętrzna skala „jak RMS B&W/slajdu”
  grainUm: Float,             // charakterystyczny rozmiar na kliszy 35 mm
  chromaShare: Float,         // 0 dla srebra/chromogenicznego B&W; >0 dla barwnika
  source: OFFICIAL_RMS | OFFICIAL_PGI | ESTIMATED_PEER | ESTIMATED_ISO,
  sourceUrl: String,
  discontinued: Boolean,
  notes: String
)
```

Mapowanie 260 id → profil: obcięcie przyrostków LUT (`_m/_mm/_p/_pp/_ppp/_alt/_hc/_cool/_warm/_cold/_negative` oraz kombinacji, a także „Time Zero (Expired)”). Warianty `+`/`−` dziedziczą profil rodzica (osobna decyzja: czy push pogrubia ziarno — pytanie Q3).

10 filmów startowych APK i 250 z paczki używają **tej samej** tabeli.

### 3.2 Geometria (niezależność od rozdzielczości)

Klatka odniesienia: **36 × 24 mm** (35 mm). Dłuższy bok bufora = 36 mm.

`cellPx = (grainUm / 36_000) * max(szerokość, wysokość) * sizeMul`

gdzie `sizeMul = 0,6 + grainSize * 2` (jak dziś), a przy `grainSize = 0,5` → `sizeMul = 1,6` **albo** (prościej, Q4) `sizeMul = grainSize / 0,5`, żeby 0,5 = dokładnie `grainUm` z profilu.

Domyślne `grainUm ≈ 16 + 1,6·gEq` µm (T-Max 100 / RMS 8 → ~29 µm; Tri-X / 17 → ~43 µm; instant ~90–120 µm). Rodzina T-grain / Delta: dolna połowa zakresu; klasyczne srebro (Tri-X, HP5, APX): górna.

Dzisiejsze domyślne 48 µm (`max/750`) zostaje jako skala „pomiarowa RMS”, nie jako rozmiar wszystkich filmów.

### 3.3 Amplituda skalibrowana RMS / PGI

Zostawiamy obwiednię Selwyna i `gain`. Zmieniamy tylko skalę:

`stops = amount * σ_EV(gEq) * envelope(p)`

`σ_EV(gEq) = (gEq / 1000) * log2(10) * kAperture`

`kAperture`: jeśli filtr ziarna ma skuteczną aperturę ≈ `grainUm` (nie 48 µm), Selwyn daje `σ ∝ 1/√a`, więc `kAperture = 48 / grainUm`. Przy `grainUm = 48` dostajemy czyste `σ_EV = gEq · 0,003322`.

Suwak `amount`: **1,0 = zgodność z kartą** (σ z `gEq`), 0 = wyłączone, >1 artystyczne wzmocnienie (zakres suwaka zostaje 0…1 albo 0…2 — Q2). Obecne `STOPS_MAX = 0,5` przy amount=1 jest ~9× silniejsze niż Tri-X przy 48 µm; po kalibracji „pełny suwak” przestaje oznaczać 0,5 EV dla każdego filmu.

**`gEq` z PGI** (robocza kalibracja, żeby Portra weszła na skalę RMS slajdu/B&W; Kodak zabrania nazywać tego RMS):

`gEq ≈ 0,12 · PGI_8×10_z_35mm`

| Film (35 mm → 8×10) | PGI | gEq robocze |
|---------------------|-----|-------------|
| Ektar 100 | 38 | 4,6 |
| Portra 160 | 50 | 6,0 |
| Portra 400 | 59 | 7,1 |
| Portra 800 | 70 | 8,4 |

To odtwarza potoczne dawne rzędy „Portra 160 ≈ 6, Portra 400 ≈ 7, Portra 800 ≈ 8–9”, których **aktualne** karty już nie drukują jako RMS.

**`gEq` z Fuji CN (oficjalny RMS, inna skala):** przeliczenie `gEq = kFuji · rmsOfficial` z `kFuji ≈ 1,8`, tak by Superia X-TRA 400 (RMS 4) ≈ Portra 400 (gEq 7,1). Współczynnik do strojenia na syntetycznej szarej karcie w fazie 2; w tabeli podajemy oba (RMS z karty **oraz** `gEq`).

**Ilford i braki:** rówieśnik Kodak tej samej technologii (Delta ↔ T-Max, HP5 ↔ Tri-X, Pan F / FP4 ↔ T-Max 100 / konwencjonalne ISO 100–125).

### 3.4 Zależność od jasności i kanałów

- Selwyn `√(p(1−p))` zostaje (maksimum w półtonach). Podłoga w cieniach (`FLOOR = 0,25`) zostaje jako szum skanu; można ją lekko wiązać z rodziną (slajd: mniejsza podłoga, B&W srebro: obecna).
- **B&W srebro i chromogeniczny B&W (XP2, BW400CN):** `chromaShare = 0` (ziarno wyłącznie jasnościowe). Dzisiejsze wykrywanie szarości zostaje jako siatka bezpieczeństwa (odcień / sepia / Polaroid B&W).
- **Negatyw kolorowy:** chmury barwnika — obecny `CHROMA_SHARE = 0,4` jako punkt startu; ewentualnie lekko wyższy na szybkich emulsjach konsumenckich.
- **Slajd:** drobniejsze, bardziej skorelowane warstwy — start jak dziś lub nieco mniej chromy niż C-41.
- **Instant:** większe `grainUm`, wyższa chroma (oddzielne dyfuzje barwników).

Ziarno liczone **po** LUT-cie (wykończenie), na jasności już „filmowej” — bez zmian kolejności potoku.

### 3.5 Ziarno, jakość, architektura

- Deterministyczny seed zostaje (ten sam obraz + film + suwak = to samo ziarno).
- Priorytet jakości (zasada 1 z AGENTS.md): kalibracja do karty, nie „tańszy szum”.
- Warstwa domenowa bez Androida (profil + wzory `cell`/`stops`); `FilmGrain` tylko czyta profil. UI nadal dwa suwaki.
- Wybór filmu (Q1): proponowane zachowanie — zastosować profil (amount=1, size=0,5) przy zmianie filmu, jeśli użytkownik nie zablokował ziarna ręcznie; nie ruszać innych suwaków.

---

## 4. Tabela: emulsje → dane z kart → parametry

Kolumny: `rms` = oficjalny diffuse RMS ×1000 @ 48 µm (lub „—”); `PGI` = 4×6 / 8×10 / 16×20 z 35 mm; `gEq` = skala wewnętrzna; `src` = rodzaj źródła.

### 4.1 Start APK (10 filmów + identity)

Identity: bez ziarna (`gEq = 0`).

| Film | ISO | rms | PGI 35 mm | gEq | µm (propozycja) | Rodzina | src | Karta / URL | Uwagi |
|------|-----|-----|-----------|-----|-----------------|---------|-----|-------------|-------|
| Ilford HP5 Plus 400 | 400 | — | — | 16 | 42 | SILVER_BW | szacunek-rówieśnik (Tri-X 17) | [HP5 Plus fact sheet](https://www.ilfordphoto.com/amfile/file/download/file/1903/product/169/) (brak RMS) | Harman nie publikuje RMS |
| Kodak TRI-X 400 | 400 | **17** | — | 17 | 43 | SILVER_BW | oficjalny RMS | [F-4017](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4017_TriX.pdf) | net D=1,0; HC-110 B, 20 °C; „fine” |
| Fuji Velvia 50 | 50 | **9** | — | 9 | 30 | DYE_SLIDE | oficjalny RMS | [Fujifilm JP — filmy](https://www.fujifilm.com/jp/ja/consumer/films/negative-and-reversal) | „RMS粒状度: 9” |
| Kodak Ektachrome 100 VS | 100 | 11* | — | 11 | 34 | DYE_SLIDE | wtórne (zestawienie kart) | do potwierdzenia kartą E-100VS; rodzina E100 dziś: [E-4000](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4000_ektachrome_100.pdf) ma RMS **8** (inny stock: E100) | VS wycofany; nie mylić z E100 |
| Kodak Kodachrome 64 | 64 | **12** | — | 12 | 35 | DYE_SLIDE | oficjalny RMS (E-88, 1998) | publikacja Kodak E-88 (Select Series); proces K-14 zakończony 2010 | wcześniejsze wydania E-88: 10; 1998: KM 11 / KR 12 / KL 19 |
| Polaroid PX-680 | ~640 | — | — | 14 | 110 | INSTANT | szacunek-ISO | brak RMS w kartach Polaroid | duże chmury barwnika |
| Fuji 400H | 400 | **4** | — | 7,2 | 28 | DYE_COLOR_NEG | oficjalny RMS + skala Fuji CN | [datasheet PRO 400H](https://asset.fujifilm.com/www/jp/files/2024-04/198fe31ee57628013d29171770b28218/datasheet_pro400h_01.pdf) | `*` nie porównywać ze slajdem; wycofany |
| Kodak Portra 400 | 400 | — | **37 / 59 / 89** | 7,1 | 27 | DYE_COLOR_NEG | oficjalny PGI | [E-4050](https://imaging.kodakalaris.com/sites/default/files/files/products/e4050_portra_400.pdf) | „cannot be compared to rms” |
| Kodak Portra 800 | 800 | — | **48 / 70 / 99** | 8,4 | 29 | DYE_COLOR_NEG | oficjalny PGI | [E-4040](https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4040_portra_800.pdf) | |
| Fuji Superia 400 | 400 | **4** | — | 7,2 | 28 | DYE_COLOR_NEG | oficjalny RMS + skala Fuji CN | [SUPERIA X-TRA 400 AF3-0217E](https://asset.fujifilm.com/www/us/files/2025-06/8abba3dd9d004f44d1e9c7fdbdf5c520/films_superia-xtra400_datasheet_01.pdf) | `*` vs slajd; 48 µm, Dmin+1,0 |

### 4.2 Kodak — oficjalny RMS (B&W i slajd aktualny)

| Film | ISO | rms | src | Karta |
|------|-----|-----|-----|-------|
| T-Max 100 | 100 | **8** | oficjalny | [F-4016](https://business.kodakmoments.com/sites/default/files/files/products/tmax100f4016.pdf) (D-76, 20 °C; 48 µm, 12×) |
| T-Max 400 | 400 | **10** | oficjalny (aktualna karta) | [F-4043](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4043_TMax_400.pdf). Starsze F-4016 podawało **11** — bierzemy F-4043 |
| T-Max P3200 | EI 1000 / puszka 3200 | **18** | oficjalny | [F-4001](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4001_tmax_3200_0.pdf) |
| Tri-X 400 | 400 | **17** | oficjalny | F-4017 (wyżej); Tri-X 320 na tej samej karcie: 16 (brak w katalogu) |
| Ektachrome E100 | 100 | **8** | oficjalny | E-4000 (gross visual D=1,0, 48 µm) — stock *inny* niż E100VS w katalogu |

### 4.3 Kodak — oficjalny PGI (negatyw kolorowy)

| Film | ISO | PGI 4×6 / 8×10 / 16×20 (35 mm) | gEq | Karta | Status |
|------|-----|--------------------------------|-----|-------|--------|
| Ektar 100 | 100 | <25 / **38** / 66 | 4,6 | [E-4046](https://imaging.kodakalaris.com/sites/default/files/files/products/e4046_ektar_100.pdf) | aktualny |
| Portra 160 | 160 | 28 / **50** / 79 | 6,0 | [E-4051](https://imaging.kodakalaris.com/sites/default/files/files/products/e4051_portra_160.pdf) | aktualny |
| Portra 400 | 400 | 37 / **59** / 89 | 7,1 | E-4050 | aktualny |
| Portra 800 | 800 | 48 / **70** / 99 | 8,4 | E-4040 | aktualny |
| Portra 160 NC | 160 | 32 / **54** / 83 | 6,5 | [E-4040, 2009](https://125px.com/docs/film/kodak/e4040-Portra-2009.pdf) | wycofany (generacja NC/VC) |
| Portra 160 VC | 160 | 34 / **56** / 85 | 6,7 | jw. | wycofany |
| Portra 400 NC | 400 | 40 / **62** / 92 | 7,4 | jw. | wycofany |
| Portra 400 VC | 400 | 42 / **64** / 93 | 7,7 | jw. | wycofany |
| Portra 400 UC | 400 | — | 7,4 | brak osobnej pełnej tabeli w tej sesji; start jak 400 NC | wycofany |
| Gold 200 | 200 | 44 / 64 / — | 7,7 | Kodak E-7022 (zestawienie [fernsphoto](https://fernsphoto.com/films/) z karty) | nie ma w katalogu LUT; referencja skali |
| UltraMax 400 | 400 | 46 / — / — | ~8 | E-7023 (fernsphoto) | nie ma w katalogu |

Elite Color 200/400, Elite ExtraColor, Elite 100 XPRO: na aktualnych kartach zwykle PGI lub nic — w fazie 2 dociągnąć E-7xxx; do czasu weryfikacji `gEq` z ISO i sąsiedztwa (Elite Color 200 ≈ Gold 200, Elite Color 400 ≈ UltraMax).

### 4.4 Kodak slajd wycofany (RMS z dawnych publikacji)

| Film | ISO | rms | src | Uwagi |
|------|-----|-----|-----|-------|
| Kodachrome 25 | 25 | **11** | E-88 (1998); wcześniej 9 | wycofany |
| Kodachrome 64 | 64 | **12** | E-88 (1998); wcześniej 10 | wycofany |
| Kodachrome 200 | 200 | **19** | E-88 (1998); wcześniej 16 | wycofany |
| E-100 GX | 100 | **8** | rodzina E100G/GX (zestawienia kart, np. [cacreeks.com/films.htm](https://www.cacreeks.com/films.htm)) | wycofany; bliski dzisiejszemu E100 |
| Ektachrome 100 VS | 100 | **11** | zestawienie kart (cacreeks) | wycofany; * do potwierdzenia skanem karty |
| Elite Chrome 200 | 200 | **12** | zestawienie kart (cacreeks) | wycofany |
| Elite Chrome 400 | 400 | ~13–16 | do weryfikacji (E-27 / E-163) | wycofany |

### 4.5 Fuji — oficjalny RMS

| Film | ISO | rms | gEq | Karta | Uwagi |
|------|-----|-----|-----|-------|-------|
| Neopan Acros 100 | 100 | **7** | 7 | [NEOPAN 100 ACROS](https://asset.fujifilm.com/www/us/files/2020-04/299395cd078366c7a2956af612ca9fdb/NeopanAcros100.pdf) | Microfine, 48 µm, 12×, Dmin+1,0. Acros II: też 7, [AF3-0258E](https://asset.fujifilm.com/www/ca/files/2020-07/fb477bd9803b3c27ab592edcf9f3567c/AF3-0258E_PIB-NEOPAN-100-ACROSII-135-3_data-sheet.pdf) |
| Velvia 50 | 50 | **9** | 9 | Fujifilm JP (wyżej) | |
| Velvia 100 / generic | 100 | **8** | 8 | Fujifilm JP | |
| Provia 100F / generic | 100 | **8** | 8 | Fujifilm JP | |
| Astia 100F | 100 | **7–8** | 8 | broszura Fuji / zestawienia; produkt wycofany | Generic Astia 100: jak 100F |
| Sensia 100 | 100 | **8** | 8 | zestawienia kart (cacreeks: „new Sensia 100”) | wycofany |
| Provia 400X | 400 | **11** | 11 | broszura Fuji Professional (RMS na slajdach 400) | wycofany |
| Provia 400F | 400 | **13** | 13 | zestawienia kart (cacreeks) | wycofany |
| Superia Reala 100 | 100 | **4** | 7,2 | [AF3-967E](https://filmcolors.org/wp-content/uploads/2025/12/Fujicolor-Superia-Reala_AF3-967E.pdf) | Fuji CN; 48 µm, 12×, Dmin+1,0 |
| Superia X-TRA 400 | 400 | **4** | 7,2 | AF3-0217E (wyżej) | z adnotacją o braku porównywalności ze slajdem |
| Superia 1600 / HG 1600 | 1600 | **7** | 12,6 | [SUPERIA 1600](https://125px.com/docs/film/fuji/superia_1600_datasheet.pdf) | Fuji CN, kFuji·7; HG traktować jak 1600 |
| PRO 400H | 400 | **4** | 7,2 | datasheet JP (wyżej) | wycofany |
| Superia 100 / 200 / 800, X-Tra 800, 160C, 800Z | 100–800 | 3–5 typowo | kFuji·rms | do dociągnięcia konkretnym AF3 w fazie 2 | rodzina Superia/PRO: 160C/NPC często RMS 3; 800 ok. 5 (broszury Fuji CN) |
| Neopan 1600 | 1600 | — | 16 | karta do odnalezienia | szacunek-ISO (szybkie srebro); w katalogu też jako LUT „negative_old” |
| Superia 200 XPRO | 200 | jak Superia 200 | jak 200 | proces slajdowy na negatywie | ziarno emulsji Superia 200, nie slajdu |

### 4.6 Agfa (broszura Professional Films)

Źródło odczytane z historycznej broszury Agfa (m.in. kopia: [nijmegenpasfotofilm — APX 100/400](https://www.nijmegenpasfotofilm.nl/wp-content/uploads/pdf-product/film-agfa-apx100-and-apx400-datasheet.pdf)), pomiar RMS ×1000, apertura 48 µm:

| Film | ISO | rms | Status |
|------|-----|-----|--------|
| APX 100 | 100 | **9,0** | wycofany (emulsja); w katalogu |
| APX 400 (nie w katalogu) | 400 | **14,0** | referencja |
| APX 25 | 25 | **7*** | karta APX 25 w tej sesji nie pobrała się (HTTP 500); wartość z wtórnego archiwum kart — potwierdzić skanem |
| Precisa 100 | 100 | **10** | zestawienia slajdów (cacreeks / RSX II 100 w broszurze Agfa ok. RMS 10) |
| Ultra Color 100, Vista 200 | 100/200 | — | brak pewnego RMS; szacunek jak konsumencki CN ISO |

### 4.7 Ilford (brak RMS) — proponowane `gEq`

| Film | ISO | gEq | Rówieśnik | Karta (bez RMS) |
|------|-----|-----|-----------|-----------------|
| Pan F Plus 50 | 50 | 8 | T-Max 100 (drobniejsze konwencjonalne) | [PAN F PLUS](https://www.ilfordphoto.com/amfile/file/download/file/1908/product/681/) |
| Delta 100 | 100 | 8 | T-Max 100 (T-grain / Core-Shell) | fact sheet Delta 100 |
| FP4 Plus 125 | 125 | 10 | konwencjonalne ISO 125 | fact sheet FP4 |
| Delta 400 | 400 | 10 | T-Max 400 | fact sheet Delta 400 |
| HP5 Plus 400 | 400 | 16 | Tri-X 400 | fact sheet HP5 |
| XP2 Super | 400 | 11 | chromogeniczny, drobniejszy niż HP5 | fact sheet XP2; `chromaShare = 0` |
| Delta 3200 | EI 3200 (ISO ~1000) | 17 | T-Max P3200 | fact sheet Delta 3200 |

W katalogu są też LUT-y `negative_new/ilford_hp_5` (+/−) — ten sam profil co HP5 Plus.

### 4.8 Pozostałe (brak lub słabe dane)

| Film | Propozycja | src |
|------|------------|-----|
| Kodak BW 400 CN | gEq 11, CHROMOGENIC_BW, chroma 0 | szacunek (klasa XP2); wycofany |
| Kodak HIE | gEq 22–28, duże `grainUm` (~55 µm) | brak standardowego RMS na powszechnych kartach; IR, bardzo grube srebro |
| Rollei Ortho 25 | gEq 7 (jak APX 25) | karta Rollei bez RMS |
| Rollei Retro 80s | gEq 9 | szacunek-ISO |
| Rollei Retro 100 Tonal | gEq 10 | szacunek-ISO |
| Rollei IR 400 | gEq 16 | szacunek-ISO (IR) |
| Polaroid 664/667/672/665/669/690, PX-70, PX-100UV+, Time Zero, Polachrome | INSTANT, gEq 12–16, grainUm 90–130 | brak RMS; warianty Cold/Warm/+ dziedziczą bazę |
| Fuji FP-100c, FP-3000b | INSTANT (peel-apart); FP-3000b grubiej (ISO 3000) | brak RMS |
| Lomography Redscale 100 / X-Pro Slide 200 | gEq jak CN ISO 100 / slajd ISO 200 | brak kart RMS |
| Agfa Ultra Color 100 / Vista 200 | gEq jak CN ISO 100/200 (ok. 6–7) | brak pewnej karty |

Indeks publikacji Kodak: [Film Technical Publications](https://www.kodakprofessional.com/resource/film-technical-publications).

---

## 5. Plan testów (TDD — faza 2, przed kodem produkcyjnym)

Zgodnie z test-driven-development: **najpierw czerwony test, potem minimalna implementacja**. Bez prywatnych zdjęć w repo (tylko syntetyki: rampa, szara karta, klin).

### 5.1 Testy jednostkowe (JVM, bez emulatora)

1. **Kompletność katalogu** — każdy id z `catalog-144.json` i `catalog.json` mapuje się na profil; `identity` → brak ziarna.
2. **Monotoniczność w rodzinie** — `gEq(T-Max 100) < gEq(T-Max 400) < gEq(Tri-X 400) ≤ gEq(T-Max 3200)`; `gEq(Portra 160) < Portra 400 < Portra 800`; `gEq(Ektar) < Portra 160`.
3. **RMS → σ_EV** — dla profilu z `rmsOfficial = 17` i `grainUm = 48`, `stops(0,5, amount=1)` ≈ `17 · 0,003322` (±1%).
4. **Niezależność rozdzielczości** — `cell(4000)/cell(1280) = 4000/1280`; te same współrzędne znormalizowane do kadru dają spójną fazę tekstury (determinizm).
5. **Determinizm** — dwa wywołania `pixel(x,y,cell)` bit w bit (regresja `FilmGrainPixelTest`).
6. **B&W** — profil `SILVER_BW` / `CHROMOGENIC_BW`: szare wejście pozostaje R=G=B po ziarnie (rozszerzenie `AdjustmentsTest`).
7. **Kolor** — profil `DYE_COLOR_NEG` przy nasyconym pikselu: `colorfulness > 0` i udział chromy > 0.
8. **Warianty LUT** — `kodak_portra_400_pp` i `kodak_portra_400` → ten sam `baseId` (chyba że Q3 włączy boost pushu — wtedy test na +12%/stop).
9. **Suwak 0** — zero zmian pikseli (jak dziś).
10. **Regresja API** — istniejące testy `stops`/`gain`/`mix` pozostają zielone albo dostają świadomie nowe oczekiwane stałe (gdy zmieni się `STOPS_MAX`).

### 5.2 Testy instrumentowane / CI emulator

- Istniejący `SaveStressTest` (ziarno + wyostrzanie + winieta + ramka, 12 i 50 MP) musi przejść — ziarno nie może zwiększyć szczytu pamięci poza budżet pasów.
- Opcjonalnie: syntetyczna szara karta 4000×3000, pomiar RMS gęstości w oknie odpowiadającym 48 µm na klatce 36 mm i porównanie z `rmsOfficial` (± tolerancja filtra). To jest test **kalibracji**, nie wyglądu UI.
- Roborazzi: klin szarości z ziarnem dla 10 filmów startowych (syntetyk, bez zdjęć użytkownika). Nagrywać tylko po akceptacji wyglądu.

### 5.3 Poza zakresem testów fazy 2

Zmiana złotych LUT, testów ekspozycji, GPU, zoomu, ramki. Jeśli któryś test niespecyficzny dla ziarna padnie — to regresja poza zakresem, wracamy (systematic-debugging), nie „poprawiamy przy okazji” innych algorytmów.

---

## 6. Ryzyka

1. **Skale nieprzystawalne (RMS slajd vs RMS C-41 vs PGI).** Ślepe wstawienie liczby z karty da Superię 400 drobniejszą niż Velvię. Mitigacja: rodziny + `gEq` + adnotacje z kart Fuji.
2. **Kalibracja `kFuji` i `gEq(PGI)` jest robocza.** Kodak zabrania porównywać PGI z RMS; nasz wzór 0,12·PGI to mostek implementacyjny, nie cytat z E-58.
3. **Ilford bez liczb** — rówieśnicy Kodak to osąd technologiczny (Delta ≈ T-Max, HP5 ≈ Tri-X), nie pomiar Harmana.
4. **Zmiana głośności ziarna przy amount=1.** Dziś 0,5 EV w półtonach jest znacznie silniejsze niż karta. Użytkownicy z presetami `grain=0,5` zobaczą inne (prawdopodobnie słabsze, zależne od filmu) ziarno. Mitigacja: Q2 (czy 1,0 = karta, czy suwak zostaje „artystyczny” i karta tylko różnicuje filmy).
5. **Domyślne `grain=0`.** Sam profil nic nie da, jeśli ziarno zostanie wyłączone. Mitigacja: Q1.
6. **Wywołanie i skan.** Karty podają RMS dla wskazanego wywoływacza (D-76, Microfine, HC-110 B). Aplikacja imituje wygląd LUT-a + ziarno, nie konkretną chemię ani skaner.
7. **Wydajność.** Parametryzacja nie powinna dodać przebiegów; tylko inne stałe w istniejącym `FilmGrain`. Strzec budżetu `SaveStressTest`.
8. **Nazwy i znaki towarowe (D11).** W UI zostają prawdziwe nazwy filmów; w dokumentacji cytujemy karty jako źródło pomiaru, bez logo producentów.

---

## 7. Decyzje Jack4B (2026-11-06)

| | Pytanie | Decyzja |
|--|---------|---------|
| **Q1** | Czy wybór filmu sam włącza ziarno? | **Nie.** Ziarno zostaje domyślnie wyłączone (`grain = 0`). Film nie rusza suwaka — tylko dostarcza profil, gdy użytkownik sam włączy ziarno. |
| **Q2** | Co oznacza suwak 100%? | **Zgodność z kartą:** `amount * σ(gEq)` (obwiednia Selwyna bez zmian). |
| **Q3** | Warianty LUT `+` / `++`? | **Dziedziczą profil rodzica**, bez dodatkowego ziarna za push. |
| **Q4** | Suwak rozmiaru 50%? | **Nie było osobnej odpowiedzi** — obowiązuje domyślna z planu: 50% = `grainUm` z profilu (`sizeMul = grainSize / 0,5`). |
| **Q5** | Współczynnik Fuji CN? | **Zostaje 1,8** (bez strojenia oka Superia vs Portra). |
| **Q6** | Instant? | **Duże, miękkie chmury barwnika** (większe `grainUm`, więcej kłaczków, wyższa chroma na kolorowym packfilmie). |

---

## 8. Faza 2

**Zrobione** w PR #151 (`feat/ziarno-emulsje` → `test`): profile emulsji, amplituda z karty, dziedziczenie wariantów, testy syntetyczne. LUT, ekspozycja, krzywa, zoom, ramka i GPU bez zmian. Opis stanu: [ZIARNO.md](ZIARNO.md).
