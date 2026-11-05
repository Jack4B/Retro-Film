# Changelog wydań testowych Retro-Film (`v-test-…`)

Prerelease’y na gałęzi `test` (nie `main`). APK: GitHub → Releases. Data w tagu = dzień publikacji (Europe/Warsaw).

| Tag | APK (asset) | Temat |
|-----|-------------|--------|
| [v-test-2026-11-04-D](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-D) | w release | Chip HDR, ustawienia, log czasów, kopia zapasowa |
| [v-test-2026-11-04-E](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-E) | w release | Etapy zapisu, backend w logu, szybszy P3 |
| [v-test-2026-11-04-F](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-F) | w release | Auto-korekta F |
| [v-test-2026-11-04-G](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-G) | w release | Jeden przebieg LUT+efekty, szybszy deband/ziarno |
| [v-test-2026-11-04-H](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-H) | w release | Budżet pamięci pasów, OOM przy zapisie, last_crash |
| [v-test-2026-11-04-I](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-I) | w release | Kafle 2D, postęp zapisu, halacja, HDR headroom |
| [v-test-2026-11-04-J](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-J) | RetroFilm-J… | Ramka 1:1, DNG headroom, budżet 128 MB |
| [v-test-2026-11-04-K](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-K) | RetroFilm-K-release.apk | Tryb ramki, GPU LUT (AGSL/GLES) eksperymentalnie |
| [v-test-2026-11-04-L](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-L) | RetroFilm-L-release.apk | GPU domyślnie OFF, bench CPU×wątki, cache probe |
| [v-test-2026-11-04-M](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-04-M) | RetroFilm-M-release.apk | Ekspozycja po zdjęciu krzywej, ochrona twarzy, suwak krzywej |
| [v-test-2026-11-05-N](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-05-N) | RetroFilm-N-release.apk | OOM catch przy zoom+wyostrzanie → fallback 1280 |
| [v-test-2026-11-05-O](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-05-O) | RetroFilm-O-release.apk | ROI zoom, Lowres MAX_CELLS, skip AGSL format 22 |
| [v-test-2026-11-05-P](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-05-P) | RetroFilm-P-release.apk | Z ramką: zoom Compose na zdjęciu+ramce (bez ROI) |
| [v-test-2026-11-05-Q](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-05-Q) | RetroFilm-Q-release.apk | Double-tap: jedno lądowanie (`awaitingRoi` / ramka 1280) |
| [v-test-2026-11-05-R](https://github.com/Jack4B/Retro-Film/releases/tag/v-test-2026-11-05-R) | RetroFilm-R-release.apk | Live ROI pan/zoom; twarze z pełnego kadru→ROI; peak Accumulator ↓; bez okna 2560 |

---

## D (2026-11-04)
Chip HDR w edytorze, domyślne HDR w Ustawieniach, lokalny log czasów (`RetroFilmPerf`), kopia zapasowa JSON. Offline.

## E
Log zapisu: prawdziwy backend (AGSL/GLES/CPU) + powód fallbacku; etapy `save_*`; szybsza konwersja Display P3.

## F
Auto-korekta: sceny, WB, ekspozycja, dokumentacja workflow.

## G
Zapis: jeden przebieg LUT+wykończenie; optymalizacje deband/ziarno/wyostrzanie; poprawka pełnego podglądu.

## H
Budżet pamięci pasów; OOM przy eksporcie → komunikat zamiast crashu; `CrashLog` / `last_crash` w CSV diagnostyki.

## I
Kafle 2D w StripPipeline (pełne wątki w budżecie); określony postęp zapisu; halacja; winieta cos⁴; zapas HDR z gain mapy; równoległy `auto_suggest`.

## J
Ramka: płótno rośnie, zdjęcie 1:1 (bez ciężkiego Lanczosa); analiza DNG F16 / BaselineExposure; budżet pasów max 128 MB. Pełne GPU LUT – jeszcze nie.

## K
Ustawienia: tryb ramki (na zewnątrz vs oryginalny rozmiar); zapis LUT na GPU (AGSL F16 + GLES) z próbką i awaryjnym CPU.

## L
GPU **domyślnie wyłączone** (na Mali ~6× wolniej niż CPU pasami). Probe cache na proces + powody (diff/tol/probe_ms); auto-bench `gpu×wątki` vs CPU; reuse zasobów GLES; `save_lut_gpu_split`.

## M
„Przygotuj pod film”: ekspozycja liczona **po** zdjęciu krzywej; limit jasności twarzy (FaceExposureCap); suwak „Zdejmij krzywą” działa też po ręcznej ekspozycji; 100% = pełne zdjęcie krzywej.

## N
Crash zoom+wyostrzanie: `refreshPreview` łapie OOM, loguje, wyłącza detail, wraca na 1280. (Nie dawało prawdziwego detalu – patrz O.)

## O
ROI: przy zoomie ≥1,5× (bez ramki) liczony widoczny wycinek (+halo). `Lowres.MAX_CELLS` chroni przed ~75 MB Accumulator. AGSL format 22 → `skipAgsl` + DataStore. GPU nadal OFF domyślnie.

## P
Z **włączoną ramką** ROI wyłączone – pinch skaluje bitmapę po `FrameRenderer` (zdjęcie+ramka razem). Bez ramki ROI z O zostaje.

## Q
Double-tap: bez ramki `awaitingRoi` nie pokazuje 2,5× na pełnym kadrze przed ROI (jeden skok gdy wycinek gotowy); z ramką detail zostaje na 1280 + Compose 2,5× (bez skoku 1280→2560). `ZoomState.detailMode`.

## R
Live gesty na bitmapie ROI (relative do bazy wycinka); twarze smart-sharpen z pełnego kadru mapowane do ROI; `REDUCE_GROUPS_HEAVY=2` + `MAX_CELLS=512²` tnie peak Accumulator; `previewBase` zawsze 1280 (ROI z lokalnego DETAIL).

Szczegóły techniczne: `docs/PLAN-WYDANIE-*.md` (I–R).
