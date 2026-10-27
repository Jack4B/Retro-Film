# Przegląd algorytmów: ocena jakości i plan ulepszeń

Pierwotny przegląd: wersja 1.0.3 (potok pasmowy), autor: Claude, na prośbę Jack4B po uwadze, że światła „nadal wyglądają
słabo”. **Zaktualizowany 2026-10-27 do wersji 1.1.12** (zob. §0): analizy i oceny z 1.0.3 zostały, ale każda ma
teraz oznaczony stan wdrożenia.

## 0. Stan na 1.1.12: co jest aktualne, a co historyczne

Od 1.0.3 wydano fazę A (1.0.4: wierność filmów), fazę B (1.0.5: korekty bez niszczenia pikseli), fazę C (1.1.0: zakładki,
kolejność korekty → film → wykończenie) i poprawki 1.1.1–1.1.12. Jak czytać dokument:

- **§1 (diagnoza świateł)** jest historyczna: opisuje stan 1.0.3, a problem rozwiązała zmiana B1 w 1.0.5.
- **§2 (oceny modułów)**: oceny 1–5 pochodzą z 1.0.3; nowa kolumna „Stan 1.1.12” mówi, co się od tego czasu zmieniło.
- **§3 (plan)** ma kolumnę „Stan” (✅ wdrożone, 🟡 częściowo, ❌ nie wdrożone) przy każdej pozycji.
- **§3a (stan wiedzy)** ma przy każdym podrozdziale linię „Stan”; podrozdział H (ziarno) jest nowy.
- **§4 (źródła)** bez zmian; **§5 (ograniczenia)** uzupełniony.

**Wdrożone od 1.0.3:** światła/cienie v5, Biele/Czernie/Tekstura, odtworzenie krawędzi chromy 4:2:0, wyostrzanie świadome
treści (włosy, oczy, skóra), odbandowanie przed filmem, „Przygotuj pod film”, mapowanie gamutowe Display P3 → sRGB,
kolejność korekty → film → wykończenie, straż regresji suwaków na CI, ziarno ze skorelowanymi kanałami.

**Nadal otwarte (kolejność wg wartości dla JPEG z telefonów):**

1. Odszumianie jasności: nadal wygładzanie filtrem prowadzonym, najsłabszy moduł; B3 (DQT + NLM) odłożone, bo na JPEG
   z telefonów zysk znikomy.
2. Maski: tylko liniowa i radialna; brak pędzla oraz masek luminancji i koloru.
3. Film w pełnej precyzji: LUT trójliniowy 8-bit (tetrahedralny jest w kodzie, ale nie domyślny), brak LUT 16-bit.
4. Shoulder filmic/AgX (odbarwianie przepaleń do bieli), zmiana rozmiaru w świetle liniowym, szeroki gamut jako
   przestrzeń robocza, dehaze z haze-lines, Auto WB Grey-Edge.
5. Ziarno: model Boolean (Newson 2017) za drogi na telefonie; ziarno zależne od ostrości i parametry z metadanych
   filmu to możliwe dalsze kroki.
6. **Dodge & Burn** (wyrównanie skóry w pasmie środkowym i pędzel): zaplanowane, jeszcze bez kodu, zob.
   [PLAN-DODGE-BURN.md](PLAN-DODGE-BURN.md).

## Jak oceniałem

1. Przegląd kodu każdego modułu względem teorii (równania, założenia, zakres stosowalności).
2. Metryki z raportu jakości CI (9 zdjęć stockowych dobrej jakości, `quality-report.yml`).
3. Prototyp lepszych świateł offline (port algorytmu do Pythona/numpy, te same zdjęcia, porównanie wizualne i liczbowe).
4. Literatura i projekty open source (tylko wiedza o metodach; kodu copyleft nie kopiujemy, zob. `ALGORYTMY.md`).

Oceny 1–5 to mój osąd jakości względem stanu wiedzy, nie wynik testów użytkowników. Prototyp jest przybliżeniem
potoku w Kotlinie (bez wszystkich etapów), więc liczby z niego pokazują kierunek, nie wartości końcowe.

## 1. Światła: diagnoza (historyczna, wersja 1.0.3; rozwiązana w 1.0.5 przez B1)

