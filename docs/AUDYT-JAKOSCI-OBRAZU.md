# Audyt jakości obrazu (gałąź `test`, 2026-11-04)

Audyt dotyczy wszystkich etapów obróbki pod kątem standardów profesjonalnej obróbki zdjęć. Audyt tylko czyta kod, nic w nim nie zmienia.

Legenda:
- Wpływ: W = wysoki, Ś = średni, N = niski.
- Nakład: S = do 1 dnia, M = 2–4 dni, L = tydzień i więcej.
- Koszt podglądu: zmiana kosztu ścieżki podglądu.

| Obszar | Stan obecny (plik/funkcja) | Standard / dobra praktyka | Wpływ | Propozycja | Nakład | Koszt podglądu |
|---|---|---|---|---|---|---|
| Wejście: przestrzeń barw | `PhotoLoader.load` (BitmapFactory, ARGB_8888); `ColorPolicy.toSrgb` mapuje Display P3 do sRGB w OKLab (`GamutMapper`); inne profile ICC przycina Skia | Pracować w szerokim gamucie (P3/Rec.2020 liniowe), a mapować dopiero na wyjściu | W | Potok w liniowym P3 (lub Rec.2020) na floatach; mapowanie gamutu tylko przy zapisie | L | Ś |
| Wyjście: szeroki gamut | `ExportPipeline.render` → `FrameRenderer(colorSpaceSource = srgb)`: zapis zawsze w sRGB, więc kolory P3 z aparatu telefonu giną | Zachować P3 źródła i osadzić ICC P3 | W | Opcja „Zachowaj Display P3”: kodowanie w P3 z ICC (`JpegMetadata` już przepuszcza APP2) | M | 0 |
| ICC przy sRGB | `JpegMetadata.strip` zostawia APP2, ale koder Androida nie dopisuje ICC dla sRGB | Zawsze osadzać profil (nawet sRGB, ok. 3 KB) | N | Wstawić mały profil sRGB v4 do APP2 | S | 0 |
| Orientacja EXIF | `ExifOrientation.apply` obraca piksele, więc wynik jest poprawny | Zgodnie ze standardem | — | — | — | — |
| Metadane | `JpegExporter`: usuwane jest całe EXIF/XMP/IPTC | Pasjonaci oczekują EXIF bez GPS: aparat, obiektyw, ekspozycja, data, prawa autorskie | W | Biała lista tagów EXIF (bez GPS IFD, MakerNote i miniatury), Orientation=1, Software=RetroFilm | M | 0 |
| Rozdzielczość wejścia | `PhotoLoader.DEFAULT_MAX_SIDE = 4096`, `inSampleSize` jako potęga 2: zdjęcie 50 MP spada do ok. 12 MP | Eksport w pełnej rozdzielczości | W | Pełne źródło przy eksporcie (potok pasami już ogranicza pamięć); proxy tylko dla podglądu | M | 0 (podgląd i tak na proxy) |
| Głębia bitowa między etapami | Korekty (float, jedna kwantyzacja z ditheringiem TPDF, `StripPipeline`) → bitmapa 8-bit → LUT → 8-bit → wykończenie → 8-bit: trzy kwantyzacje | Jeden potok float/16-bit, jedna kwantyzacja na końcu | W | LUT wewnątrz potoku float (CPU, lub AGSL na `RGBA_F16`); pośrednie bitmapy `RGBA_F16` | M | N (F16 na GPU) |
| Dithering na wyjściu | `Dither` (TPDF ±0,5 LSB) w korektach; ścieżka LUT GPU nie dithuje | Dither przy każdej kwantyzacji do 8 bit | Ś | Dither w shaderze AGSL/GLES (szum niebieski) albo usunięcie pośrednich 8-bit | S | ~0 |
| Ekspozycja i balans bieli | `AdjustmentProcessor`: światło liniowe, macierz Bradforda (`WhiteBalance`), miękkie obcięcie świateł | Zgodnie ze standardem (CAT02/CAT16 lepsze dla nasyconych) | N | Opcjonalnie CAT16 | S | 0 |
| Krzywe tonalne | `ToneCurve`: PCHIP (Fritsch–Carlson), monotoniczne; `softClip` świateł | Zgodnie ze standardem | N | Krzywa także na luminancji (tryb „Luma”), żeby uniknąć przesunięć barwy | S | 0 |
| HSL | `HslMixer`: OKLab/OKLCh z mapowaniem chromy; indeks barwy ucina zakres przez `coerceIn` | OKLCh i zawijanie barwy 359→0 | Ś | Wagi pasm liczone kołowo (mod 360) z interpolacją między stopniami; dziś możliwy skok na granicy czerwień/magenta | S | 0 |
| Aplikacja LUT | Domyślnie trilinear (AGSL, GLES, CPU), tetraedryczna jest dostępna; większość LUT-ów ma 144³, jeden 33³; wejście `clamp` 0..1; domena sRGB (display-referred) | Tetraedryczna (mniej przesunięć neutralnej szarości); stała domena (shaper/log dla HDR) | Ś | Domyślnie tetraedryczna w AGSL (koszt GPU znikomy); test neutralnej szarości | S | N |
| Mieszanie intensywności LUT | `mix(rgb, lut, intensity)` w gamma | Mieszanie w świetle liniowym lub OKLab | N | Mieszanie w liniowym | S | ~0 |
| Wyostrzanie | `Sharpener` i `SemanticSharpen`: na luminancji, tłumienie aureoli, promień skalowany do rozmiaru, maskowanie; wyostrzanie wyjściowe po `Resampler` | Zgodnie ze standardem | N | Wyostrzanie wyjściowe zależne od nośnika (ekran/druk) | S | 0 |
| Ziarno | `FilmGrain`: Selwyn p(1−p), mnożnik gęstości, filtr Gaussa, torus 512, komórka skalowana do rozmiaru, korelacja kanałów | Bardzo dobrze, model filmowy | N | Ziarno zależne od formatu filmu (35 mm/120) w presetach | S | 0 |
| Winieta | `VignetteLut`: (1+d²)^−2, czyli prawo cos⁴, z przybliżeniem gamma (`GAMMA_INV`); elipsa wg proporcji | cos⁴ w świetle liniowym | N | Mnożenie w liniowym (etap wykończenia ma już liniowe kanały) | S | 0 |
| Rozmiar (resampling) | `Resampler`: Lanczos, pasami, liniowy przy eksporcie, sRGB w podglądzie | Zgodnie ze standardem | — | — | — | — |
| Obrót i perspektywa | `Geometry` (linia ok. 158): `Paint.FILTER_BITMAP_FLAG` to w Skia bilinear (komentarz mówi „bikubiczna”) | Bikubiczna/Lanczos, w liniowym | Ś | Własne próbkowanie Lanczos-3/bikubiczne przez macierz odwrotną (pasami) albo `drawBitmap` z `SamplingOptions(CubicResampler)` przez `RuntimeShader` | M | 0 (podgląd zostaje bilinear) |
| Ramka i podpis | `FrameRenderer`: `TextPaint(ANTI_ALIAS)` w rozdzielczości wyjścia; zdjęcie skalowane `drawBitmap(FILTER_BITMAP)` (bilinear) | Bez skalowania zdjęcia albo skalowanie Lanczosem | Ś | Wklejanie 1:1 (ramka dookoła) albo `Resampler` zamiast bilinear | S | 0 |
| Maski lokalne | `LocalMask`: liniowa/radialna z wtapianiem; `ToneMask`/`GuidedFilter` dla tonów | Maski dopasowane do krawędzi (guided) | N | Maska „zakres jasności/koloru” uściślona filtrem prowadzonym | M | N |
| Automaty | `AutoEnhance`, `AutoWhiteBalance` (analiza sceny) | Propozycje suwaków, nie nadpisywanie | N | Gray-world ważony luminancją z odrzuceniem nasyconych pikseli | S | 0 |
| JPEG | `Bitmap.compress` (libjpeg-turbo): jakość 92 (eksport wsadowy 95), **zawsze 4:2:0** | 4:4:4 przy q≥90 (ostre krawędzie koloru, czerwień, napisy), opcjonalnie optymalizacja Huffmana | W | Własny koder (libjpeg-turbo przez NDK lub `jpegli`) z 4:4:4 i ICC | M | 0 |
| Formaty 10-bit / bezstratne | Tylko JPEG | HEIF/AVIF 10-bit, PNG/TIFF 16-bit do archiwum | Ś | AVIF (`Bitmap.CompressFormat` API 34+ 10-bit przy F16) i PNG 16-bit | M | 0 |
| Ultra HDR / gain map | `BitmapFactory` dekoduje mapę zysku (API 34+), ale każdy etap tworzy nową bitmapę, więc mapa ginie i wynik jest SDR | Zachować lub przeliczyć mapę zysku | Ś | Skopiować `Bitmap.gainmap` po geometrii (przeskalowana) i dołączyć przy kodowaniu; LUT tylko na bazie SDR | M | 0 |
| RAW/DNG | Brak | Pasjonaci fotografują w RAW | W | `ImageDecoder` dla DNG (podgląd) lub libraw przez NDK; demozaikowanie poza zakresem MVP | L | Ś |

