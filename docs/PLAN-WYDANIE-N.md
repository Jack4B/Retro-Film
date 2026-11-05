# Wydanie N (2026-11-05) – crash przy pinch-zoom + wyostrzanie

## Zgłoszenie
realme RMX5085, release M: Przygotuj pod film → film → Efekty → Wyostrzenie → pinch-zoom na detale → crash.

## Przyczyna (z kodu, nie zgadywanie)
1. `ZoomState.isDetailed` (scale ≥ 1,5) → `MainState.updateDetail(true)` → `previewDetail`.
2. `previewBase` wtedy używa `PREVIEW_SIDE_DETAIL = 2560` zamiast 1280 (4× pikseli).
3. `LaunchedEffect(previewDetail)` odpala `refreshPreview()` → pełny potok (korekty z przygotowania, LUT, wyostrzanie świadome treści / SemanticSharpen z floatami i mapami).
4. Eksport już łapał `OutOfMemoryError` przez `CrashLog.record` i zwracał komunikat; **podgląd nie łapał OOM** – wyjątek zabijał proces.
5. Dodatkowo przy przełączeniu 1280→2560 `PreviewStages` trzymał stare etapy, więc chwilowo leżały w pamięci obie rozdzielczości.

Potok filmu/LUT/koloru **nie był** źródłem – tylko rozmiar podglądu + brak obsługi OOM w `refreshPreview`.

## Naprawa
- `refreshPreview` łapie `OutOfMemoryError`, loguje `CrashLog.record("preview"|"preview_fallback")`, wyłącza `previewDetail`, czyści cache i próbuje ponownie na 1280; gdy i to padnie – zostawia ostatni udany podgląd (bez crashu procesu).
- `updateDetail` czyści `previewCache` + `PreviewStages` przed zmianą rozdzielczości.
- `PreviewStages.clear()`.
- Testy: `PreviewDetailOomTest` (wstrzyknięty OOM przy detail+sharpen+prep; OOM na detail i fallback; clear etapów).

## Poza zakresem
- ROI/kafelki przy zoomie (kolejna optymalizacja; safety-net OOM wystarcza na crash).
- Zmiany algorytmów filmu/LUT/koloru (zostawione zgodnie ze zgłoszeniem).
