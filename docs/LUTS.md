# LUT-y

## Źródło i licencja
- RawTherapee Film Simulation Collection – Pat David, Pavlov Dmitry, Michael Ezra.
- Źródło: https://rawpedia.rawtherapee.com/Film_Simulation (YahiaAngelo/Film-Luts je przepakowuje).
- Licencja: **CC BY-SA 4.0**. Nasze LUT-y 33³ to utwór zależny – także CC BY-SA 4.0. Katalog `luts/` jest wyłączony z MIT.
- Nazwy filmów tylko opisowe; brak powiązania z producentami, brak logo.
- Status: w APK tylko zestaw startowy (identity + 10 filmów, 33³ RGB8) w `core/engine/src/main/assets/luts/`; pełna paczka 260 filmów pobierana z release `luts-v1` (D8). Format: docs/LUT-BIN.md (PR #5).

## Regeneracja
- Narzędzie `lutcheck.py` (Python Programista Master, osobny PR).
- Wejście: oryginalne HaldCLUT (144³). Wyjście: `.cube` 33³.

## Dowód jakości
- Porównanie 33³ (z interpolacją jak w silniku) vs HaldCLUT 144³: **dE < 1**.
- `report.json` – wyniki dE (średnia, maks.) dla każdego LUT-a.
- Lista hashy (SHA-256) wszystkich plików w `luts/`, weryfikowana w CI.
