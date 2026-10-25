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
- Zrobione: `app` – Start (jedno CTA), Edytor z zakładkami Film / Ramka, przytrzymanie zdjęcia = oryginał, moc filmu 0–100%, kategorie z id katalogu, miniatury filmów na zdjęciu użytkownika (`FilmThumbnails`, z `LutMemoryCache`, bez osobnej pamięci podręcznej), ulubione (DataStore), zapis jednym dotknięciem; `ExportPipeline.render` przyjmuje `intensity` (domyślnie 1f). Usunięty `GalleryScreen` z `feature:start` (decyzja Jack4B: zostaje Photo Picker z wyborem wielu zdjęć) razem z testem i zrzutami.
- PR / gałąź: `f5/edytor-krok-1`.
- Otwarte / P2 odłożone: pomiar miniatur na emulatorze (emulator.yml), zrzuty z telefonu (390) do porównania z makietami, seria (pasek miniatur, jeden film i jedna ramka na serię, podpis per zdjęcie), Korekty, miniatury w APK dla filmów do pobrania, Biblioteka filmów, wielokrotny wybór w pickerze – kolejne PR-y.
- Następny krok: CI → review Gauge → Critic.

## 2026-10-25 – Claude (narzędzie miniatur filmów)
- Faza: D12 – miniatury filmów do pobrania
- Zrobione: `tools/thumbs/make_thumbs.py` (HaldCLUT PNG → JPG 160 px + `thumbs.json`, kontrola budżetu), opis w `tools/README.md`; sprawdzone na 10 filmach startowych (≈3 KB/szt. na scenie testowej).
- PR / gałąź: `f5/miniatury-jpg`.
- Otwarte / P2 odłożone: wygenerować komplet 260 miniatur ze zdjęciem CC0 (wymaga kopii Retro-Film-LUTs@luts-v1 i sieci; nie mam do tego dostępu), wrzucić do `app/src/main/assets/thumbs/`, a po stronie aplikacji pokazać je w Bibliotece filmów (osobny PR).
- Następny krok: @Forge / Jack4B: uruchomić narzędzie na pełnej paczce.

## 2026-10-25 – Claude (przegląd koncepcji, makiety v2)
- Faza: poza fazami (ocena + redesign UI/UX)
- Zrobione: `docs/OCENA-I-REDESIGN.md` (ocena koncepcji, uwagi T1–T12), `docs/makiety/index.html` (8 makiet v2, nazwy filmów prawdziwe), decyzje D11 i D12 w `DECYZJE.md`, zmiany w `AGENTS.md`, `docs/LUTS.md` i `docs/EKRANY.md` (zgodnie z odpowiedziami Jack4B).
- PR / gałąź: `ccr-0dd79b3e-ffwulp`.
- Otwarte / P2 odłożone: README i PLAN nadal opisują stan sprzed kodu (T2); EKRANY.md ma notę o v2, ale treść sprzed v2; informacja o braku powiązania z producentami w „Licencje i autorzy” (do zatwierdzenia); kod UI w #27 („Edytor krok 1”), potem seria, Korekty, miniatury, Biblioteka filmów.
- Następny krok: review Gauge → Critic.
- Dopisane: D13 (motyw jasny/ciemny) i makiety w obu motywach (przycisk „Motyw” w `docs/makiety/index.html`).

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

