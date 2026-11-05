# Wydanie L (2026-11-04) – GPU domyślnie wyłączone, pomiar zamiast zgadywania

Telefon (realme RMX5085, Mali-G720, 3072x4096, K z GPU): lut=gpu-gles-f32, save_lut_effects_dither 5906 ms vs ~1 s na CPU (J). GPU ~6× wolniej.

## Zmiany
1. Przełącznik „Zapis na GPU” **domyślnie wyłączony** (CPU w pasach).
2. Próbka backendów raz na proces; każdy zapis loguje `save_backend_probe name=… result=ok|rejected diff= tol= probe_ms=` lub `result=error error=Klasa:_komunikat` (`cached=true` po pierwszym zapisie) – widać, czemu AGSL odpadł.
3. Auto-pomiar przy włączonym GPU: pierwszy pas liczony na GPU (po rozgrzewce) i CPU; GPU wybrane tylko gdy `gpu_ms × wątki < cpu_ms` (CPU liczy pasy równolegle, GPU szeregowo). Zdarzenie `save_backend_bench`; decyzja trzymana do końca procesu (`reason=bench_cpu_faster`).
4. GLES: tekstury/FBO/bufory używane ponownie między pasami (wcześniej tworzone per pas), wyjście RGBA16F, wgrywanie hurtowe glTexSubImage2D.
5. Podział czasu `save_lut_gpu_split upload_ms= draw_ms= readback_ms= calls=` w CSV.

## Nie zrobione
- Asynchroniczny odczyt PBO (etap pasów jest synchroniczny; wymaga przebudowy potoku).
- Utrwalanie wyniku próbki między uruchomieniami (per wersja+urządzenie) – tylko cache w procesie.
- Wejście nadal RGBA32F (16F wejścia dałoby błąd > tolerancji przy ciemnych tonach – niezmierzone).

## Dokumentacja
Zachowanie przełącznika: [USTAWIENIA-I-ZOOM.md](USTAWIENIA-I-ZOOM.md). Domyślnie OFF obowiązuje od L (nadpisuje K).
