# Ziarno filmowe (wydanie S)

Dokumentacja dla użytkownika i dewelopera. Stan na `v-test-2026-11-06-S` (PR [#151](https://github.com/Jack4B/Retro-Film/pull/151), 6 lis 2026, Europe/Warsaw).

Plan badawczy i decyzje Q1–Q6: [ZIARNO-PLAN.md](ZIARNO-PLAN.md) (**wdrożone**). Kod: `FilmGrain.kt`, `FilmGrainProfiles.kt`. Suwaki są w zakładce **Wykończenie → Klisza**.

---

## Dla użytkownika

Dwa suwaki: **Ziarno** (siła) i **Rozmiar ziarna**.

| Suwak | Domyślnie | Znaczenie |
|-------|-----------|-----------|
| Ziarno | **0 (wyłączone)** | 0 = brak ziarna. **100% = amplituda z karty technicznej** wybranego filmu (`amount · σ(gEq)` i obwiednia Selwyna: najmocniej w półtonach). Powyżej 100% (jeśli suwak na to pozwala) to wzmocnienie artystyczne. |
| Rozmiar ziarna | **50%** | **50% = charakterystyczny rozmiar `grainUm` z profilu emulsji** na klatce 35 mm (36 mm dłuższego boku). Mniej = drobniejsze plamki, więcej = grubsze. |

**Wybór filmu nie włącza ziarna** (decyzja Q1). Film tylko dostarcza profil: gdy sam włączysz suwak, Tri-X jest ziarnistszy niż T-Max 100 albo Ektar, zgodnie z kartą — ale przy suwaku 0 nic nie widać.

Warianty LUT (`+` / `++` / `−`, Cold, Warm, Alt, HC, expired, early, …) **dziedziczą ziarno rodzica** — bez dopłaty za push (Q3).

- **Filmy czarno-białe** (srebro i chromogeniczne: XP2, BW400CN): ziarno wyłącznie jasnościowe, bez kolorowych kropeczek.
- **Instant** (Polaroid, FP-100c, FP-3000b): duże, miękkie chmury barwnika. Packfilmy B&W (664/665/667/672, FP-3000b) też bez chromy.
- **Bez filmu / Identity:** nawet przy włączonym suwaku nie ma ziarna (`gEq = 0`).
- Presety zapisują suwaki, nie osobny „numer karty”; przy podglądzie i zapisie aplikacja dobiera profil z aktualnie wybranego filmu.

Ziarno liczy się **po** LUT-cie (wykończenie), deterministycznie: ten sam obraz + film + suwaki = to samo ziarno na podglądzie i w zapisie.

---

## Model (jak to działa)

Istniejący model kryształów z `FilmGrain` zostaje. Wydanie S **parametryzuje** go kartą filmu, nie wymienia algorytmu. Model Boolean (Newson i in., 2017) jest odłożony — za drogi na 12 Mpx w telefonie.

1. **Tekstura.** Biały szum Gaussa na torusie 512², splot Gaussa, dwie oktawy (drobne ziarno + rzadsze kłaczki). Instant ma silniejsze i większe kłaczki (`clumpScale = 4`, wagi ~0,71 / 0,70 zamiast 0,90 / 0,44 i skali 2,6).
2. **Obwiednia Selwyna.** Amplituda rośnie jak `√(p(1−p))` (maksimum w półtonach), z podłogą w cieniach (szum skanu).
3. **Skala z karty.** Przy suwaku 100% odchylenie w półtonach to

   `σ_EV = (gEq / 1000) · log₂(10) · (48 / grainUm)`

   potem `stops = amount · σ_EV · obwiednia`. Dla Tri-X (`gEq = 17`) przy aperturze 48 µm to ≈ **0,056 EV** — wcześniej każdy film dostawał artystyczne 0,5 EV (~9× mocniej niż karta Tri-X).
4. **Zastosowanie.** Mnożnik `2^(stops · g)` w świetle liniowym, znormalizowany (`gain`), więc średnia jasność się nie przesuwa.
5. **Barwa.** Negatyw kolorowy: `chromaShare = 0,4` (~16% mocy w niezależnym szumie RGB). Slajd: 0,35. Instant kolor: 0,55. B&W / chromogeniczny B&W / packfilm B&W: **0**. Dodatkowo udział barwy zanika na szarych pikselach i lekkim odcieniu filmu (`colorfulness`, 1.1.12).
6. **Rozmiar komórki** (klatka odniesienia 36 × 24 mm):

   `cellPx = (grainUm / 36_000) · max(szerokość, wysokość) · (grainSize / 0,5)`

   Domyślne `grainUm ≈ 16 + 1,6 · gEq` µm (T-Max 100 ≈ 29 µm, Tri-X ≈ 43 µm). Instant i HIE mają własne `grainUm` (ok. 100–125 µm / 55 µm).

`grainStockId` jest ustawiane przy budowie looku podglądu i zapisu; `isNeutral` go ignoruje, więc sam wybór filmu nie wymusza przebiegu wykończenia.

---

## Skale z kart (nie wolno mieszać surowych liczb)

| Skala | Kto publikuje | Co oznacza | Mostek w aplikacji |
|-------|---------------|------------|--------------------|
| Diffuse RMS × 1000 @ 48 µm | Kodak B&W i slajd, Fuji B&W/slajd, Agfa (broszury) | Odchylenie std. gęstości optycznej, zwykle przy D ≈ 1,0 | `gEq = RMS` |
| Print Grain Index (PGI) | Nowoczesny Kodak C-41 (Portra, Ektar, …) | Indeks odbitek 4×6 / 8×10 / 16×20 z 35 mm; **nieporównywalny z RMS** (cytat z kart E-4040/E-4050/…) | `gEq ≈ 0,12 · PGI_8×10` (mostek roboczy, nie cytat z E-58) |
| RMS Fuji C-41 | Superia, Reala, PRO 400H | Oficjalny RMS, ale Fuji pisze wprost: **nie porównywać ze slajdem** | `gEq = 1,8 · RMS` (decyzja Q5), np. Superia 400 RMS 4 → `gEq` 7,2 ≈ Portra 400 |
| Ilford / Harman | HP5, Delta, FP4, Pan F, XP2 | Karty **nie podają RMS** | Szacunek-rówieśnik Kodak tej samej technologii (Delta ↔ T-Max, HP5 ↔ Tri-X) — nigdy jako „RMS z karty Ilford” |

Indeks publikacji Kodak: [Film Technical Publications](https://www.kodakprofessional.com/resource/film-technical-publications).

---

## Tabela profili (wszystkie bazy)

Klucz = ostatni człon id LUT bez przyrostków wariantu. Warianty dziedziczą ten wiersz. `µm` = `grainUm` (przy suwaku rozmiaru 50%). Alias = drugi klucz o tych samych liczbach.

### Czarno-białe srebro

| Id | ISO | gEq | µm | src | Karta / URL |
|----|-----|-----|----|-----|-------------|
| `agfa_apx_25` | 25 | 7 | 27 | wtórne (archiwum kart; skan HTTP 500) | potwierdzić skanem APX 25 |
| `agfa_apx_100` | 100 | 9 | 30 | oficjalny RMS (broszura) | [APX 100/400](https://www.nijmegenpasfotofilm.nl/wp-content/uploads/pdf-product/film-agfa-apx100-and-apx400-datasheet.pdf) (RMS 9,0 @ 48 µm) |
| `fuji_neopan_acros_100` | 100 | 7 | 27 | oficjalny RMS | [NEOPAN 100 ACROS](https://asset.fujifilm.com/www/us/files/2020-04/299395cd078366c7a2956af612ca9fdb/NeopanAcros100.pdf); Acros II też 7, [AF3-0258E](https://asset.fujifilm.com/www/ca/files/2020-07/fb477bd9803b3c27ab592edcf9f3567c/AF3-0258E_PIB-NEOPAN-100-ACROSII-135-3_data-sheet.pdf) |
| `fuji_neopan_1600` | 1600 | 16 | 42 | szacunek-ISO | karta RMS do odnalezienia |
| `ilford_pan_f_plus_50` | 50 | 8 | 29 | szacunek-rówieśnik (T-Max 100) | [PAN F PLUS](https://www.ilfordphoto.com/amfile/file/download/file/1908/product/681/) (brak RMS) |
| `ilford_delta_100` | 100 | 8 | 29 | szacunek-rówieśnik (T-Max 100) | fact sheet Delta 100 (Harman, brak RMS) |
| `ilford_fp_4_plus_125` | 125 | 10 | 32 | szacunek-rówieśnik (konwencjonalne ISO 125) | fact sheet FP4 Plus |
| `ilford_delta_400` | 400 | 10 | 32 | szacunek-rówieśnik (T-Max 400) | fact sheet Delta 400 |
| `ilford_hp_5_plus_400` | 400 | 16 | 42 | szacunek-rówieśnik (Tri-X 17) | [HP5 Plus](https://www.ilfordphoto.com/amfile/file/download/file/1903/product/169/) (brak RMS) |
| `ilford_hp_5` | 400 | 16 | 42 | alias HP5 Plus | ten sam profil (LUT `negative_new/ilford_hp_5`) |
| `ilford_delta_3200` | 3200 | 17 | 43 | szacunek-rówieśnik (T-Max P3200) | fact sheet Delta 3200 |
| `kodak_t_max_100` | 100 | 8 | 29 | oficjalny RMS **8** | [F-4016](https://business.kodakmoments.com/sites/default/files/files/products/tmax100f4016.pdf) |
| `kodak_t_max_400` | 400 | 10 | 32 | oficjalny RMS **10** (aktualna karta) | [F-4043](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4043_TMax_400.pdf); starsze F-4016 podawało 11 |
| `kodak_t_max_3200` | 3200 | 18 | 45 | oficjalny RMS **18** | [F-4001](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4001_tmax_3200_0.pdf) |
| `kodak_tmax_3200` | 3200 | 18 | 45 | alias `kodak_t_max_3200` | jw. |
| `kodak_tri_x_400` | 400 | 17 | 43 | oficjalny RMS **17** | [F-4017](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/f4017_TriX.pdf) (net D=1,0; 48 µm) |
| `kodak_hie_hs_infra` | 400 | 25 | **55** | szacunek (IR, grube srebro; `grainUm` własne) | brak standardowego RMS na powszechnych kartach |
| `rollei_ortho_25` | 25 | 7 | 27 | szacunek (jak APX 25) | karta Rollei bez RMS |
| `rollei_retro_80s` | 80 | 9 | 30 | szacunek-ISO | karta bez RMS |
| `rollei_retro_100_tonal` | 100 | 10 | 32 | szacunek-ISO | karta bez RMS |
| `rollei_ir_400` | 400 | 16 | 42 | szacunek-ISO (IR) | karta bez RMS |

### Chromogeniczny B&W (`chromaShare = 0`)

| Id | ISO | gEq | µm | src | Karta / URL |
|----|-----|-----|----|-----|-------------|
| `ilford_xp_2` | 400 | 11 | 34 | szacunek (drobniejszy niż HP5) | fact sheet XP2 Super (brak RMS) |
| `kodak_bw_400_cn` | 400 | 11 | 34 | szacunek (klasa XP2) | wycofany |

### Slajd

| Id | ISO | gEq | µm | src | Karta / URL |
|----|-----|-----|----|-----|-------------|
| `agfa_precisa_100` | 100 | 10 | 32 | broszura / zestawienia (RSX II ~10) | Agfa Professional; [cacreeks.com/films.htm](https://www.cacreeks.com/films.htm) |
| `fuji_astia_100f` | 100 | 8 | 29 | broszura Fuji / zestawienia | [Fujifilm JP — filmy](https://www.fujifilm.com/jp/ja/consumer/films/negative-and-reversal) |
| `fuji_astia_100_generic` | 100 | 8 | 29 | alias rodziny Astia 100 | jw. |
| `fuji_provia_100f` | 100 | 8 | 29 | oficjalny RMS **8** | Fujifilm JP |
| `fuji_provia_100_generic` | 100 | 8 | 29 | alias | jw. |
| `fuji_provia_400x` | 400 | 11 | 34 | broszura Fuji Professional | wycofany |
| `fuji_provia_400f` | 400 | 13 | 37 | zestawienia kart (cacreeks) | wycofany |
| `fuji_sensia_100` | 100 | 8 | 29 | zestawienia („new Sensia 100”) | wycofany |
| `fuji_velvia_50` | 50 | 9 | 30 | oficjalny RMS **9** | Fujifilm JP |
| `fuji_velvia_100_generic` | 100 | 8 | 29 | oficjalny RMS **8** | Fujifilm JP |
| `kodak_e_100_gx_ektachrome_100` | 100 | 8 | 29 | oficjalny RMS rodziny E100 | [E-4000](https://kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4000_ektachrome_100.pdf) (gross visual D=1,0) — *inny stock* niż E100VS |
| `kodak_ektachrome_100_vs` | 100 | 11 | 34 | wtórne (zestawienie kart) | wycofany; nie mylić z aktualnym E100 (RMS 8) |
| `kodak_ektachrome_100_vs_generic` | 100 | 11 | 34 | alias | jw. |
| `kodak_elite_chrome_200` | 200 | 12 | 35 | zestawienia (cacreeks) | wycofany |
| `kodak_elite_chrome_400` | 400 | 14 | 38 | szacunek / E-27, E-163 | wycofany |
| `kodak_elite_extracolor_100` | 100 | 11 | 34 | szacunek rodziny Elite Chrome | w katalogu jako slajd |
| `kodak_kodachrome_25` | 25 | 11 | 34 | oficjalny RMS E-88 (1998); wcześniej 9 | proces K-14 zakończony 2010 |
| `kodak_kodachrome_64` | 64 | 12 | 35 | oficjalny RMS E-88 (1998); wcześniej 10 | publikacja Kodak E-88 Select Series |
| `kodak_kodachrome_64_generic` | 64 | 12 | 35 | alias | jw. |
| `kodak_kodachrome_200` | 200 | 19 | 46 | oficjalny RMS E-88 (1998); wcześniej 16 | jw. |
| `lomography_x_pro_slide_200` | 200 | 12 | 35 | szacunek (slajd ISO 200) | brak karty RMS |

### Negatyw kolorowy — Fuji C-41 (`gEq = 1,8 · RMS`)

| Id | ISO | RMS karty | gEq | µm | src | Karta / URL |
|----|-----|-----------|-----|----|-----|-------------|
| `fuji_superia_reala_100` | 100 | **4** | 7,2 | 28 | oficjalny RMS + skala Fuji CN | [AF3-967E](https://filmcolors.org/wp-content/uploads/2025/12/Fujicolor-Superia-Reala_AF3-967E.pdf) |
| `fuji_superia_100` | 100 | 4* | 7,2 | 28 | rodzina Superia (typowe 3–5) | do dociągnięcia konkretnym AF3 |
| `fuji_superia_200` | 200 | 4* | 7,2 | 28 | rodzina Superia | jw. |
| `fuji_superia_200_xpro` | 200 | 4* | 7,2 | 28 | ziarno emulsji Superia 200 (nie slajdu), mimo procesu X-pro | jw. |
| `fuji_superia_400` | 400 | **4** | 7,2 | 28 | oficjalny RMS + skala Fuji CN | [SUPERIA X-TRA 400 AF3-0217E](https://asset.fujifilm.com/www/us/files/2025-06/8abba3dd9d004f44d1e9c7fdbdf5c520/films_superia-xtra400_datasheet_01.pdf) (`*` vs slajd) |
| `fuji_400h` | 400 | **4** | 7,2 | 28 | oficjalny RMS + skala Fuji CN | [PRO 400H](https://asset.fujifilm.com/www/jp/files/2024-04/198fe31ee57628013d29171770b28218/datasheet_pro400h_01.pdf) (wycofany) |
| `fuji_160c` | 160 | **3*** | 5,4 | 25 | broszury Fuji CN (NPC/160C często RMS 3) | do dociągnięcia AF3 |
| `fuji_superia_800` | 800 | **5*** | 9,0 | 30 | broszury Fuji CN (~5) | do dociągnięcia AF3 |
| `fuji_superia_x_tra_800` | 800 | 5* | 9,0 | 30 | jak Superia 800 | jw. |
| `fuji_800z` | 800 | 5* | 9,0 | 30 | rodzina PRO/Superia 800 | jw. |
| `fuji_superia_1600` | 1600 | **7** | 12,6 | 36 | oficjalny RMS + skala Fuji CN | [SUPERIA 1600](https://125px.com/docs/film/fuji/superia_1600_datasheet.pdf) |
| `fuji_superia_hg_1600` | 1600 | 7 | 12,6 | 36 | jak Superia 1600 | jw. |

### Negatyw kolorowy — Kodak PGI i sąsiedztwo

Mostek: `gEq ≈ 0,12 · PGI_8×10` z 35 mm.

| Id | ISO | PGI 4×6 / 8×10 / 16×20 | gEq | µm | src | Karta / URL |
|----|-----|------------------------|-----|----|-----|-------------|
| `kodak_ektar_100` | 100 | <25 / **38** / 66 | 4,6 | 23 | oficjalny PGI | [E-4046](https://imaging.kodakalaris.com/sites/default/files/files/products/e4046_ektar_100.pdf) |
| `kodak_portra_160` | 160 | 28 / **50** / 79 | 6,0 | 26 | oficjalny PGI | [E-4051](https://imaging.kodakalaris.com/sites/default/files/files/products/e4051_portra_160.pdf) |
| `kodak_portra_160_nc` | 160 | 32 / **54** / 83 | 6,5 | 26 | oficjalny PGI (2009) | [E-4040, 2009](https://125px.com/docs/film/kodak/e4040-Portra-2009.pdf) (wycofany NC/VC) |
| `kodak_portra_160_vc` | 160 | 34 / **56** / 85 | 6,7 | 27 | oficjalny PGI (2009) | jw. |
| `kodak_portra_400` | 400 | 37 / **59** / 89 | 7,1 | 27 | oficjalny PGI | [E-4050](https://imaging.kodakalaris.com/sites/default/files/files/products/e4050_portra_400.pdf) |
| `kodak_portra_400_nc` | 400 | 40 / **62** / 92 | 7,4 | 28 | oficjalny PGI (2009) | E-4040 2009 |
| `kodak_portra_400_uc` | 400 | — | 7,4 | 28 | start jak 400 NC | wycofany; brak pełnej tabeli PGI w sesji badawczej |
| `kodak_portra_400_vc` | 400 | 42 / **64** / 93 | 7,7 | 28 | oficjalny PGI (2009) | E-4040 2009 |
| `kodak_portra_800` | 800 | 48 / **70** / 99 | 8,4 | 29 | oficjalny PGI | [E-4040](https://www.kodakprofessional.com/sites/default/files/wysiwyg/pro/resources/e4040_portra_800.pdf) |
| `kodak_elite_color_200` | 200 | jak Gold 200 (44 / 64 / —) | 7,7 | 28 | sąsiedztwo Gold 200 / E-7022 | [fernsphoto.com/films](https://fernsphoto.com/films/) |
| `kodak_elite_color_400` | 400 | sąsiedztwo UltraMax | 8,0 | 29 | szacunek-ISO / E-7023 | fernsphoto |
| `kodak_elite_100_xpro` | 100 | — | 6,0 | 26 | szacunek jak Portra 160 | proces X-pro na negatywie |
| `agfa_ultra_color_100` | 100 | — | 6,0 | 26 | szacunek konsumencki CN ISO 100 | brak pewnej karty RMS/PGI |
| `agfa_vista_200` | 200 | — | 6,5 | 26 | szacunek konsumencki CN ISO 200 | brak pewnej karty |
| `lomography_redscale_100` | 100 | — | 6,0 | 26 | szacunek CN ISO 100 | brak karty RMS |

### Instant (duże miękkie chmury; `grainUm` własne)

| Id | ISO | gEq | µm | chroma | src |
|----|-----|-----|----|--------|-----|
| `fuji_fp_100c` | 100 | 12 | 100 | 0,55 | szacunek-ISO; brak RMS w kartach peel-apart |
| `fuji_fp_3000b` | 3000 | 16 | 125 | 0 | B&W packfilm, grubsze (ISO 3000) |
| `polaroid_px_100uv` | 100 | 12 | 100 | 0,55 | szacunek-ISO |
| `polaroid_px_70` | 150 | 13 | 105 | 0,55 | szacunek-ISO |
| `polaroid_px_680` | 640 | 14 | 110 | 0,55 | szacunek-ISO (film startowy APK) |
| `polaroid_time_zero` | 150 | 14 | 110 | 0,55 | szacunek; wariant `expired` dziedziczy bazę |
| `polaroid_669` | 80 | 13 | 105 | 0,55 | szacunek-ISO |
| `polaroid_690` | 100 | 13 | 105 | 0,55 | szacunek-ISO |
| `polaroid_polachrome` | 40 | 13 | 100 | 0,55 | slajd instant; szacunek |
| `polaroid_664` | 100 | 14 | 110 | 0 | packfilm B&W |
| `polaroid_667` | 3000 | 16 | 120 | 0 | packfilm B&W |
| `polaroid_672` | 400 | 14 | 110 | 0 | packfilm B&W |
| `polaroid_665` | 80 | 14 | 110 | 0 | packfilm B&W (negatyw + odbitka) |

`identity` oraz pusty id: `gEq = 0`, rodzina `NONE`. Nieznany przyszły id (poza katalogiem) spada na fallback jak Portra 400 (`gEq = 7,1`). Test kompletności pilnuje, że **wszystkie** id z `catalog-144.json` i starterów `catalog.json` trafiają w tabelę.

Przyrostki obcinane przy mapowaniu: `m` / `mm` / `mmm`, `p` / `pp` / `ppp`, `alt`, `hc`, `cool`, `warm`, `cold`, `negative`, `expired`, `early`.

---

## Uwagi i ograniczenia

- **`GRAIN_CELL_MIN = 0,7`.** Komórka nie spada poniżej 0,7 px. Na szybkim podglądzie **512 px** bardzo drobne emulsje (Ektar, T-Max 100) mogą być nieco „za grube” względem zapisu. Porównanie 1280 vs 4000 zachowuje `cell ∝ dłuższy bok` (test niezależności rozdzielczości).
- RMS, PGI i RMS Fuji C-41 to **inne skale**. Superia 400 (RMS 4) nie jest „drobniejsza niż Velvia 50 (RMS 9)” na wspólnej osi — stąd mostek 1,8.
- Mostek PGI → `gEq` jest implementacyjny. Kodak zabrania nazywać PGI wartością RMS.
- Ilford: rówieśnicy Kodak to osąd technologiczny, nie pomiar Harmana.
- Karty podają RMS dla wskazanego wywoływacza (D-76, HC-110 B, Microfine, …). Aplikacja imituje wygląd LUT-a + ziarno, nie konkretną chemię ani skaner.
- Osobnego profilu „średni format 120” nie ma — geometria zawsze zakłada klatkę 35 mm (36 mm).
- Presety z dawnym `grain = 0,5` przy suwaku 100% zobaczą **słabsze**, zależne od filmu ziarno (kalibracja do karty zamiast stałych 0,5 EV).
- Roborazzi ziarna nie nagrywano (brak zmiany UI); kalibrację pokrywają syntetyki JVM.

---

## Testy (CI)

Jednostkowe (JVM, `./gradlew :app:testDebugUnitTest`), bez prywatnych zdjęć:

| Test | Co sprawdza |
|------|-------------|
| `FilmGrainProfilesTest` | kompletność katalogu, dziedziczenie wariantów, monotoniczność `gEq`, suwak 100% = σ z karty, RMS gęstości @ D=1 (±20%), komórka 4000/1280, instant vs Portra, `grainStockId` nie psuje `isNeutral`, Fuji ×1,8 |
| `FilmGrainPixelTest` | `pixel()` ≡ `luma()`+`chroma()` bit w bit (determinizm) |
| `AdjustmentsTest` | Selwyn, `gain` ≈ 1, `mix`/`colorfulness`, ziarno B&W bez dryfu R/B, szare/odcień |
| `AdjustmentsSplitTest` | `grainStockId` należy do grupy wykończenia |

Na emulatorze CI zostaje m.in. `SaveStressTest` (ziarno nie może rozsadzić budżetu pamięci pasów). Zob. [CI.md](CI.md).

---

## Pliki

| Plik | Rola |
|------|------|
| `pipeline/FilmGrain.kt` | tekstura, σ, komórka, `stops`, `mix` |
| `pipeline/FilmGrainProfiles.kt` | tabela baz + dziedziczenie wariantów |
| `pipeline/Adjustments.kt` | suwaki, `grainStockId`, `withGrainStock` |
| `ui/FinishPanel.kt` | suwaki „Ziarno” i „Rozmiar ziarna” |
| [PLAN-WYDANIE-S.md](PLAN-WYDANIE-S.md) | notatka wydania S |
| [CHANGELOG-TEST-WYDANIA.md](CHANGELOG-TEST-WYDANIA.md) | wpis testera |