**To nie jest regres.** Metryki jasnego obszaru po „światłach −100%” są praktycznie identyczne przed i po potoku
pasmowym (np. aloe: spadek jasności −56,6 → −57,0, lokalny kontrast ×1,06 w obu wersjach). Zmiany 1.0.3 dotyczyły
kwantyzacji i pamięci, nie matematyki świateł.

**Co robi dziś algorytm.** Baza jasności B = siatka ~512 komórek wygładzona filtrem prowadzonym; mnożnik
m = 2^(−1,2·0,4545·w(B)), w = smoothstep(0,40; 0,90; B); detal ponad bazą wzmacniany tak, by zachować jego
bezwzględny kontrast; barwa skalowana słabiej niż jasność. Skutek na zdjęciu: biel 255 → ok. 175 (kodowanie sRGB),
lokalny kontrast jasnego obszaru ×0,94…×1,23, barwność ×0,97…×1,0 (CI).

**Dlaczego wygląda „słabo”.**

- Algorytm *zachowuje* strukturę (chmury), ale jej nie *wzmacnia*. Lightroom, Capture One i local Laplacian
  (Paris 2011) przy ściąganiu świateł jednocześnie dodają kontrast lokalny w tej strefie; tutaj powstaje płaski szary
  obszar o tej samej fakturze, tylko ciemniejszy.
- Barwa w świetle: bezwzględna chroma zostaje, ale jasność spada o 30%, więc niskonasycone niebo (pochmurne) zostaje
  szare. Zysk z odrobiny chromy w światłach jest widoczny w prototypie.
- Rozkład na bazę i detal liczony w kodowaniu gamma, a nie w logarytmie jasności (Durand–Dorsey 2002): stosunki
  jasności (kontrast względny) są wtedy skalowane nierówno w cieniach i światłach.
- Jedna skala bazy: struktura średnich rozmiarów (kształt chmur) nie ma własnej regulacji.
- W obszarach całkowicie prześwietlonych (255) nie ma informacji do odzyskania; ściągnięcie ich daje szary placek.
  Lepiej je oszczędzać (zostają białe) niż szarzyć.
- JPEG ma 8 bitów bez zapasu (nie scene-referred), więc „odzyskanie” prześwietleń jest ograniczone z natury; zysk
  jest głównie w kontraście lokalnym i barwie.

**Prototyp (port numpy, 7 zdjęć, jasna ćwiartka, −100%).** Rozkład w logarytmie na 3 warstwy (baza gruba + warstwa
średnia z filtru prowadzonego + warstwa drobna), kompresja tylko bazy, wzmocnienie warstw detalu proporcjonalne do
suwaka i do wagi światła, chroma +25%·s.

| Wariant | lokalny kontrast (×) | barwność (×) |
|---|---|---|
| obecny (parametry produkcyjne) | 0,94 … 1,23 | 0,97 … 1,00 |
| prototyp „pro” (a1 = 1,0; a0 = 0,5) | 0,93 … 1,03 | 1,04 … 1,22 |
| prototyp „mocny” (a1 = 1,8; a0 = 0,9) | 1,04 … 1,35 | 1,06 … 1,32 |

Wniosek: wariant „pro” poprawia głównie barwę (chmury i niebo przestają szarzeć); wariant „mocny” dodaje też
wyraźną strukturę (chmury na łodziach, niebo na plaży). Różnica jest widoczna, ale to udoskonalenie, nie przełom;
przełom wymagałby przejścia na local Laplacian lub scene-referred filmic (zob. plan, pozycje 1–3).

**Czego jeszcze nie wiem.** Co dokładnie nie podoba się Jack4B na konkretnym zdjęciu z telefonu. Zrzut „przed/po”
przy −100% pozwoli wybrać między: wzmocnieniem struktury, barwy, ochroną prześwietleń albo samym zakresem suwaka.

## 2. Ocena modułów

