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
| D10 | Zarządzanie barwami przy eksporcie (P3 → sRGB) | Opisane niżej; status do potwierdzenia przez Jack4B |
| D11 | Nazwy filmów w UI: prawdziwe, jak w paczce LUT | **Zdecydowane – Jack4B 2026-10-25** |
| D12 | Zakres v0.1 i UX (redesign v2) | **Zdecydowane – Jack4B 2026-10-25** |
| D13 | Motyw jasny i ciemny (wybór w Ustawieniach) | **Zdecydowane – Jack4B 2026-10-25** |

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

## D11 – Nazwy filmów w UI
- Nazwy filmów zostają prawdziwe, tak jak w paczce LUT (RawTherapee Film Simulation Collection), np. „Kodak Portra 400”, „Fuji Velvia 50”. Dotyczy UI, makiet i katalogu.
- Bez logo i grafik producentów. Zmienione zapisy: AGENTS.md („Twarde reguły”) i docs/LUTS.md.
- Zamyka T1 z docs/OCENA-I-REDESIGN.md (decyzja Jack4B, 2026-10-25, przekazana przez Nexus w PR #26).
- Do zatwierdzenia: informacja „niezależny projekt, bez powiązania z producentami” w „Licencje i autorzy”.

## D12 – Zakres v0.1 i UX (redesign v2)
- **Nawigacja:** Start → Edytor z zakładkami (Film / Korekty / Ramka / Więcej) → zapis; zakładki zamiast osobnych ekranów.
- **Wybór zdjęć:** tylko systemowy Photo Picker z wyborem wielu zdjęć; `GalleryScreen` usunięty (wymagałby uprawnień do mediów).
- **Seria:** jeden film i jedna ramka na całą serię; podpis osobno dla każdego zdjęcia; bez Room i bez zapisu projektów.
- **Eksport:** w v0.1 tylko JPEG; PNG zostaje wyłącznie dla plików LUT.
- **Miniatury filmów:** w APK (JPG, ok. 4 MB łącznie), także dla filmów do pobrania, żeby wybierać po wyglądzie.
- **Biblioteka:** 260 filmów obsłużone kategoriami z licznikami, wyszukiwaniem i ulubionymi.
- Decyzja Jack4B, 2026-10-25 (PR #26). Kod UI idzie osobnymi PR-ami po zatwierdzeniu makiet (pierwszy: „Edytor krok 1”, PR #27).

## D13 – Motyw jasny i ciemny
- Wybór w Ustawieniach → Wygląd: **Systemowy / Ciemny / Jasny**. Domyślnie **Ciemny** (D3 bez zmian).
- Paleta jasna: tło #FBF8F3, tekst #211B13, akcent #8A5200 (kontrast AA; jasny bursztyn #F5A33B na jasnym tle ma ≈ 2:1, więc w jasnym motywie nie służy do tekstu ani przycisków).
- Dynamic color (D3, domyślnie wył.) działa w obu motywach.
- Nakładki na zdjęciu (np. „Przytrzymaj zdjęcie”) zawsze ciemne z białym tekstem, niezależnie od motywu.
- Decyzja Jack4B, 2026-10-25 (czat). Kod: PR „Motyw jasny”.

## D14 – Zasady kluczowe: jakość obrazu i efektywna praca
- **Maksimum z JPEG** jest wyróżnikiem aplikacji (barwa, zachowanie detalu, wyostrzanie włosów i oczu odpowiednimi, także semantycznymi metodami); wszystkie zmiany algorytmów oceniamy pod tym kątem, na podstawie aktualnej literatury.
- **Efektywna praca**: oszczędność tokenów (abonament Pro); reguły w AGENTS.md.
- Decyzja Jack4B, 2026-10-26 (czat).

## D15 – Kolejność pracy: najpierw korekty, potem film (kierunek, wymaga projektu UX)
- Docelowy przepływ: **Kadr → Korekty** (balans bieli, ekspozycja, zdejmowanie przebarwień, odszumianie, dehaze) → **Film** (LUT jako „look”) → **Wykończenie** (ziarno, winieta, blaknięcie, tonowanie, ostrość wyjściowa) → Ramka. Filmy są projektowane dla zdjęcia po korektach, więc stosowanie ich na nieskorygowanym obrazie daje gorszy i mniej przewidywalny wynik.
- Dziś eksport robi odwrotnie (film, potem korekty); zmiana wymaga przeprojektowania zakładek edytora i kolejności w potoku (film w potoku float między korektami a wykończeniem, zob. PRZEGLAD-ALGORYTMOW.md, pozycja 4).
- Kierunek zgłoszony przez Jack4B, 2026-10-26 (czat); szczegóły UX do zatwierdzenia (makiety Lumen).
