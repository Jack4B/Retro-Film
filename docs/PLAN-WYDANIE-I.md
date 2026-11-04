# Plan wydania I (v-test-2026-11-04-I)

Zakres zatwierdzony 2026-11-04 14:21 (UTC+1). Wygładzanie skóry – POZA zakresem.
Zasady: PR do `test` (nigdy `main`), bez force-push, offline (bez telemetrii), bez prywatnych zdjęć w repo/CI,
TDD, systematic-debugging, samodzielny code review przed scaleniem.

## Dane wejściowe (telefon, H, realme RMX5085, 12 MP P3, agsl-tetra)
Stabilnie (4 zapisy). Szczyt 367–403 MB. `save_lut_effects_dither` 4508–5510 ms (G: 1438–1672),
ramka 442–485, dekodowanie ~180, konwersja ~215, kodowanie ~150, zapis ~60, całość 5,6–6,6 s.
`open_full_preview` 1227–1309 ms (G: 568). `auto_suggest` 29–89 ms.

## Etapy

| # | Etap | DoD | Status |
|---|------|-----|--------|
| 0 | Plan (ten plik) | zatwierdzony w PR | ✅ |
| 2 | Regres pełnego podglądu i zapisu z H | przyczyna potwierdzona testem; układ pasów nie ogranicza wątków bez potrzeby | ✅ (StripBudgetTest; CI do potwierdzenia) |
| 1a | Pasy CPU: mniej pamięci na wątek (wyostrzanie w podpasach, dokładny szacunek), układ maksymalizujący przepustowość w budżecie | 12 MP: 8 wątków w ≤ 224 MB buforów; wynik bit-w-bit niezależny od układu | ✅ kafle 2D (kolumny+pasy), sumy okien w double; bit-w-bit test |
| 1b | GPU (AGSL/GLES) dla zapisu | patrz „Ryzyka” – realizowane etapami, CPU zostaje rezerwą | ⏳ |
| 3 | Postęp pojedynczego zapisu | pasek określony (etapy + pasy), UI responsywne | ⏳ |
| 4 | Auto-korekta | pomiar bez przyciętych próbek; DNG/Ultra HDR z realnego zapasu; `auto_suggest` ≤ 30 ms | ⏳ |
| 5 | Audyt efektów | `docs/AUDYT-EFEKTOW.md` + poprawki (winieta cos⁴ w świetle liniowym, ziarno, …) | ⏳ |
| 6 | Bez regresji | crash log / save_error, chip HDR, kopia zapasowa, logi | ⏳ |
| 7 | Wydanie | CI zielone, review, merge, prerelease I | ⏳ |

## Plan testów
- Jednostkowe (Robolectric): układ pasów i budżet, niezależność wyniku od układu, postęp zapisu (monotoniczny 0→1),
  pomiar ekspozycji bez przepalonych próbek, winieta (spadek cos⁴, neutralna przy 0), ziarno (zależność od jasności).
- Roborazzi jasny/ciemny – bez zmian UI poza paskiem postępu.
- Emulator (CI): `SaveStressTest` (12 MP ×4 kolejno, 50 MP, 2 naraz), `PerfTimingTest`, `SaveStagesTest`, `AutoSuggestTest`.

## Ryzyka
- GPU na emulatorze CI to programowy renderer – czasy GPU z CI nie przenoszą się na telefon; potwierdzenie tylko na realme.
- Pełne przeniesienie wyostrzania (dekonwolucja RL), odbandowania i odszumiania do AGSL z dokładnością „w granicach
  ditheringu” to duża zmiana; jeśli nie zmieści się w tym wydaniu, zostaje rezerwa CPU i jest to jawnie opisane.
- Zmiany wyglądu efektów (etap 5) zmieniają obrazy wzorcowe – aktualizowane świadomie, opisane w audycie.