| Moduł | Metoda w aplikacji | Ocena | Najważniejsze uwagi | Stan 1.1.12 |
|---|---|---|---|---|
| Ekspozycja, kolano | mnożnik w świetle liniowym, miękki shoulder | 4 | brak odbarwiania do bieli (filmic/AgX): przepalone kolory zostają nasycone | bez zmian (filmic/AgX niewdrożone) |
| Balans bieli | adaptacja Bradforda, temperatura w miredach | 4 | CAT16 jako alternatywa; Auto WB: shades-of-gray p = 2 (Grey-Edge, van de Weijer 2007, dałby stabilniejszy wynik) | bez zmian; Auto-WB działa też w „Przygotuj pod film” (A3, na żądanie) |
| Cienie i światła | baza filtra prowadzonego + detal | 3 | zob. §1; szum drobnego detalu w głębokich cieniach rośnie ×1,4…×1,9 po rozjaśnieniu (raport CI, 7 z 9 zdjęć), brak sterowania szumem | **poprawione (B1, 1.0.5)**: log 3-warstwowy, chroma i ochrona wypaleń w światłach, `SHADOW_DETAIL` 0,1 (szum w cieniach rośnie wolniej niż jasność), straż regresji suwaków (B6); ocena ≈ 4 |
| Kontrast | sigmoida na jasności Rec. 709, skalowanie kanałów | 4 | pivot 18% jako opcja filmowa jest; hue-preserving | dodane Biele/Czernie (`EndsCurve`) i Tekstura (`TextureBoost`) w B2 (1.0.5) |
| Nasycenie (vibrance) | OKLab, chroma przy stałym L i odcieniu | 4,5 | gamut przez bisekcję chromy; lepsza byłaby adaptacyjna L0 (Ottosson) dla bardzo nasyconych świateł | bez zmian |
| HSL, tonowanie | OKLCH / OKLab | 4 | percepcyjnie poprawne | bez zmian |
| Krzywe | PCHIP (monotoniczny) RGB + jasność | 4,5 | bez nadstrzałów | bez zmian |
| Wyrazistość | baza filtra prowadzonego w 600 px, jedna skala | 3,5 | brak osobnej „Tekstury” (skala drobna) i skali średniej | częściowo: skala drobna jest teraz osobnym suwakiem Tekstura (B2); Wyrazistość nadal jedna skala |
| Wyostrzanie | USM z miękkim limitem + Richardson–Lucy, maska krawędzi | 4 | halo: 0,0–0,2% pikseli > 12 poziomów (1,2% na drobnym tekście); PSF gaussowski bez estymacji | **rozbudowane (B5, 1.0.5)**: włosy (koherencja tensora struktury), oczy (`FaceFinder`), skóra słabiej, przełącznik; podgląd maski krawędzi |
| Dehaze | DCP (He 2009) + filtr prowadzony, duża łatka, ω 0,85 | 3,5 | PSNR na sztucznej mgle +1…+9 dB na wszystkich 9 zdjęciach; słabość: białe obiekty i niebo (DCP); alternatywa: haze-lines (Berman 2016) | bez zmian (haze-lines niewdrożone) |
| Odszumianie koloru | filtr prowadzony Cb/Cr z jasnością jako przewodnikiem | 3,5 | błąd chrominancji −50…−80%; działa dobrze, nie modeluje szumu zależnego od sygnału | dodane odtworzenie krawędzi chromy 4:2:0 (B4, 1.0.5) |
| Odszumianie jasności | filtr prowadzony, eps = (σk)², σ Immerkaera | 2,5 | to wygładzanie, nie denoiser: efekt „plastiku”, σ globalne (zawyżone na teksturach), stałe w jasności; stan wiedzy: NLM/BM3D/falki + stabilizacja wariancji | bez zmian: B3 (DQT + NLM) odłożone, na JPEG z telefonów zysk znikomy; nadal najsłabszy moduł |
| Ziarno | model kryształów srebra, amplituda √(p(1−p)), tekstura Gaussa | 4 | wygląd dobry; fizycznie dokładniejszy jest model Boolean (Newson 2017), ale kosztowny | **zmienione (1.1.11–1.1.12)**: kanały silnie skorelowane (szum barwny 16%), szare zdjęcia i odcień filmu dają ziarno prawie szare; zob. §3a H |
| Winieta | cos⁴ | 4 | | bez zmian |
| Dithering | TPDF ±0,5, jedna kwantyzacja na końcu korekt | 5 | | bez zmian; dochodzi odbandowanie przed filmem (A2) |
| Silnik filmów | LUT 3D, domyślnie trójliniowy, wynik 8-bit | 3,5 | tetrahedralna jest w kodzie, ale nieużywana domyślnie; wynik filmu kwantyzowany do 8 bitów bez ditheringu przed korektami | częściowo: `Deband` przed filmem (A2, ≤ ±3 poziomy tylko na płaskich gradientach); LUT nadal 8-bit, trójliniowy domyślnie |
| Gamut | sRGB (zdjęcia P3 wracają przez kanwę) | 3 | telefony zapisują P3; przycinanie do sRGB zubaża nasycone kolory | **wdrożone (A4, 1.0.4)**: Display P3 → sRGB z mapowaniem gamutowym w OKLab zamiast przycinania; przestrzeń robocza nadal sRGB |
| Zmiana rozmiaru | Lanczos 3, jądro rozciągane | 3,5 | skalowanie w kodowaniu gamma (ciemnieją drobne jasne detale); lepsze w świetle liniowym | bez zmian (nadal w kodowaniu gamma) |
| Maski lokalne | liniowa, radialna + maska tonalna cieni/świateł | 3 | brak pędzla i masek luminancji/koloru (najczęściej używane w programach pro) | bez zmian: liniowa i radialna (do 3), brak pędzla i masek luminancji/koloru |
| Auto-ulepszenie | percentyle + scena (skóra, niebo) | 3,5 | heurystyki; sensowne granice, brak uczenia | dodane niezależne, odwracalne przełączniki Auto i Przygotuj (1.1.1) |
| Geometria, perspektywa | Catmull–Rom, homografia, auto-kadr | 4 | | bez zmian |

