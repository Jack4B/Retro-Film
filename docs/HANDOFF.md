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

## 2026-10-25 – Forge
- Faza: F1 (silnik LUT)
- Zrobione: `core:engine` – `LutEngine`, `Lut` (format binarny RGB8 33³, docs/LUT-BIN.md), backendy AGSL / GLES 3.0 (tekstura 3D) / CPU, fabryka z fallbackiem do CPU, testy JVM CPU, test instrumentalny GPU vs CPU (`diff.json`).
- PR / gałąź: `f1/engine` (draft, baza `f0/szkielet`; po squash F0 – rebase na `main`).
- Otwarte / P2 odłożone: potwierdzić format binarny z Python Programista Master; uruchomić `GpuVsCpuTest` na urządzeniach (API 26–32 i 33+).
- Następny krok: review Gauge → Critic.

