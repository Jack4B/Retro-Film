# Binarny format LUT (`.rgb8` / `.rgb16`) – silnik

Narzędzia w `tools/` (lutcheck.py) produkują `.cube`; nie było specyfikacji formatu binarnego, więc silnik
definiuje własny (do potwierdzenia z Python Programista Master przy konwersji `.cube` → bin):

- Surowe bajty, **bez nagłówka**: `N³ × 3 × bajtyNaKanał` bajtów.
- **Rozmiar `N` i format pochodzą z wpisu `catalog.json`** (pola `size`, `format`; brak pola = wartość
  z korzenia katalogu, a potem 33 / `rgb8`). Obsługiwane N: 2..256 (w praktyce 33, 65, 144).
- `format: "rgb8"` → plik `.rgb8`, kanał `uint8` (0..255 = round(v × 255)); 33³ → 107 811 B, 65³ → 823 875 B,
  144³ → 8 957 952 B.
- `format: "rgb16"` → plik `.rgb16`, kanał `uint16` **little-endian** (0..65535 = round(v × 65535));
  długość ×2.
- Loader **waliduje długość pliku** = `N³ × 3 × (1 | 2)`; niezgodność = błąd (`IllegalArgumentException`).
- v z `.cube` przycinane do 0..1.
- Kolejność: **r zmienia się najszybciej**, potem g, potem b – ta sama co wierszy w `.cube`.
  Offset wpisu: `((b × size + g) × size + r) × 3`.
- Bez wpisu katalogu (`Lut.fromBinary(bytes)`) rozmiar RGB8 wyliczany z długości (pierwiastek sześcienny).
- Domena wejścia 0..1 (sRGB, bez transformacji).

Konwersja z `.cube`: wiersze danych w kolejności z pliku → `round(clamp(v,0,1)*255)` → zapis bajtów.

## Backendy (`core:engine`)
| Backend | Warunek | Interpolacja |
|---|---|---|
| AGSL `RuntimeShader` | API 33+ | trójliniowa w shaderze (LUT jako siatka kafli 2D, próbkowanie nearest) |
| GLES 3.0 offscreen | kontekst ES3 dostępny | sprzętowa, tekstura 3D RGBA8 + `GL_LINEAR` |
| CPU | zawsze | trójliniowa (domyślna) lub czworościenna, referencja (`CpuLut`), pełna precyzja RGB16 |

Domyślna interpolacja: **trójliniowa** (czworościenna dała pomijalny zysk ΔE2000).

### Limity tekstur
- **AGSL**: plaster `b` (N×N) w kaflu `(b mod T, b div T)`, `T = ceil(√N)`. Atlas: 33 → 198×198,
  65 → 585×585, 144 → 1728×1728 px (ARGB8888: 0,15 / 1,4 / 11,9 MB). Dawny pasek 1D `N²×N` dla 144
  miałby 20 736 px szerokości > typowego `GL_MAX_TEXTURE_SIZE` 16 384 (minimum ES3: 2048) – stąd kafle.
  Interpolacja między plastrami `b` liczona w shaderze (dwa odczyty z różnych kafli), więc brak przecieków
  na granicach kafli. Buforowane są maks. **2** tekstury LUT (LRU).
- **GLES 3.0**: tekstura 3D N³; minimum ES3 `GL_MAX_3D_TEXTURE_SIZE` = 256 → 144³ mieści się (RGBA8 ≈ 11,9 MB).
- RGB16 na GPU jest kwantowany do 8 bitów na kanał (tekstury RGBA8); pełną precyzję ma tylko CPU.

Fabryka `LutEngineFactory.create()` wybiera AGSL → GLES → CPU; backendy GPU przy błędzie spadają do CPU.
Golden test GPU vs CPU (`GpuVsCpuTest`) zapisuje `files/diff.json`; tolerancja: max delta ≤ 2/255 na kanał.
Obraz traktowany jako niepremnożony; alfa zachowana.