## 3. Plan ulepszeń (priorytet, nakład, ryzyko)

| # | Zmiana | Zysk | Nakład | Ryzyko | Stan 1.1.12 |
|---|---|---|---|---|---|
| 1 | **Światła/cienie v5**: rozkład logarytmiczny 3-warstwowy, wzmocnienie detalu ~ suwak, chroma w światłach, ochrona prześwietleń, sterowanie szumem w cieniach | światła wyraźnie lepsze (kontrast, barwa) | 1 PR | niskie (testy metryk + zestawienia przed/po) | ✅ 1.0.5 (B1) |
| 2 | Suwak **Tekstura** (skala drobna), **Biele/Czernie**, skala średnia osobno | pełny zestaw jak w programach pro | 1 PR | niskie | ✅ 1.0.5 (B2: Tekstura, Biele, Czernie; warstwa średnia w `ToneMask`) |
| 3 | Shoulder **filmic/AgX**: odbarwianie do bieli zachowujące odcień, obejmuje kolano ekspozycji | mniej „plastikowych” przepaleń | 1 PR | średnie (zmiana wyglądu) | ❌ niewdrożone (opcja estetyczna, niski priorytet) |
| 4 | Film w potoku float: LUT tetrahedralny, LUT 16-bit/float, dither przed pierwszą kwantyzacją | mniej pasmowania w niebie (potwierdzone w literaturze SMPTE) | 2 PR | średnie (wydajność GPU) | 🟡 film po korektach i odbandowanie (A1, A2); LUT tetrahedralny/16-bit nadal nie domyślny |
| 5 | Przestrzeń robocza szeroki gamut (P3) | pełne kolory z telefonu | 2 PR | średnie | 🟡 mapowanie gamutowe P3 → sRGB (A4); szeroki gamut jako przestrzeń robocza nie |
| 6 | Zmiana rozmiaru w świetle liniowym | poprawne drobne detale | 1 PR | niskie | ❌ niewdrożone |
| 7 | **Odszumianie jasności**: model szumu zależny od sygnału (Poisson–Gaussa: σ²(y) = a·y + b), lokalne σ, NLM-lite lub falki ze stabilizacją wariancji (GAT) | ostatni słaby moduł | 2–3 PR | średnie (czas) | ❌ odłożone (B3: niski zysk na JPEG z telefonów) |
| 8 | Maski: luminancja, zakres koloru, pędzel z krawędzią z filtru prowadzonego | najczęściej używane narzędzie pro | 2 PR | niskie | ❌ niewdrożone |
| 9 | Dehaze: A z regionu nieba, opcjonalnie haze-lines | dehaze na niebie i bieli | 1–2 PR | średnie | 🟡 ochrona nieba i kompensacja jasności są; haze-lines nie |
| 10 | Ziarno: parametry z metadanych filmu, opcjonalny model Boolean | autentyczność | 2 PR | niskie | 🟡 ziarno poprawione w 1.1.11–1.1.12 (zob. §3a H); parametry z metadanych filmu i model Boolean nie |
| 11 | Auto WB Grey-Edge, Auto-tone z krzywą S ważoną twarzami | lepsze „Auto” | 1 PR | niskie | 🟡 Auto ze sceną (skóra, niebo) jest; Grey-Edge nie wdrożony |

