# Decyzje

| ID | Temat | Status |
|---|---|---|
| D1 | Licencje LUT-ów | Zdecydowane: CC BY-SA 4.0 |
| D2 | Podpis odręczny: font OFL czy rysunek palcem | Otwarte (po makietach) |
| D3 | Dynamic color domyślnie wył. | Zdecydowane |
| D4 | LUT-y w APK czy pobierane na żądanie | **Otwarte – decyduje Jack4B** |
| D5 | Formuła `versionCode` | Proponowane |

## D1 – Licencje LUT-ów
RawTherapee Film Simulation Collection (Pat David, Pavlov Dmitry, Michael Ezra), CC BY-SA 4.0, https://rawpedia.rawtherapee.com/Film_Simulation. Regenerowane do 33³ jako utwór zależny CC BY-SA 4.0; `luts/` wyłączone z MIT. Szczegóły: [LUTS.md](LUTS.md).

## D2 – Podpis odręczny
Font OFL (Caveat) vs rysunek palcem. Decyzja po makietach (Lumen).

## D3 – Dynamic color
Domyślnie wyłączony; ciemny motyw marki, akcent `#F5A33B`.

## D4 – Dystrybucja LUT-ów (decyduje Jack4B)
- A) w APK – działa offline, prostsze. **Rekomendacja Nexus.**
- B) pobieranie na żądanie – mniejsze APK, wymaga sieci i hostingu.

## D5 – versionCode
`versionCode = (major*10000 + minor*100 + patch) * 1000 + run_number % 1000`
- Uwaga: `% 1000` się powtarza – przed przekroczeniem 999 zwiększ `patch`.
- CI kończy się błędem, jeśli nowy `versionCode` ≤ `versionCode` ostatniego wydania.
