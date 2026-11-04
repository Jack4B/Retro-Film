# Pomiary na urządzeniu (RMX5085, build testowy)

Wszystkie backendy: PASS, max_diff ≤ 1 względem CPU.

| Backend | Render 12 MP | Wysyłka LUT 33³ / 65³ / 144³ |
|---|---|---|
| AGSL | 142 ms (było 172) | 9 / 40 / 296 ms (bez zmian) |
| GLES3 | 364 ms (było 2406) | 3 / 6 / 11 ms (było 17 / 70 / 603) – cache tekstur |
| CPU | 3634 ms | – |

## Cache LUT dla AGSL (partia B)

Przyczyna 296 ms dla 144³: każdy render AGSL tworzy nowy `HardwareRenderer`, więc atlas LUT (bitmapa programowa)
był wysyłany na GPU przy każdym renderze. Teraz atlas jest od razu kopiowany do bitmapy `HARDWARE` (wysyłka raz),
cache LRU 6 LUT-ów (było 2), a `LutEngine.warm` przygotowuje atlasy sąsiednich filmów karuzeli w tle
(`MainState.prefetchNeighbours`). Powrót do filmu nie płaci już za wysyłkę; pierwszy raz – tak (pakowanie + upload).

Odłożone: atlas 2D zapisywany na dysk (cache plików) – do rozważenia, jeśli pierwsze otwarcie 144³ okaże się zbyt
wolne po zmierzeniu nowego builda.

## Pierwsze otwarcie zdjęcia (zgłoszenie: „pierwsze wczytanie trwa bardzo długo”)

Plik użytkownika (realme, prywatny, nie w repo): JPEG 3072×4096, 15,6 MB = obraz bazowy 5,8 MB + gain mapa Ultra HDR
2048×1536 (1 MB, MPF/XMP hdrgm) + wideo Motion/Live Photo 9,5 MB doklejone na końcu.

Przyczyny i poprawki:
- plik był czytany 4× przez ContentResolver (EXIF orientacja, wymiary, dekodowanie, miniatura) → teraz 1 odczyt do pamięci;
- podgląd dekodował do 4096 px (praktycznie pełna rozdzielczość) → teraz do 2560 (`PREVIEW_MAX_SIDE`, inSampleSize),
  pełna rozdzielczość tylko w eksporcie (`ExportSource`), a wymiary oryginału trzymane osobno (`sourceSize`);
- dekoder na API 34+ dekodował też gain mapę → „HDR ze zdjęcia” domyślnie Wył.: `UltraHdrJpeg.sdrOnly` odcina MPF,
  XMP hdrgm i wszystko za EOI obrazu bazowego (27 ms na JVM dla tego pliku: 16,4 MB → 5,8 MB);
- miniatura EXIF pokazywana od razu, zanim skończy się dekodowanie;
- rozgrzewka w tle przy starcie (`AppGraph.warmUp`): katalog, pierwszy LUT, atlas AGSL i mini-render (zimny shader/EGL).

Pomiar: `PerfTimingTest.firstOpen` (syntetyczny 12 MP + 9 MB „wideo” za JPEG-iem; prywatnego zdjęcia nie ma w CI):
`open_decode_4096_ms` (stara ścieżka), `open_thumbnail_ms`, `open_decode_preview_ms`, `open_first_preview_cold_ms`.

DNG: `RawLoader` (ImageDecoder, API 28+) – dekodowanie od razu do rozmiaru podglądu, orientacja z dekodera,
neutralne renderowanie platformy (balans bieli z pliku, bazowa krzywa DNG) jako płaski punkt startu.

### DNG z natywnego aparatu realme RMX5085 (GT 7T) — plik prywatny, poza repo
- DNG 1.4, Make `realme`, 3264×2448, Bayer CFA **10-bit nieskompresowany** (Compression 1, WhiteLevel 1023, BlackLevel 64), ~16 MB.
- **Brak osadzonego podglądu** (jedna strona IFD) → miniatura EXIF niedostępna; pierwszy podgląd dopiero po dekodzie RAW (ImageDecoder, cel 2560).
- **Orientation = 9 (niepoprawna wartość EXIF)** → traktowana jak 1 (Normal), `applyOrientation` nie obraca. Prawdziwej orientacji nie da się odczytać z pliku.
- BaselineExposure −0,40 EV, tylko ColorMatrix1 (D65), AsShotNeutral ≈ (1,0 / 0,572 / 0,610) → WB kamery ≈ R×1,75 B×1,64; brak ProfileToneCurve i opcodów (brak korekcji winiety/dystorsji w pliku).
- Referencja rawpy (WB kamery, bez auto-jasności) na boxie: 0,19 s, średnia 52/255, kanały R/G/B 59/53/46 — neutralny, lekko ciepły, płaski; ciemniej niż JPEG z aparatu (oczekiwane dla „flat”).
- Ryzyko: render platformowy (Skia/dng_sdk) na urządzeniu niezweryfikowany — brak emulatora/urządzenia na boxie.