Proponowana kolejność (z 1.0.3): 1 → 2 → 7 → 4 → 3 → reszta; wykonano 1 i 2, pozycja 7 odłożona. Każda zmiana z raportem jakości na zdjęciach stockowych i testem
równoważności tam, gdzie zachowanie ma się nie zmienić.

## 3a. Stan wiedzy 2024–2026: co daje „maksimum z JPEG”

Zasada kluczowa (D14): wyróżnikiem ma być wyciśnięcie z JPEG-a maksimum barwy i detalu (włosy, oczy). Poniżej
metody, które to realnie dają, z oceną dojrzałości i ryzyk. Wszystko oznaczone „niezweryfikowane” wymaga sprawdzenia
przed wdrożeniem (licencje modeli, opóźnienia na telefonie).

### A. Wyostrzanie świadome treści (włosy, oczy, skóra)

**Stan 1.1.12:** wdrożone w 1.0.5 (B5): włosy przez koherencję tensora struktury, oczy z `FaceFinder` (Android `FaceDetector`, bez modeli uczonych; licencja modeli TFLite nadal nierozstrzygnięta), skóra wyostrzana słabiej, własna implementacja (kwestia patentowa niezweryfikowana).

- Stałe wzmocnienie USM prześwietla skórę i wzmacnia szum; literatura i praktyka sprowadzają się do **mapy siły
  wyostrzania** G(x) z segmentacji: skóra łagodniej (lub wygładzanie), oczy, brwi, usta i włosy mocniej, krawędzie
  z ograniczeniem aureoli (np. adaptacyjne wyostrzanie świadome skóry, arXiv 2107.14765).
- Oczy: Android `FaceDetector` (już używany w `FaceFinder`) daje tylko środek oka i odległość; pełne kontury i
  tęczówka (478 punktów w MediaPipe Face Landmarker) wymagają modelu TFLite. **Licencja modeli i ich waga w APK:
  niezweryfikowane → eskalacja do Jack4B przed użyciem (reguła AGENTS.md).**
- Włosy bez uczenia: pasma włosów mają **wysoką spójność orientacji gradientu** (tensor struktury, współczynnik
  koherencji λ1−λ2 / λ1+λ2). Maska „włosopodobna” = wysoka koherencja przy drobnej skali; wyostrzanie kierunkowe
  (wzdłuż krawędzi, nie w poprzek) wzmacnia pasma bez wzmacniania szumu. Metoda znana z coherence-enhancing filtering
  (Weickert) i wyostrzania sterowanego tensorem struktury; tania, bez modelu.
- Warstwa detalu zamiast USM na obrazie: wyostrzanie jako wzmocnienie warstwy detalu bazy z filtra prowadzonego w
  logarytmie, bramkowane lokalnym σ szumu (nie wzmacnia szumu i nie rysuje aureoli).
- Uwaga prawna: opisane idee są powszechne, ale istnieją patenty na mapy siły wyostrzania z map twarzy/skóry; wdrażamy
  własną implementację, bez kopiowania zastrzeżeń (kwestia patentowa niezweryfikowana).

### B. Wiedza specyficzna dla JPEG (największa nieużywana dźwignia)

