# Wydanie P (2026-11-05) – zoom z ramką = cała kompozycja

## Zgłoszenie
Release O, realme: podgląd z ramką „powiększa wewnątrz ramki” zamiast skalować zdjęcie+ramkę razem.

## Przyczyna
ROI z O (`previewDetail` + viewport) wycinał widoczny fragment **zdjęcia przed** `FrameRenderer.render`, a `previewRoiActive` wyłączał `graphicsLayer` zoom w Compose. Ramka była doklejana wokół wycinka – gesty zoomu zmieniały ROI (treść w oknie), nie skalowały gotowej kompozycji.

## Naprawa
ROI włącza się tylko gdy **`!frame.enabled`**. Z włączoną ramką: pełny podgląd + Compose zoom na bitmapie już z ramką (jak przed O). Od Q/R `previewBase` przy detail **zostaje na 1280** (bez skoku 1280→2560). Bez ramki zostaje ROI z O/R. Limit `Lowres.MAX_CELLS` nadal chroni ścieżkę z ramką.

## Stan po R
Bez zmian reguły „ROI tylko bez ramki”. Live pan/pinch dotyczy ścieżki bez ramki ([PLAN-WYDANIE-R.md](PLAN-WYDANIE-R.md)).

## Testy
`PreviewRoiTest`: z ramką `previewRoiActive=false`, wymiary = pełna kompozycja (marginesy), `composeZoomLayer` nakłada pinch na bitmapę; bez ramki ROI wraca. (+ regresje O, `composeZoomLayerIdentityOnlyWhenRoiActive`).
