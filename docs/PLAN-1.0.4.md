# Plan prac do 1.0.4 (zatwierdzony przez Jack4B, 2026-10-26)

Priorytety: **(1) wierna barwa filmów z LUT-ów**, **(2) korekty bez niszczenia pikseli** (suwak świateł nie robi obrazu
płaskiego i szarego), (3) przepływ UX (faza C = 1.1.0). Zasady kluczowe: AGENTS.md, D14 i D15 w DECYZJE.md.
Zatwierdzone: kolejność A → B → C; zakładki **Kadr · Korekty · Film · Wykończenie · Ramka**; „Przygotuj pod film” tylko
jako przycisk; 1.0.4 = fazy A i B, faza C jako **1.1.0**. Zmiana (2026-10-26, na prośbę Jack4B o build do oceny): 1.0.4 wydane po samej fazie A, faza B w kolejnym wydaniu. Bramki oceny na telefonie: po fazie A i po B1+B6.

## Wyniki pomiaru A0 (`tools/fidelity/a0_fidelity.py`, 8 filmów, 144³ i 256³)

| Co | Wynik | Wniosek |
|---|---|---|
| Podwójna kwantyzacja (film → 8 bit → korekta) | błąd średnio 0,25 poziomu, p99 1, max 1 | liczbowo pomijalna; film w float potrzebny głównie dla kolejności D15 |
| Poziomy na osi szarości po filmie | 189–231 z 256; 8–24% schodków rośnie o ≥ 2 poziomy (maks. 3–4) | ryzyko pasmowania w niebie i gładkich tłach; lekarstwo: odbandowanie wejścia i dithering, nie samo float |
| Przycinanie P3 → sRGB | zmiana odcienia p95 1,7°, p99 3,7° (mapowanie OKLab: 0°); wynik filmu różni się średnio o 1,2 poziomu (p95 5,6) | mała, tylko dla kolorów spoza sRGB; tanie do poprawy |
| Wierność samego LUT-a (144³, trójliniowo, CPU) | z definicji zgodna z paczką; GPU vs CPU: testy ≤ 1–2 poziomy | bez zmian |

Nie zmierzone: wpływ „wyglądu telefonu” (HDR+, krzywa S) na zgodność z wzorcem filmu; ocenimy A/B wizualnie (A3).
W RawTherapee (źródło paczki) film jest stosowany po krzywych tonalnych i korektach, na obrazie w kodowaniu sRGB, co
potwierdza kolejność z D15.

## Faza A: wierność filmów (priorytet 1)

- **A1. Kolejność i film w float**: potok Geometria → Odszumianie i dehaze → **Korekty** → **Film** (LUT na pasach, float,
  CPU: trójliniowo z kostki 8-bit; podgląd ma tę samą ścieżkę) → **Wykończenie** → ostrość wyjściowa → jedna kwantyzacja
  z ditheringiem. Podział pól: Korekty = ekspozycja, temperatura, tint, światła, cienie, kontrast, nasycenie, HSL,
  odszumianie, dehaze, wyrazistość, ostrość; Wykończenie = blaknięcie, tonowanie, krzywe, ziarno, winieta. Interfejs bez
  zmian (jedna zakładka Korekty do fazy C). Test równoważności dla ustawień bez filmu.
- **A2. Odbandowanie i dithering**: przed filmem w płaskich obszarach przywrócenie wartości podpoziomowych (filtr
  zachowujący krawędzie ograniczony do ±2 poziomów + szum TPDF), na końcu dithering (jest). Metryka: liczba konturów ≥ 2
  poziomy na gradiencie nieba przed i po.
- **A3. „Przygotuj pod film”** (przycisk, nie domyślne): Auto-WB, ekspozycja do wzorca i delikatne zdjęcie krzywej
  telefonu; ocena A/B wizualna na zdjęciach stockowych.
- **A4. Mapowanie gamutowe zamiast przycinania** (OKLab, stałe L i odcień) dla kolorów P3 przed filmem; D10 (zapis sRGB)
  bez zmian.
- **A5. Budżet**: ≤ 2,5 s przy 12 Mpx z filmem (CI, A/B w jednym przebiegu).

## Faza B: korekty bez niszczenia (priorytet 2)

- **B1. Światła/cienie v5**: rozkład logarytmiczny 3-warstwowy, wzmocnienie detalu zależne od suwaka, chroma w światłach,
  ochrona prześwietlonych płaszczyzn, bramkowanie szumu w cieniach.
- **B2. Biele/Czernie i Tekstura**, kontrast z pivotem 18%.
- **B3. Odszumianie jasności** z modelem szumu z tablic DQT pliku JPEG i NLM-lite; ochrona włosów i oczu.
- **B4. Odtworzenie krawędzi chromy** (korekta efektów 4:2:0).
- **B5. Wyostrzanie semantyczne**: włosy (koherencja gradientu), oczy (`FaceFinder`), skóra łagodniej, warstwa detalu w
  logarytmie, bramka szumu; bez ML w 1.0.4.
- **B6. Straż regresji** (przegląd suwaków −100…+100 na CI): kontrast lokalny i chroma w jasnych/ciemnych partiach ≥ 0,95×
  oryginału, nowe przycięcia ≤ 0,5% pikseli, halo ≤ 0,3%, szum w cieniach ≤ 1,3×.

## Faza C (1.1.0): redesign UX

Zakładki Kadr · Korekty · Film · Wykończenie · Ramka; w Korektach wybór narzędzia (Światło, Kolor, Detal, Szum) i
kompaktowa szuflada, reset podwójnym dotknięciem, „przed” długim przytrzymaniem, cofnij/ponów, przycisk „Przygotuj pod
film”; podział `Adjustments` na `Corrections` i `Finish` z migracją presetów i edycji serii. Makiety (Lumen) → zatwierdzenie →
kod.

## Poza zakresem

Podmiana nieba i generatywne uzupełnianie prześwietleń (AI), modele uczone, MediaPipe (licencje do weryfikacji).
