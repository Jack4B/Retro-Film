# Wydanie R (2026-11-05) – live ROI + twarze + peak pamięci

## Źródło
Code review po Q: 3 ustalenia (2 HIGH, 1 MEDIUM) + tanie zamknięcie okna 2560 przed ROI.

## Przyczyny
1. **Sticky pan/pinch po ROI:** `composeZoomLayer` wymuszał identity gdy `roiActive` – gesty nie ruszały bitmapy aż do kolejnego rebuildu.
2. **Puste/złe twarze na zoomie:** `facesFor` wołał `FaceFinder` na wycinku ROI (albo cache z pełnego kadru bez mapowania współrzędnych).
3. **Peak ~78 MB:** `Parallel.reduce` × `REDUCE_GROUPS=8` × `Lowres.Accumulator` (~9,8 MB przy 640²) przy `semanticMap`.
4. **Okno 2560:** `previewBase` przy detail bez ramki budował pełne 2560 w cache zanim ROI uciął wycinek.

## Naprawy
1. Live relative zoom: `composeZoomLayer(..., roiBaseScale/Offset*)` – warstwa = `(scale/base, offset-baseOffset)`; `awaitingRoi` i zoom z ramką bez zmian (Q one-landing + P framed).
2. Twarze zawsze z pełnego kadru (cache `photo+geometry`); przy ROI `FaceBox.mapIntoRoi(PixelRect, fullW, fullH)`.
3. `Parallel.REDUCE_GROUPS_HEAVY=2` na ścieżkach Accumulator; `Lowres.MAX_CELLS=512²` → peak ≲ 13 MB.
4. `previewBase` zawsze 1280; ROI bierze lokalne źródło do `PREVIEW_SIDE_DETAIL` bez wpisu do cache.

## Testy
`RoiLiveZoomTest`, `FaceBoxRoiTest`, `RoiFacesMappingTest`, `ParallelReduceMemoryTest`, regresje `PreviewRoiTest` / `DoubleTapZoomTest` / `LowresCapTest`.

## Poza zakresem
Algorytmy filmu/LUT nietknięte. `settleForRoiBase` zostaje API testowym (produkcja używa relative base z viewportu przy budowie ROI).

## Dokumentacja
[USTAWIENIA-I-ZOOM.md](USTAWIENIA-I-ZOOM.md) · [CHANGELOG-TEST-WYDANIA.md](CHANGELOG-TEST-WYDANIA.md)
