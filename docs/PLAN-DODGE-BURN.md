# Plan: Dodge & Burn (do dodania)

Stan: **plan, bez kodu** (2026-10-27). Wracamy do tego w sobotę 2026-10-31. Zasady: czyste algorytmy, bez sieci
neuronowych; kluczowa zasada nr 1 (nie niszczyć detalu, fantastyczna barwa); kodu copyleft nie kopiujemy (idee z
literatury, własna implementacja).

## 1. Cel

Dwa narzędzia w zakładce **Korekty**:

- **A. Wyrównanie skóry** (automatyczny mikro Dodge & Burn): wyrównuje plamy tonalne skóry (cienie pod oczami,
  przebarwienia, nierówne światło) suwakiem 0–100%, **bez ruszania porów i tekstury**. To nie jest wygładzanie skóry.
- **B. Pędzel Dodge & Burn** (ręczny): użytkownik maluje rozjaśnienie (dodge) i przyciemnienie (burn) z miękką
  krawędzią uwzględniającą krawędzie obrazu; ten sam kod w podglądzie i w zapisie.

## 2. Ocena wklejonych propozycji

| Propozycja | Decyzja | Uzasadnienie |
|---|---|---|
| Praca tylko na luminancji | ✅ bierzemy | w logarytmie liniowej jasności; barwa przez ten sam mnożnik, chroma jak w świetle v5 |
| `korekcja = −(oryginał − rozmyty)·siła` | ⚠️ **do poprawki** | różnica oryginał − duży blur zawiera też wysokie częstotliwości, więc taka korekta spłaszcza pory (efekt plastiku), wbrew założeniu; poprawka: korygować **tylko pasmo środkowe** |
| Gauss, promień 15–40 px | ⚠️ do poprawki | zależy od rozdzielczości; promienie względem szerokości twarzy (zapas: dłuższy bok), na siatce `ToneMask` |
| Bilateral / Guided / Surface Blur | ✅ guided | mamy `GuidedFilter` i `ToneMask`; bilateralny za wolny, Surface Blur to w praktyce to samo |
| Frequency Separation | ✅ rdzeń metody | w logarytmie i w trzech pasmach (gruba baza, średnia, detal) |
| Maska skóry z progów HSV/YCrCb | ⚠️ ulepszamy | mamy OKLab (`SceneAnalysis`); same progi barwy łapią drewno i piasek, więc wymagamy twarzy i rozrostu od niej |
| Haar/LBP/dlib | ❌ | brak w Androidzie; mamy `FaceDetector` (twarze zwrócone przodem) |
| MediaPipe | ❌ | to modele neuronowe (TFLite): poza zakresem, licencja i waga w APK niezweryfikowane |
| CLAHE na skórze | ❌ | podbija szum i daje sztuczny wygląd |
| Suwak 0–100% jako mnożnik | ✅ | jak pozostałe suwaki, 0 = brak zmian |
| „Realistyczne oczekiwania” | ✅ | zgodne z naszą oceną: dobre portrety dobrze, trudna skóra słabiej |

## 3. Metodologia A: wyrównanie skóry

**Przestrzeń i pasma** (na siatce `ToneMask`, ~512 komórek, filtr prowadzony): ℓ = ln(Y + ε), Y = liniowa
luminancja. Baza gruba B_c (promień r_c), baza średnia B_m (promień r_m < r_c), detal d = ℓ − B_m.
Pasmo środkowe D = B_m − B_c niesie plamy i cienie średniej skali; **d zostaje nietknięte**.

**Korekta:** Δℓ = −α · M(x) · D · w(|D|), gdzie w = 1 − smoothstep(T1, T2, |D|): małe odchyłki wyrównujemy w
pełni, duże (prawdziwa bryła twarzy: cień nosa, kontur) zostają. Twardy limit |Δℓ| ≤ 0,4 EV · ln 2. Wynik: Y′ = Y·e^Δℓ,
ten sam mnożnik na R, G, B (odcień bez zmian). T1 ≈ 0,15 EV, T2 ≈ 0,5 EV (do dobrania offline w numpy na portretach).

**Promienie:** względem szerokości ramki twarzy W_f: r_m ≈ 0,015·W_f, r_c ≈ 0,10·W_f; bez twarzy (maska ręczna lub
rozrost) względem dłuższego boku L: r_m ≈ 0,004·L, r_c ≈ 0,03·L.

**Maska M(x)** (liczona raz, w `StripAnalysis`, na siatce niskiej rozdzielczości ~256 px):

1. Elipsa twarzy z `FaceFinder` (jak w `SceneAnalysis`) jako zalążek; **bez twarzy efekt = 0** (podpowiedź w UI).
2. Wiarygodność skóry s(x): gładka funkcja odcienia OKLab 28–72°, chroma i jasności z progami z `SceneAnalysis`.
3. **Rozrost od twarzy:** składowe spójne s > 0,5 styczne do elipsy (szyja, ręce); limit odległości ≈ 3·W_f, żeby nie
   łapać drewna i piasku.
4. Wykluczenia: oczy (pozycje z `FaceDetector`), usta i brwi (odcień/chroma poza zakresem skóry), włosy (niskie s i
   wysoka koherencja gradientu z `SemanticSharpen`), obniżona waga na silnych krawędziach.
