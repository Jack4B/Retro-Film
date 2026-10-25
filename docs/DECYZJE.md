# Decyzje

| ID | Temat | Status |
|---|---|---|
| D1 | Licencje LUT-ów | Zdecydowane: CC BY-SA 4.0 |
| D2 | Podpis odręczny: font OFL czy rysunek palcem | Otwarte (po makietach) |
| D3 | Dynamic color domyślnie wył. | Zdecydowane |
| D4 | LUT-y w APK czy pobierane na żądanie | **Otwarte – decyduje Jack4B** |
| D5 | Formuła `versionCode` | Proponowane |
| D6 | Material 3 w wersji beta (Expressive) | Zdecydowane (tymczasowo) |
| D7 | Kryterium jakości LUT-ów 33³ (ΔE2000) | **Proponowane – czeka na Jack4B** |

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

## D6 – Material 3 `1.5.0-beta02` (Expressive)
- `androidx.compose.material3:material3` przypięte jawnie do `1.5.0-beta02` w `gradle/libs.versions.toml` (nadpisuje wersję z Compose BOM).
- Powód: Jack4B chce Material 3 Expressive (`MaterialExpressiveTheme`, `MotionScheme.expressive()`), którego nie ma w stabilnym wydaniu.
- Ryzyko: API Expressive jest eksperymentalne (`@ExperimentalMaterial3ExpressiveApi`) i może się zmienić lub zawierać błędy; beta może być niezgodna z innymi artefaktami z BOM.
- Plan: po wydaniu stabilnej wersji 1.5.0 przejść na stabilną (najlepiej usunąć nadpisanie i brać wersję z BOM) i poprawić ewentualne zmiany API.
## D7 – Kryterium jakości LUT-ów (proponowane, czeka na Jack4B)
- Pierwotny wymóg „max ΔE < 1” jest nieosiągalny dla 33³ (spełnia 5/260; 48³: 23/260). Przyczyna: rozdzielczość siatki, nie kwantyzacja (RGB16 ≈ RGB8).
- Propozycja: dla każdego filmu, ΔE2000 (sRGB D65 → Lab) 33³ RGB8 vs HaldCLUT 144³, wszystkie 144³ punktów, interpolacja jak w silniku (trójliniowa, `CpuLut`): **mean < 0,5 i p99 < 1,5**. Max – informacyjnie.
- LUT-y z max > 3 oznaczone `approx: true` w `catalog.json`.
- Identity LUT 33³ musi mieć max ΔE < 1 (golden test silnika, bez zmian).
- Wynik (PR #11): 260/260 spełnia (33³ trój- i czworościenna, 48³ obie). Szczegóły: `core/engine/src/main/assets/luts/report.json`.
- Status: wyłącznie propozycja – nie obowiązuje do decyzji Jack4B.
