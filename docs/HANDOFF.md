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

## 2026-10-25 – Claude
- Faza: redesign UI/UX, Edytor krok 1 (wg docs/OCENA-I-REDESIGN.md, PR #26)
- Zrobione: `app` – Start (jedno CTA), Edytor z zakładkami Film / Ramka, przytrzymanie zdjęcia = oryginał, moc filmu 0–100%, kategorie z id katalogu, zapis jednym dotknięciem; `ExportPipeline.render` przyjmuje `intensity` (domyślnie 1f). Usunięty `GalleryScreen` z `feature:start` (decyzja Jack4B: zostaje Photo Picker z wyborem wielu zdjęć) razem z testem i zrzutami.
- PR / gałąź: `f5/edytor-krok-1`.
- Otwarte / P2 odłożone: seria (pasek miniatur, jeden film i jedna ramka na serię, podpis per zdjęcie), zakładka Korekty, miniatury filmów w APK, Biblioteka filmów, wielokrotny wybór w pickerze – kolejne PR-y.
- Następny krok: CI → review Gauge → Critic.

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