5. Miękkość: maska niskiej rozdzielczości dopasowana do krawędzi obrazu filtrem prowadzonym, potem powiększona;
   żadnych twardych krawędzi.

**Pipeline:** etap tonalny korekt (po odzyskiwaniu świateł i teksturze, przed Biele/Czernie i kontrastem), na
istniejącej siatce `ToneMask` (bez drugiego filtru prowadzonego); maska w planie `StripPlans`. **UI:** w Korektach
grupa „Skóra”: suwak „Wyrównanie tonu”, przełącznik „Podgląd maski skóry” (jak podgląd maski wyostrzania).

**Koszt (budżet):** ≤ +8% czasu „wszystko naraz” 12 Mpx na CI, ≤ +1 MB pamięci (maska niskiej rozdzielczości).

## 4. Metodologia B: pędzel Dodge & Burn

- Maska pędzla: bitmapa 8-bit w niskiej rozdzielczości (≤ 1024 px dłuższego boku), współrzędne znormalizowane (ten sam
  wynik w podglądzie i w zapisie), przepływ (flow) z miękkim zanikiem; osobne warstwy rozjaśnienia i przyciemnienia.
- Krawędzie: waga piksela maski zależy od podobieństwa jasności i barwy do punktu wejścia pędzla (jak „auto-maska” w
  programach pro) i/lub dopasowanie maski filtrem prowadzonym po pociągnięciu.
- Działanie: mnożnik 2^(±EV·maska) w świetle liniowym, ten sam na R, G, B; EV maks. ±1; opcjonalny zakres tonalny
  (cienie/półtony/światła).
- Rozmiar, miękkość, siła, cofnij/ponów; zapis w `PhotoEdit` (kompresja RLE), osobno dla zdjęcia (`EditBook`), ostrzeżenie
  przy wyjściu obejmuje maski; ≤ 1 MB na zdjęcie.
- Gesty na podglądzie (mamy infrastrukturę gestów i przesuwanie/szczypanie); miejsce w UI: Korekty → Maski → „Pędzel”.

## 5. Kryteria akceptacji (mierzalne, na CI)

- α = 0 → wynik **identyczny** (identity), maska pędzla pusta → identity.
- Skóra przy α = 1: energia pasma środkowego w masce −25…−45%; **energia pasma wysokiego (pory) ≥ 0,97**; średnia
  jasność skóry ±0,5%; zmiana odcienia < 0,5°; poza maską i piórem zmiana = 0; brak halo na granicy maski.
- Brak twarzy → brak zmian (A); testy na portretach różnych karnacji (zdjęcia stockowe z licencją, do raportu jakości:
  `portrait_stadium` jest, trzeba 3–4 kolejnych).
- Rozszerzona straż regresji suwaków (`SliderRegressionTest`), test wydajności 12 Mpx, brak regresji istniejących
  funkcji (światła, wyostrzanie włosów i oczu).

## 6. Etapy (osobne PR-y)

| Etap | Zakres | Wynik |
|---|---|---|
| D0 | prototyp w numpy na portretach stockowych, dobór T1, T2, promieni; metryki | stałe i raport przed/po |
| D1 | `SkinMask` (twarz, wiarygodność, rozrost, wykluczenia), podgląd maski, testy | maska bez efektu na obraz |
| D2 | `SkinEven` na siatce `ToneMask`, suwak w UI, testy kryteriów | **Wyrównanie skóry** (wydanie 1.2.0) |
| D3 | pędzel: maska, gesty, cofnij/ponów, zapis w `PhotoEdit`, UI | **Pędzel D&B** (1.2.x) |
| D4 | szlif: QA, dostępność (TalkBack, ≥ 48 dp), dokumentacja, zrzuty | wydanie |

## 7. Ryzyka i ograniczenia

- `FaceDetector` znajduje tylko twarze zwrócone przodem: profil, pochylenie i małe twarze → brak automatu (zostaje
  pędzel).
- Fałszywa skóra (drewno, piasek, ciepłe ściany): tylko w obrębie rozrostu od twarzy.
- Plastik: pilnujemy energii pasma wysokiego (kryterium ≥ 0,97), nie ruszamy detalu d.
- Patenty na retusz skóry: **niezweryfikowane → eskalacja do Jack4B przed wydaniem** (reguła AGENTS.md); idee są
  powszechne (separacja częstotliwości), implementacja własna.
- Ciemne karnacje i makijaż: progi odcienia wymagają oceny na zróżnicowanych zdjęciach.

## 8. Do ustalenia z Jack4B (sobota)

1. Czy suwak zaczyna od 0 (jak reszta), czy portrety dostają łagodną wartość domyślną po wykryciu twarzy?
2. Pędzel w pierwszym wydaniu razem z wyrównaniem, czy dopiero w kolejnym (rekomendacja: kolejny)?
3. Zakres: tylko twarz i okolica (szyja, ręce przez rozrost), czy także dowolne ręcznie wskazane obszary?
4. Nazwa grupy: „Skóra” / „Retusz skóry” / „Wyrównanie skóry”.
5. Kształtowanie światła na twarzy (delikatne podbicie oczodołów i czoła) jako osobny, późniejszy krok?
