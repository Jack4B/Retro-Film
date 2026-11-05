# Wydanie Q (2026-11-05) – stabilne lądowanie po dwukrotnym dotknięciu

## Status P
P już naprawia zoom z ramką (ROI off przy `frame.enabled`).

## Zgłoszenie
Double-tap: skok do jednej skali, po chwili do drugiej.

## Przyczyna
Compose najpierw 2,5× na pełnym podglądzie, potem ROI/`previewRoiActive` → identity (albo 1280→2560 z ramką).

## Naprawa
1. **Bez ramki:** `awaitingRoi` trzyma layer na identity aż ROI gotowe → jeden skok (pojawia się wycinek). Gesty po ROI nadal aktualizują viewport (scale zostaje >1).
2. **Z ramką:** `previewBase` zostaje na 1280 w detail; Compose 2,5× od razu, bez wymiany bitmapy.
3. `ZoomState.detailMode` + opcjonalne `settleForRoiBase` (testy / API).

## Testy
`DoubleTapZoomTest`, `ZoomStateTest`, regresje `PreviewRoiTest`.

## Stan po R
- One-landing (`awaitingRoi`) bez zmian.
- Po pojawieniu się ROI gesty nie są już „przyklejone”: relative scale/offset do bazy wycinka ([PLAN-WYDANIE-R.md](PLAN-WYDANIE-R.md)).
- `settleForRoiBase` nadal API testowe; produkcja trzyma bazę z viewportu przy budowie ROI.
