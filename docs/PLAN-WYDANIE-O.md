# Wydanie O (2026-11-05) – ROI zoom + GPU bez wymuszania

## Crash (realme, po N)
`last_crash`: OutOfMemoryError ~75 MB w `Lowres.Accumulator` (GuidedFilter.kt:118) przez
`StripAnalysis.semanticMap` → StripPipeline → AdjustmentsApplier → ExportPipeline.look → MainState.renderPreview.

N łapał OOM w `refreshPreview` i wracał na 1280, ale **1280 z wyostrzaniem świadomym treści też alokuje duże akumulatory** (factor=1 przy TARGET=1024), a pełne 2560 przy zoomie było nadal pierwszą próbą. Sam catch nie dawał prawdziwego podglądu detalu.

## 1. ROI zoom (priorytet)
- Pinch ≥ 1,5×: zamiast całego 2560 liczony jest **widoczny wycinek** (+halo 48 px, max bok 1280) po kadrowaniu geometry.
- `ZoomMath.visiblePhotoRect` / `expand`; `previewRoiActive` – UI bez podwójnego graphicsLayer zoom.
- `Lowres.forSize`: twardy limit `MAX_CELLS = 640²` (~9 MB na akumulator) – nawet pełna klatka nie alokuje 75 MB.
- N-owski catch OOM zostaje jako siatka bezpieczeństwa.

## 2. GPU (bez włączania domyślnego)
- `gpuEnabled` nadal **false**.
- AGSL po `Invalid format specified 22`: `skipAgsl` na proces + DataStore `gpu_skip_agsl` (bez ponownego probing AGSL przy starcie).
- Bench (L) nadal: `gpu_ms × threads < cpu_ms`; na Mali GLES wygrywa pojedynczy pas, ale przegrywa z 8 wątkami CPU – stąd domyślnie OFF.
- GLES bez zmiany algorytmu LUT; całe-zdjęcie LUT na GPU odłożone (efekty sąsiedztwa i tak idą pasami).

## Testy
ZoomMath (ROI), LowresCap, PreviewRoi, PreviewDetailOom, SaveBackendAgslSkip + istniejące SaveBackend.

## Stan po R (aktualizacja)
- `composeZoomLayer` nie trzyma już hard identity przy `roiActive` – gesty live względem bazy ROI ([PLAN-WYDANIE-R.md](PLAN-WYDANIE-R.md)).
- `Lowres.MAX_CELLS` obniżone do **512²**; Accumulator reduce: `REDUCE_GROUPS_HEAVY=2`.
- `previewBase` zawsze 1280; crop ROI z lokalnego źródła do `PREVIEW_SIDE_DETAIL` (bez okna pełnego 2560 w cache).
- Z ramką nadal ROI off (P); double-tap one-landing (Q).
