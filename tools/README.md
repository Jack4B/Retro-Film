# LUT-y w Retro-Film

## Źródło i licencja
- RawTherapee Film Simulation Collection (2015-09-20), autorzy: Pat David, Pavlov Dmitry, Michael Ezra.
- Licencja: **CC BY-SA 4.0** — https://rawpedia.rawtherapee.com/Film_Simulation
- Pliki `.rgb8` (zestaw startowy w `core/engine/src/main/assets/luts/` i paczki do pobrania) to utwór zależny (konwersja HaldCLUT → 33³) i też są na CC BY-SA 4.0 (`luts/LICENSE`). Kod aplikacji pozostaje na MIT.
- Nazwy filmów są opisowe; brak powiązań z producentami, bez logo.

## Narzędzia (`tools/`, poza APK)
- `lutcheck.py <katalog> --out out --license CC-BY-SA-4.0` — walidacja (LUT_3D_SIZE, liczba wierszy, zakres 0..1, odrzuca 1D), normalizacja nazw i formatu, deduplikacja po zawartości, `catalog.json` + `report.json`.
- `hald2lut.py <HaldCLUT/> <Film-Luts/> --out <katalog> [--size N] [--bits 8|16] [--only id1,id2] [--identity] [--golden tools/golden]` — konwersja HaldCLUT 144³ → N³ (domyślnie 33) (próbkowanie trilinearne, RGB8 surowe `.rgb8`, format: `docs/LUT-BIN.md` z PR #5), dopasowanie do katalogu Film-Luts po nazwie, dowód ΔE2000 na wszystkich 144³ punktach (`report.json`), `catalog.json`, `SHA256SUMS`, golden PNG 17³. Wymaga numpy + Pillow.
- `luteval.py <HaldCLUT/> <katalog pełnej paczki> --golden tools/golden` — ΔE2000 144³ dla 33³ (trójliniowa = metoda silnika, czworościenna dodatkowo) i 48³ (tylko statystyki), identity, kryterium D7, flaga `approx`, porównania w `tools/golden/compare/`.
- `frame_ref.py <zdjęcie> [wyjście.png]` — render referencyjny ramki wg `docs/ramka-spec.md` (font Caveat, OFL — plik fontu dodawany osobno).

## Stan
Test na YahiaAngelo/Film-Luts: 296 plików, 0 błędów, 9 duplikatów, wszystkie 13³. Konwersja HaldCLUT → 33³: 260 LUT-ów (katalog i raport: `tools/packs/`; w APK tylko zestaw startowy), 27 bez źródła HaldCLUT; wyniki ΔE w `report.json`.

## photocompare.py
Porównanie na prawdziwych zdjęciach (`tools/golden/photos/`, CC0, źródła w `SOURCES.md`): HaldCLUT 144³ | 33³ RGB8 trójliniowo | różnica×10 dla 5 najgorszych filmów, ΔE2000 (mean/p99/max, px ΔE>3 z regionami).
`python3 tools/photocompare.py <HaldCLUT> tools/packs/catalog-33-full.json tools/golden` → `tools/golden/compare/photos/`.

Zestaw startowy w APK (D8):
`python3 tools/hald2lut.py <HaldCLUT> <Film-Luts> --out core/engine/src/main/assets/luts --identity --only negative_new/kodak_portra_400,negative_new/fuji_400h,colorslide/fuji_velvia_50,colorslide/kodak_kodachrome_64,colorslide/kodak_ektachrome_100_vs,bw/kodak_tri_x_400,bw/ilford_hp_5_plus_400,instant_consumer/polaroid_px_680,negative_old/fuji_superia_400,negative_new/kodak_portra_800`
(potem pola `displayName`/`approx`/`bundled` jak w `tools/packs/catalog-33-full.json`).
