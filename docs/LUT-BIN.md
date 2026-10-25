# Binarny format LUT (`.lut33`) – silnik

Narzędzia w `tools/` (lutcheck.py) produkują `.cube`; nie było specyfikacji formatu binarnego, więc silnik
definiuje własny (do potwierdzenia z Python Programista Master przy konwersji `.cube` → bin):

- Surowe bajty, **bez nagłówka**: `size³ × 3` bajtów; domyślnie `size = 33` → 107 811 bajtów.
- Każdy wpis to `R, G, B` jako `uint8` (0..255, wartość = round(v × 255), v z `.cube` przycięte do 0..1).
- Kolejność: **r zmienia się najszybciej**, potem g, potem b – ta sama co wierszy w `.cube`.
  Offset wpisu: `((b × size + g) × size + r) × 3`.
- Rozmiar wyliczany z długości pliku (pierwiastek sześcienny); dozwolone 2..64.
- Domena wejścia 0..1 (sRGB, bez transformacji).

Konwersja z `.cube`: wiersze danych w kolejności z pliku → `round(clamp(v,0,1)*255)` → zapis bajtów.

## Backendy (`core:engine`)
| Backend | Warunek | Interpolacja |
|---|---|---|
| AGSL `RuntimeShader` | API 33+ | trójliniowa w shaderze (LUT jako pasek 2D `size²×size`, próbkowanie nearest) |
| GLES 3.0 offscreen | kontekst ES3 dostępny | sprzętowa, tekstura 3D RGBA8 + `GL_LINEAR` |
| CPU | zawsze | trójliniowa, referencja (`CpuLut`) |

Fabryka `LutEngineFactory.create()` wybiera AGSL → GLES → CPU; backendy GPU przy błędzie spadają do CPU.
Golden test GPU vs CPU (`GpuVsCpuTest`) zapisuje `files/diff.json`; tolerancja: max delta ≤ 2/255 na kanał.
Obraz traktowany jako niepremnożony; alfa zachowana.