## Top 10 (priorytet)

1. **4:4:4 chroma i wyższa jakość JPEG.** Największy zysk widoczny gołym okiem na krawędziach kolorów, koszt podglądu zerowy.
2. **Eksport w pełnej rozdzielczości.** Dziś zdjęcie powyżej 4096 px traci ponad połowę pikseli.
3. **Jeden potok float z LUT w środku.** Usuwa dwie pośrednie kwantyzacje 8-bit, a z nimi pasmowanie w niebie i gradientach.
4. **Zachowanie Display P3 na wyjściu z ICC.** Kolory z aparatów telefonów nie są przycinane do sRGB.
5. **EXIF bez GPS.** Aparat, obiektyw i ekspozycja zostają, prywatność jest zachowana.
6. **Tetraedryczna interpolacja LUT jako domyślna w AGSL.** Mniej przesunięć barwy neutralnych tonów przy znikomym koszcie GPU.
7. **Bikubiczny/Lanczos przy prostowaniu, perspektywie i ramce.** Ostrzejszy wynik niż dzisiejszy bilinear.
8. **Dithering na wyjściu ścieżki GPU.** Tańsza alternatywa dla punktu 3.
9. **AVIF/HEIF 10-bit i PNG 16-bit.** Formaty archiwalne i HDR.
10. **Zachowanie Ultra HDR gain map, potem DNG.** Wierność HDR i wejście RAW dla pasjonatów.

Mniejsze poprawki:
- zawijanie barwy w HSL
- winieta i mieszanie intensywności LUT w świetle liniowym
- ICC sRGB osadzany zawsze
- CAT16
