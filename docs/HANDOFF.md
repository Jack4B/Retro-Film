# Handoff – dziennik

Nowe wpisy na górze. Szablon:

```
## RRRR-MM-DD – <autor/rola>
- Faza:
- Zrobione:
- PR / gałąź:
- Otwarte / P2 odłożone:
- Następny krok:
```

## 2026-10-25 – Forge/Lumen (redesign, krok 1)
- Faza: redesign UI/UX wg docs/OCENA-I-REDESIGN.md
- Zrobione: `app` – Start (jedno CTA), Edytor z zakładkami Film / Ramka, przytrzymanie zdjęcia = oryginał, moc filmu (suwak 0–100%, zatwierdzana po puszczeniu), kategorie filmów z id katalogu, zapis jednym dotknięciem; `ExportPipeline.render` przyjmuje `intensity`. Teksty w `strings.xml`.
- PR / gałąź: #26 (`ccr-0dd79b3e-ffwulp`).
- Otwarte / P2 odłożone: kod nie był budowany lokalnie (brak Android SDK i dostępu do Google Maven w środowisku) – weryfikuje CI; zakładka Korekty, seria (pasek miniatur, WorkManager), miniatury filmów, Biblioteka filmów, arkusz Zapisz, PNG – czekają na odpowiedzi Jack4B (pytania 1–5 w ocenie); `GalleryScreen` i osobne ekrany Edytor/Ramka/Eksport z feature:* zostają nieużywane do decyzji; zrzuty Roborazzi nie zmienione (nie ruszano feature:*).
- Następny krok: CI zielone → review Gauge → Critic.

## 2026-10-25 – Lumen (przegląd koncepcji)
- Faza: poza fazami (ocena + redesign UI/UX)
- Zrobione: `docs/OCENA-I-REDESIGN.md` (ocena koncepcji, uwagi T1–T12, pytania do Jack4B) i `docs/makiety/index.html` (8 makiet v2).
- PR / gałąź: `ccr-0dd79b3e-ffwulp`.
- Otwarte / P2 odłożone: T1 nazwy producentów w katalogu (P0, ESKALACJA); rozjazd README/PLAN/EKRANY z kodem – poprawić po decyzjach Jack4B.
- Następny krok: decyzje Jack4B (pytania 1–5 w ocenie), potem review Gauge → Critic.

## 2026-10-25 – Forge
- Faza: F3 (spięcie aplikacji)
- Zrobione: `MainActivity` – ścieżka testowa: Photo Picker → film (identity + katalog `assets/luts`) → ramka + podpis (`SignatureFitCheck`) → eksport JPEG (`JpegExporter`) do MediaStore `Pictures/RetroFilm` (API 26–28: SAF). Orientacja EXIF na pikselach (`ExifOrientation`, testy JVM), ICC zachowany (P3 → ramka → JPEG), trasa `settings` (placeholder).
- PR / gałąź: `f3/app-wiring`.
- Otwarte / P2 odłożone: 10 filmów startowych pojawi się po merge #11 (`luts/33`); LUT na zdjęciu P3 liczony w sRGB (silnik) – wynik wraca do P3 przez kanwę.
- Następny krok: review Gauge → Critic; podpiąć ekran Ustawień Lumena w `RetroFilmNavHost`.

## 2026-10-25 – Forge
- Faza: F1 (silnik LUT)
- Zrobione: `core:engine` – `LutEngine`, `Lut` (format binarny RGB8 33³, docs/LUT-BIN.md), backendy AGSL / GLES 3.0 (tekstura 3D) / CPU, fabryka z fallbackiem do CPU, testy JVM CPU, test instrumentalny GPU vs CPU (`diff.json`).
- PR / gałąź: `f1/engine` (draft, baza `f0/szkielet`; po squash F0 – rebase na `main`).
- Otwarte / P2 odłożone: potwierdzić format binarny z Python Programista Master; uruchomić `GpuVsCpuTest` na urządzeniach (API 26–32 i 33+).
- Następny krok: review Gauge → Critic.