**Stan 1.1.12:** odtworzenie krawędzi chromy 4:2:0 wdrożone (B4, 1.0.5). Model szumu z tablic DQT i odblokowywanie odłożone (B3): na zdjęciach z telefonów, zapisanych w wysokiej jakości, zysk znikomy, a szum mierzony z obrazu wystarcza.

- **Model szumu kwantyzacji z tablic DQT.** Plik JPEG zawiera tablice kwantyzacji; błąd kwantyzacji współczynnika to
  ~Δ/√12, więc znamy amplitudę „szumu komarowego” i blokowego bez zgadywania (dziś: σ Immerkaera z całego obrazu,
  zawyżone na teksturach). Pozwala to sterować odszumianiem jasności i bramką wyostrzania per-obraz i per-jasność.
  Koszt: parsowanie markerów DQT, bez nowych zależności.
- **Odblokowywanie (deblocking).** Klasycznie SA-DCT (Foi 2007), które było najlepsze wśród metod nieuczonych, ale
  rozmywa; FBCNN (ICCV 2021) jest uczone, ze sterowaniem współczynnikiem jakości; model za ciężki na dziś. Realistyczna
  ścieżka: bramkowanie wyostrzania i odszumianie świadome siatki 8×8 z DQT.
- **Podpróbkowanie chromy 4:2:0.** W JPEG z telefonów kolor ma połowę rozdzielczości; dekoder go rozmywa, więc na
  krawędziach (włosy na niebie) kolor „wycieka”. **Odtworzenie krawędzi chromy filtrem prowadzonym z jasnością jako
  przewodnikiem** (to samo narzędzie co w `ChromaDenoise`, tylko w drugą stronę) wyostrza barwy bez zmiany detalu;
  efekt jest widoczny jako „czystsze” kolory przy krawędziach.

### C. Tone mapping i światła

**Stan 1.1.12:** dekompozycja logarytmiczna z filtrem prowadzonym wdrożona (B1, 1.0.5). Odbarwianie do bieli (filmic/AgX) niewdrożone.

- Metody lokalne dla JPEG: dekompozycja logarytmiczna z filtrem zachowującym krawędzie (Durand–Dorsey 2002, guided
  filter He 2013) i local Laplacian (Paris 2011; wersja szybka Aubry 2014, ~350 ms/Mpx/rdzeń, więc na pełnej
  rozdzielczości za wolna, ale możliwa dla bazy w niskiej skali).
- Operatory filmic/AgX/Khronos PBR Neutral są projektowane dla scene-referred HDR; dla JPEG przydaje się z nich idea
  **odbarwiania do bieli z zachowaniem odcienia** w shoulderze; opinie w sieci są rozbieżne, który jest „lepszy”,
  więc traktujemy to jako opcję estetyczną, nie obowiązek.

### D. Metody uczone

**Stan 1.1.12:** bez zmian, obserwacja (nic z tej sekcji nie jest wdrożone ani planowane na teraz).

- 3D LUT adaptacyjne (AdaInt, CVPR 2022; SepLUT, ECCV 2022): sieć przewiduje wagi LUT z miniatury; działa w czasie
  rzeczywistym na GPU/CPU. Zgodne z naszym silnikiem LUT, ale wymaga modelu i danych treningowych (zbiory typu
  MIT-Adobe FiveK mają ograniczenia licencyjne; niezweryfikowane) oraz pomiarów na telefonie (niezweryfikowane).
  Obserwować; nie jest potrzebne, by osiągnąć wyróżnik.
- Odszumianie na telefonie: sieci mobilne (MFDNet, 2022) są realne, ale jedno z badań wskazuje, że wersje GPU
  klasycznych NLM i BM3D bywają 10–20× szybsze od CNN o porównywalnej jakości i mniej wygładzają regularne tekstury.
  Dla nas: najpierw NLM-lite z modelem szumu z DQT, uczone później.
- NAFNet i podobne w standardowej wielkości są za ciężkie na telefon (przykład: NAFNet-Deblur ok. 68 M parametrów,
  niewspierany na układach mobilnych w Qualcomm AI Hub).

### E. Zmiana rozmiaru w świetle liniowym

**Stan 1.1.12:** niewdrożone (Lanczos 3 w kodowaniu gamma).

