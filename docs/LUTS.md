# LUT-y

## Źródło i licencja
- RawTherapee Film Simulation Collection – Pat David, Pavlov Dmitry, Michael Ezra.
- Źródło: https://rawpedia.rawtherapee.com/Film_Simulation (YahiaAngelo/Film-Luts je przepakowuje).
- Licencja: **CC BY-SA 4.0**. Nasze LUT-y 33³ to utwór zależny – także CC BY-SA 4.0. Katalog `luts/` jest wyłączony z MIT.
- Nazwy filmów tylko opisowe; brak powiązania z producentami, brak logo.
- Status (D9 – jedna jakość w aplikacji): w APK zestaw startowy w `core/engine/src/main/assets/luts/` – 10 filmów jako natywne HaldCLUT PNG 144³ (`format: png144`, bajty 1:1 z Jack4B/Retro-Film-LUTs@0432c5a, sha256 = wpisy `catalog-144.json`) + identity (33³ RGB8, patrz niżej); pełna paczka 260 filmów (D8/D9) – natywne HaldCLUT PNG: 259 filmów 144³ (poziom 12, 8 957 952 B po rozpakowaniu) i 1 film 256³ (`negative_color/kodak_ektar_100`, poziom 16, 50 331 648 B po rozpakowaniu), pobierane z tagu `luts-v1` repo Jack4B/Retro-Film-LUTs; katalog `app/src/main/assets/luts/catalog-144.json`. Format: docs/LUT-BIN.md (PR #5).

## Regeneracja
- Narzędzie `lutcheck.py` (Python Programista Master, osobny PR).
- Wejście: oryginalne HaldCLUT (144³; `kodak_ektar_100` natywnie 256³). Wyjście: `.cube` 33³.

## Dowód jakości
- Porównanie 33³ (z interpolacją jak w silniku) vs HaldCLUT 144³: **dE < 1**.
- `report.json` – wyniki dE (średnia, maks.) dla każdego LUT-a.
- Lista hashy (SHA-256) wszystkich plików w `luts/`, weryfikowana w CI.

## Zestaw startowy w APK (D9)
- Pliki: `core/engine/src/main/assets/luts/<kategoria>/<id>.png` + `catalog.json` (pola `format: png144`, `grid: 144`, `unpackedLength: 8957952`, `sha256`, `bytes`) + `SHA256SUMS`. Stare pliki `.rgb8` 33³ filmów usunięte.
- Ładowanie: `LutCatalog.open` → dla `png144`/`png-hald` ten sam `HaldClutDecoder` co pakiety pobierane (#15), wynik w wspólnym `LutRawCache` (LRU 180 MB, klucz `kategoria~nazwa` jak `PackEntry.localName`). Pierwsze użycie filmu dekoduje PNG (~9 MB RGB8), kolejne czytają surowy plik z cache.
- Dekoder obsługuje PNG RGB, RGBA oraz szarość (typ 0/4) – 66 z 260 plików pakietu (filmy cz-b, m.in. startowe Ilford HP5 i Tri-X) to PNG w skali szarości, rozwijane do R=G=B.
- Identity: zostaje `identity.rgb8` 33³. Aplikacja nie stosuje LUT-a dla identity (`MainState.IDENTITY_ID` → brak LUT), a interpolacja trójliniowa na siatce tożsamościowej jest dokładna przy każdym rozmiarze siatki – identity 144³ (Hald 1728×1728) zwiększyłby APK bez żadnej zmiany wyniku.
- Golden: `tools/golden/make_golden.py` czyta PNG Hald (i rgb8 dla identity), `GoldenLutTest` dekoduje przez `LutCatalog.open`, tolerancja ≤ 1 poziom; oczekiwane obrazy wygenerowane z 144³ (różnią się od starych 33³ o max 1–4 poziomy).
