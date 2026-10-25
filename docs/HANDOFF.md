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

## 2026-10-25 – Claude (Korekty)
- Faza: redesign – zakładka Korekty
- Zrobione: `Adjustments` (ekspozycja ±2 EV, kontrast, nasycenie, temperatura) i `AdjustmentMatrix` (czysta macierz 4×5) + `AdjustmentsApplier` (ColorMatrix) wpięte w `ExportPipeline.render` po nałożeniu filmu; zakładka Korekty w Edytorze (cztery suwaki zatwierdzane po puszczeniu, „Wyzeruj”); testy `AdjustmentsTest`. Wartości neutralne omijają krok (wynik filmu i golden bez zmian).
- PR / gałąź: `f5/korekty` (baza: `f5/biblioteka-filmow`, #32).
- Otwarte / P2 odłożone: korekty liczone w kodowaniu sRGB (nie w świetle liniowym) – prosty model; ziarno; cienie/światła; kopiuj/wklej ustawienia; zrzuty z telefonu (390); golden dla korekt na GPU, jeśli silnik dostanie je kiedyś natywnie.
- Następny krok: review Lumen → Gauge → Critic.

## 2026-10-25 – Claude (Biblioteka filmów)
- Faza: D12 – Biblioteka filmów z miniaturami
- Zrobione: ekran „Filmy” (wyszukiwarka, kategorie z licznikami, ★ ulubione, „Na telefonie”, „Pobierz” wszystkie, siatka kafelków z gotowych JPG z APK, stany: na telefonie / ↓ rozmiar / pobieranie % / błąd; dotknięcie wybiera lub pobiera, przytrzymanie = ulubione, akcje TalkBack); dodatkowy widok „Na moim zdjęciu” z `LutMemoryCache` (nie zastępuje JPG); logika w `LibraryModel` z testami JVM; wejście z zakładki Film (ikona lupy); miniatury JPG (260 plików) pochodzą z #31 (`app/src/main/assets/thumbs/`) – ten PR ich nie zawiera; do czasu scalenia #31 kafelki mają puste tło z nazwą.
- PR / gałąź: `f5/biblioteka-filmow` (baza: `f5/pobieranie-filmow`, #30).
- Otwarte / P2 odłożone: scalić #31 (komplet 260 miniatur od Forge) przed lub razem z tym PR; zrzuty z telefonu (390); test pobierania na urządzeniu.
- Następny krok: review Lumen → Gauge → Critic.

## 2026-10-25 – Claude (włączenie pobierania filmów)
- Faza: D8/D9 – pobieranie filmów
- Zrobione: `LutDownloadFeature.ENABLED = true`; `EngineFilmEngine` widzi filmy pobrane (`DownloadedFilms`, implementuje `PackDownloadedFiles` – same pliki, bez WorkManagera); lista filmów w Edytorze odświeża się po pobraniu (`FilmLibrary`); testy `EngineFilmEngineDownloadedTest` i `PackDownloadedFilesTest`.
- PR / gałąź: `f5/pobieranie-filmow` (baza: `f5/edytor-krok-1`, #27).
- Otwarte / P2 odłożone: nie sprawdziłem, czy tag `luts-v1` istnieje w Jack4B/Retro-Film-LUTs (poza moim zakresem) – bez niego pobieranie się nie uda; miniatury w APK dla filmów do pobrania i Biblioteka filmów (D12); test pobierania na urządzeniu/emulatorze.
- Następny krok: @Forge: emulator.yml i test pobierania na telefonie; potwierdzić tag `luts-v1`.

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


## 2026-10-25 – Claude (alpha.2 → alpha.7: edytor, korekty, wiele zdjęć)
- Faza: poza fazami (rozwój aplikacji po testach na telefonie)
- Zrobione: grupy filmów (marka, rodzaj, ulubione), galeria na starcie, wiele zdjęć i zapis serii, podpis (4 kroje, wielkość, kolor), ramka `TightFrame`, korekty v3 (światło liniowe, Bradford, lokalne cienie i światła, ostrość, wyrazistość, HSL, tonowanie, blaknięcie, ziarno, winieta), kadr (obrót, prostowanie, proporcje), zoom podglądu, miniatury Biblioteki z wczytanego zdjęcia, ręczny Release z polem `tag`, ręczne nagrywanie zrzutów Roborazzi (`record-screenshots.yml`).
- Dokumenty: `docs/ALGORYTMY.md` (źródła i licencje algorytmów; kodu GPL/AGPL nie kopiujemy, aplikacja jest na MIT).
- PR / gałąź: #34–#53 (m.in. `f5/kolor-hsl`).
- Otwarte / P2 odłożone: dehaze i CLAHE (kandydat: kod MIT z `awxkee/aire` z notą), krzywe tonalne, przesuwanie kadru palcem, miniatury zdjęć w pasku serii, test wydajności korekt na 12 Mpx na urządzeniu.
- Następny krok: sprawdzić alpha.7 na telefonie (HSL, tonowanie, zoom, zapis serii).