Zasada jest poprawna (uśrednianie fizyczne), ale filtry z ujemnymi płatami (Lanczos, Catmull-Rom) w świetle liniowym
dają aureole przy kontrastowych krawędziach, więc pozycja 6 planu wymaga testu na zdjęciach z tekstem i krawędziami;
bezpieczniejsze jest jądro bez ujemnych płatów (obszarowe/Mitchell) przy zmniejszaniu.

### F. Kolejność w potoku (kierunek D15)

**Stan 1.1.12:** wdrożone: korekty → film → wykończenie (A1 w 1.0.4, D15; zakładki w 1.1.0). Film nadal przechodzi przez 8-bitową bitmapę po LUT-cie (trójliniowy), ale jest poprzedzony odbandowaniem (A2).

Korekty (balans bieli, ekspozycja, przebarwienia, odszumianie, dehaze) → film (LUT) → wykończenie (ziarno, winieta,
blaknięcie, tonowanie, ostrość wyjściowa). Obecnie film idzie pierwszy i jest kwantyzowany do 8 bitów; docelowo film
w potoku float, między korektami a wykończeniem.

### G. Zaktualizowana kolejność wdrożeń (pod „maksimum z JPEG”)

**Stan 1.1.12:** punkty 2–5 wdrożone (B4, B5, B1–B2, D15 w fazie C), punkt 1 odłożony (B3), punkt 6 częściowo (mapowanie gamutowe P3 → sRGB; reszta otwarta).

1. Model szumu z DQT + odszumianie jasności z bramką wyostrzania (pozycja 7 planu, ale oparta o JPEG).
2. Odtworzenie krawędzi chromy (kolor przy krawędziach).
3. Wyostrzanie kierunkowe z maską włosów (koherencja) i oczu (z `FaceFinder`), z mapą siły i bramką szumu.
4. Światła/cienie v5 (pozycje 1–2).
5. Reorganizacja przepływu korekty → film → wykończenie wraz z redesignem zakładek (D15).
6. Reszta: tetrahedral LUT 16-bit, szeroki gamut, maski, dehaze, ziarno.

### H. Ziarno filmowe (przegląd z 2026-10-27, po zgłoszeniu kolorowego ziarna na Polaroidzie)

Zgłoszenie: na zdjęciu czarno-białym z odcieniem filmu (Polaroid) ziarno wychodziło kolorowe. Przyczyna: model dawał
każdemu kanałowi RGB niezależny szum barwny (44% wariancji), a wykrywanie „czarno-białego” (1.1.11) miało wąski próg,
którego odcień filmu nie spełniał. Przegląd metod (sprawdzone pod kątem naszej aplikacji; kodu copyleft nie kopiujemy,
bierzemy tylko idee):

| Metoda | Co daje | Decyzja |
|--------|---------|---------|
| Zależność od jasności (prawo Selwyna, p(1−p)) | ziarno „oddycha” z tonami | **już jest** (`FilmGrain.stops`) |
| Clumping, widmo dolnoprzepustowe, dwie oktawy | organiczny wygląd, bez ostrych pikseli | **już jest** (tekstura Gaussa po torusie) |
| Silna korelacja kanałów (Pierre 2023, stos warstw kryształów; Zhang 2023) | unika cyfrowego szumu barwnego | **wdrożone (1.1.12)**: szum barwny 16% wariancji zamiast 44%, reszta to wspólna jasność |
| Udział barwy zależny od nasycenia piksela | zdjęcia czarno-białe i z odcieniem dostają ziarno prawie szare, odcień zostaje | **wdrożone (1.1.12)**: gładka skala, rozrzut kanałów 0,02 → 0,30 zamiast progu |
| Newson, Delon, Galerne 2017 (model Boolean, Monte Carlo) | najwierniejszy, niezależny od rozdzielczości | **odłożone**: koszt na 12 Mpx przekracza budżet czasu na telefonie |
| Ansel (GPL), Dehancer, Filmbox, TheGrain | warstwy, ziarno zależne od ostrości (TheGrain) | tylko idee; ziarno zależne od ostrości do rozważenia później |
| Skany prawdziwego ziarna (overlaye) | autentyczność | **odrzucone**: licencje skanów, wzór „zamrożony”, rozmiar w APK |

