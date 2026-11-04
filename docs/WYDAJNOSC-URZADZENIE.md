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
