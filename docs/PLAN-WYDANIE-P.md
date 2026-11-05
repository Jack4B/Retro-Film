# Wydanie P (2026-11-05) – zoom z ramką = cała kompozycja

## Zgłoszenie
Release O, realme: podgląd z ramką „powiększa wewnątrz ramki” zamiast skalować zdjęcie+ramkę razem.

## Przyczyna
ROI z O (`previewDetail` + viewport) wycinał widoczny fragment **zdjęcia przed** `FrameRenderer.render`, a `previewRoiActive` wyłączał `graphicsLayer` zoom w Compose. Ramka była doklejana wokół wycinka – gesty zoomu zmieniały ROI (treść w oknie), nie skalowały gotowej kompozycji.

## Naprawa
ROI włącza się tylko gdy **`!frame.enabled`**. Z włączoną ramką: pełny podgląd (1280/2560) + Compose zoom na bitmapie już z ramką (jak przed O). Bez ramki zostaje ROI z O (ochrona przed OOM GuidedFilter). Limit `Lowres.MAX_CELLS` z O nadal chroni ścieżkę z ramką.

## Testy
`PreviewRoiTest`: z ramką `previewRoiActive=false`, wymiary = pełna kompozycja (marginesy), `composeZoomLayer` nakłada pinch na bitmapę; bez ramki ROI wraca. (+ regresje O, `composeZoomLayerIdentityOnlyWhenRoiActive`).
