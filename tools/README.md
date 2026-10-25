# LUT-y w Retro-Film

## Źródło i licencja
- RawTherapee Film Simulation Collection (2015-09-20), autorzy: Pat David, Pavlov Dmitry, Michael Ezra.
- Licencja: **CC BY-SA 4.0** — https://rawpedia.rawtherapee.com/Film_Simulation
- Pliki `.rgb8` w `core/engine/src/main/assets/luts/` to utwór zależny (konwersja HaldCLUT → 33³) i też są na CC BY-SA 4.0 (`luts/LICENSE`). Kod aplikacji pozostaje na MIT.
- Nazwy filmów są opisowe; brak powiązań z producentami, bez logo.

## Narzędzia (`tools/`, poza APK)
- `lutcheck.py <katalog> --out out --license CC-BY-SA-4.0` — walidacja (LUT_3D_SIZE, liczba wierszy, zakres 0..1, odrzuca 1D), normalizacja nazw i formatu, deduplikacja po zawartości, `catalog.json` + `report.json`.
- `hald2lut.py <HaldCLUT/> <Film-Luts/> --out core/engine/src/main/assets/luts --golden tools/golden` — konwersja HaldCLUT 144³ → 33³ (próbkowanie trilinearne, RGB8 surowe `.rgb8`, format: `docs/LUT-BIN.md` z PR #5), dopasowanie do katalogu Film-Luts po nazwie, dowód ΔE2000 na wszystkich 144³ punktach (`report.json`), `catalog.json`, `SHA256SUMS`, golden PNG 17³. Wymaga numpy + Pillow.
- `frame_ref.py <zdjęcie> [wyjście.png]` — render referencyjny ramki wg `docs/ramka-spec.md` (font Caveat, OFL — plik fontu dodawany osobno).

## Stan
Test na YahiaAngelo/Film-Luts: 296 plików, 0 błędów, 9 duplikatów, wszystkie 13³. Konwersja HaldCLUT → 33³: 260 LUT-ów w `core/engine/src/main/assets/luts/`, 27 bez źródła HaldCLUT; wyniki ΔE w `report.json`.
