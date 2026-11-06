# Wydanie S (2026-11-06) – ziarno według kart technicznych

## Źródło
Dokumentacja: [ZIARNO.md](ZIARNO.md). Decyzje Jack4B do [ZIARNO-PLAN.md](ZIARNO-PLAN.md) (PR #150, wdrożenie #151): Q1 ziarno domyślnie OFF; Q2 suwak 100% = σ z karty; Q3 warianty `+`/`++` bez dopłaty; Q4 50% rozmiaru = `grainUm`; Q5 Fuji CN ×1,8; Q6 instant = duże miękkie chmury barwnika.

## Zmiany
- Tabela profili emulsji (`FilmGrainProfiles`) dla całego katalogu; warianty LUT dziedziczą bazę.
- Amplituda: `amount · σ(gEq) ·` obwiednia Selwyna (zamiast stałego 0,5 EV).
- Komórka: `(grainUm / 36 mm) · dłuższy bok`, przy suwaku rozmiaru 0,5.
- B&W i chromogeniczny B&W: `chromaShare = 0`. Instant: większe `grainUm` i silniejsze kłaczki.
- Wybór filmu ustawia tylko `grainStockId` w ścieżce podglądu/zapisu — nie włącza suwaka.

## Testy
`FilmGrainProfilesTest` (kompletność katalogu, dziedziczenie, RMS @ D=1, niezależność 1280 vs 4000, B&W mono), regresje `AdjustmentsTest` / `FilmGrainPixelTest`.

## Poza zakresem
LUT, ekspozycja, krzywa, zoom, ramka, GPU.
