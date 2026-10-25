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
| D8 | Dystrybucja LUT-ów: pobieranie z Jack4B/Retro-Film-LUTs@`luts-v1` + zestaw startowy w APK | Zdecydowane (rozmiar siatki: D9) |
| D9 | Siatka filtrów do pobrania: natywne 144³ PNG (jeden film 256³) | **Zdecydowane – Jack4B 2026-10-25** |

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
- Wynik (PR #11): 260/260 spełnia (33³ trój- i czworościenna, 48³ obie). Szczegóły: `tools/packs/report-33-full.json`.
- Status: wyłącznie propozycja – nie obowiązuje do decyzji Jack4B.

## D8 – LUT-y pobierane na żądanie (zestaw startowy w APK)
- Pełna paczka jest w osobnym repo **Jack4B/Retro-Film-LUTs**, przypiętym do tagu **`luts-v1`** (nigdy `main`): `https://raw.githubusercontent.com/Jack4B/Retro-Film-LUTs/luts-v1/<file>`, `file` = `luts/<kategoria>/<nazwa>.png`. Tag tworzy się **wyłącznie na polecenie Jack4B**.
- Tylko HTTPS, jeden host na liście dozwolonych (`raw.githubusercontent.com`), także dla przekierowań. Bez mirrorów, bez telemetrii, bez identyfikatorów urządzenia, stały User-Agent `RetroFilm`.
- Uprawnienia `INTERNET` i `ACCESS_NETWORK_STATE` – **sieć służy wyłącznie do pobierania filtrów**.
- `app/src/main/assets/luts/catalog-144.json` (bajt w bajt z Retro-Film-LUTs@0432c5a; pola m.in. `grid`, `unpackedLength`, `sha256`, `bytes`) jest wbudowany w APK. SHA-256 i rozmiar sprawdzane **przed** przyjęciem pliku; niezgodność ⇒ usunięcie i `Failed(CHECKSUM)`; potem atomowy rename. Test JVM porównuje katalog z `SHA256SUMS` (260 wpisów).
- WorkManager: wznawianie (HTTP Range do `.part`, kontrola `Content-Range`), kolejka wraca po odzyskaniu sieci; „tylko Wi‑Fi” (domyślnie wł.) ⇒ `NetworkType.UNMETERED`. Przed startem sprawdzane wolne miejsce (`Failed(STORAGE)`).
- Pobieranie jest **włączone** (`LutDownloadFeature.ENABLED = true`, polecenie Jack4B 2026-10-25); pobrane filmy widzi silnik (`DownloadedFilms`) i lista w Edytorze. Wymaga istniejącego tagu `luts-v1` w Jack4B/Retro-Film-LUTs (tag zakłada się wyłącznie na polecenie Jack4B).
- W APK jest mały zestaw offline: identity + 10 filmów startowych w 33³ RGB8 (`core/engine/src/main/assets/luts/`, `catalog.json` z polem `bundled`).
- Każda paczka zawiera `LICENSE` (CC BY-SA 4.0) i atrybucję: Pat David, Pavlov Dmitry, Michael Ezra – RawTherapee Film Simulation Collection.
- Rozmiar siatki i format paczki do pobrania: zob. **D9**. Konwerter `tools/hald2lut.py` obsługuje dowolne `--size` i `--bits 8|16` (zestaw startowy w APK pozostaje 33³ RGB8).

## D9 – Filtry do pobrania: natywne 144³ PNG
- Filtry do pobrania to natywne HaldCLUT w PNG: 259 × 144³ i 1 × 256³ (`negative_color/kodak_ektar_100`, poziom 16, 4096×4096); łącznie 378 138 252 B za 260 filmów.
- Kryterium jakości: max ΔE2000 < 1 względem oryginału – spełnione z definicji (to oryginał; 33³ spełnia 5/260, 48³ 23/260, pomiary w PR #11).
- JPG tylko dla miniatur i zdjęć wynikowych, nigdy dla samych LUT-ów.
- Rozpakowanie przy pierwszym użyciu, cache z LRU, sha256 na PNG, długość po rozpakowaniu z `unpackedLength` w `catalog-144.json` (144³·3 = 8 957 952 B; 256³·3 = 50 331 648 B). Cache LRU ~180 MB liczony z rzeczywistych rozmiarów.
- Budowa paczki: `tools/packs/build144png.py` (wynik w repo Jack4B/Retro-Film-LUTs; tag `luts-v1` tylko na polecenie Jack4B).
- Decyzja Jack4B 2026-10-25.

## D10 – Zarządzanie barwami przy eksporcie (P3 → sRGB)

- Filmy (HaldCLUT) są zbudowane i liczone w sRGB, więc zdjęcie spoza sRGB (np. Display P3) jest przed LUT-em konwertowane do sRGB.
- Eksport zapisuje profil sRGB; przestrzeń barw bitmapy i profil ICC w pliku zawsze się zgadzają (testy `PipelineTest`).
- Świadome ograniczenie: kolory spoza sRGB są przy filtrze obcinane; szerszy gamut P3 nie jest zachowywany.
