# Wydanie Q (2026-11-05) – stabilne lądowanie po dwukrotnym dotknięciu

## Status P
P (v-test-2026-11-05-P) już naprawia zoom z ramką: ROI wyłączone przy `frame.enabled`, Compose skaluje całą kompozycję zdjęcie+ramka.

## Zgłoszenie Q
Po dwukrotnym dotknięciu zoom skacze do jednej wartości, a po chwili do drugiej.

## Przyczyna
1. `toggle` → scale 2,5× (Compose od razu powiększa pełny podgląd).
2. Po ~16 ms + render: bez ramki ROI ustawia `previewRoiActive` i `composeZoomLayer` → identity; albo z ramką `previewDetail` przełącza 1280→2560 (inna bitmapa przy tym samym scale).
3. Drugi skok wizualny.

## Naprawa
- `ZoomState.detailMode` + `settleForRoiBase()`: po ROI scale/offset → 1/0, ale `isDetailed` zostaje (bez wyłączania ROI).
- `LaunchedEffect(previewRoiActive)` woła settle; viewport nie jest odświeżany po settle (ten sam wycinek).
- Z ramką `previewBase` zostaje na 1280 nawet w detail – jedyny zoom to Compose (bez skoku rozdzielczości).

## Testy
`DoubleTapZoomTest`, rozszerzony `ZoomStateTest`; regresje P (`PreviewRoiTest`).