## 4. Źródła

- darktable, moduł *tone equalizer* (maska z filtra prowadzonego niezależnego od ekspozycji, typowo promień 1–10% dłuższego boku): https://docs.darktable.org/usermanual/5.6/en/module-reference/processing-modules/tone-equalizer/
- Dyskusja o *tone equalizer* (kompresja histogramu przez filtr prowadzony, kompensacja kontrastu maski): https://discuss.pixls.us/t/a-tone-equalizer-in-darktable/10678/101
- Aubry, Paris, Hasinoff, Kautz, Durand, *Fast Local Laplacian Filters* (SIGGRAPH 2014; funkcje remapowania detalu i krawędzi, ~350 ms/Mpx na jednym rdzeniu): https://imagine.enpc.fr/~aubrym/projects/llf/data/Aubry_fastLLF_siggraph14.pdf
- Paris, Hasinoff, Kautz, *Local Laplacian Filters* (2011); Durand, Dorsey, *Fast Bilateral Filtering* (2002); He, Sun, Tang, *Guided Image Filtering* (PAMI 2013); He, Sun, Tang, *Dark Channel Prior* (2009).
- Berman, Treibitz, Avidan, *Non-Local Image Dehazing* (CVPR 2016): https://openaccess.thecvf.com/content_cvpr_2016/papers/Berman_Non-Local_Image_Dehazing_CVPR_2016_paper.pdf
- SMPTE, *A Review of 3D-LUT Performance in 10-bit and 12-bit HDR BT.2100 PQ* (tetrahedralna przewyższa trójliniową; ta sama jakość przy ok. 20–25% mniejszej LUT): https://journal.smpte.org/periodicals/SMPTE%20Motion%20Imaging%20Journal/129/2/17/
- Newson, Delon, Galerne, *A Stochastic Film Grain Model for Resolution-Independent Rendering* (CGF 2017): https://diglib.eg.org:443/handle/10.1111/cgf13159
- Foi i in., stabilizacja wariancji i uogólniona transformacja Anscombego dla szumu Poisson–Gaussa; Dabov i in., BM3D (2007); Buades i in., NLM (2005): https://webpages.tuni.fi/foi/
- Wyostrzanie świadome skóry: *A guided edge-aware smoothing-sharpening filter…* (arXiv 2107.14765): https://arxiv.org/pdf/2107.14765
- Jiang, Zhang, Timofte, *Towards Flexible Blind JPEG Artifacts Removal* (FBCNN, ICCV 2021): https://openaccess.thecvf.com/content/ICCV2021/html/Jiang_Towards_Flexible_Blind_JPEG_Artifacts_Removal_ICCV_2021_paper.html
- Yang i in., *AdaInt* (CVPR 2022): https://openaccess.thecvf.com/content/CVPR2022/papers/Yang_AdaInt_Learning_Adaptive_Intervals_for_3D_Lookup_Tables_on_Real-Time_CVPR_2022_paper.pdf; Yang i in., *SepLUT* (ECCV 2022): https://arxiv.org/pdf/2207.08351
- *Lightweight network towards real-time image denoising on mobile devices* (MFDNet): https://arxiv.org/pdf/2211.04687
- Ottosson, *A perceptual color space for image processing* (OKLab, 2020); Mertens, Kautz, Van Reeth, *Exposure Fusion* (2007); van de Weijer i in., *Edge-Based Color Constancy* (2007).

## 5. Ograniczenia tego przeglądu

- Oceny 1–5 są moim osądem, nie badaniem użytkowników.
- Prototyp świateł nie obejmuje całego potoku Kotlina (np. efektów po drodze); wartości końcowe mogą się różnić.
- Nie oceniałem wyglądu na telefonie; przegląd opiera się na kodzie, metrykach CI i porównaniach z numpy.
- Aktualizacja z 2026-10-27 dodaje tylko status wdrożenia (kolumny „Stan 1.1.12”, linie „Stan”, §0 i §3a H); oceny 1–5 w §2 nie były przeliczane od nowa, więc poprawione moduły mają ocenę z 1.0.3, a nową opisuje kolumna stanu.
