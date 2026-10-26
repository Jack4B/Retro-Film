# Przegląd algorytmów: ocena jakości i plan ulepszeń

Stan: wersja 1.0.3 (potok pasmowy). Autor: Claude, na prośbę Jack4B po uwadze, że światła „nadal wyglądają słabo”.

## Jak oceniałem

1. Przegląd kodu każdego modułu względem teorii (równania, założenia, zakres stosowalności).
2. Metryki z raportu jakości CI (9 zdjęć stockowych dobrej jakości, `quality-report.yml`).
3. Prototyp lepszych świateł offline (port algorytmu do Pythona/numpy, te same zdjęcia, porównanie wizualne i liczbowe).
4. Literatura i projekty open source (tylko wiedza o metodach; kodu copyleft nie kopiujemy, zob. `ALGORYTMY.md`).

Oceny 1–5 to mój osąd jakości względem stanu wiedzy, nie wynik testów użytkowników. Prototyp jest przybliżeniem
potoku w Kotlinie (bez wszystkich etapów), więc liczby z niego pokazują kierunek, nie wartości końcowe.

## 1. Światła: diagnoza

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

| Moduł | Metoda w aplikacji | Ocena | Najważniejsze uwagi |
|---|---|---|---|
| Ekspozycja, kolano | mnożnik w świetle liniowym, miękki shoulder | 4 | brak odbarwiania do bieli (filmic/AgX): przepalone kolory zostają nasycone |
| Balans bieli | adaptacja Bradforda, temperatura w miredach | 4 | CAT16 jako alternatywa; Auto WB: shades-of-gray p = 2 (Grey-Edge, van de Weijer 2007, dałby stabilniejszy wynik) |
| Cienie i światła | baza filtra prowadzonego + detal | 3 | zob. §1; szum drobnego detalu w głębokich cieniach rośnie ×1,4…×1,9 po rozjaśnieniu (raport CI, 7 z 9 zdjęć), brak sterowania szumem |
| Kontrast | sigmoida na jasności Rec. 709, skalowanie kanałów | 4 | pivot 18% jako opcja filmowa jest; hue-preserving |
| Nasycenie (vibrance) | OKLab, chroma przy stałym L i odcieniu | 4,5 | gamut przez bisekcję chromy; lepsza byłaby adaptacyjna L0 (Ottosson) dla bardzo nasyconych świateł |
| HSL, tonowanie | OKLCH / OKLab | 4 | percepcyjnie poprawne |
| Krzywe | PCHIP (monotoniczny) RGB + jasność | 4,5 | bez nadstrzałów |
| Wyrazistość | baza filtra prowadzonego w 600 px, jedna skala | 3,5 | brak osobnej „Tekstury” (skala drobna) i skali średniej |
| Wyostrzanie | USM z miękkim limitem + Richardson–Lucy, maska krawędzi | 4 | halo: 0,0–0,2% pikseli > 12 poziomów (1,2% na drobnym tekście); PSF gaussowski bez estymacji |
| Dehaze | DCP (He 2009) + filtr prowadzony, duża łatka, ω 0,85 | 3,5 | PSNR na sztucznej mgle +1…+9 dB na wszystkich 9 zdjęciach; słabość: białe obiekty i niebo (DCP); alternatywa: haze-lines (Berman 2016) |
| Odszumianie koloru | filtr prowadzony Cb/Cr z jasnością jako przewodnikiem | 3,5 | błąd chrominancji −50…−80%; działa dobrze, nie modeluje szumu zależnego od sygnału |
| Odszumianie jasności | filtr prowadzony, eps = (σk)², σ Immerkaera | 2,5 | to wygładzanie, nie denoiser: efekt „plastiku”, σ globalne (zawyżone na teksturach), stałe w jasności; stan wiedzy: NLM/BM3D/falki + stabilizacja wariancji |
| Ziarno | model kryształów srebra, amplituda √(p(1−p)), tekstura Gaussa | 4 | wygląd dobry; fizycznie dokładniejszy jest model Boolean (Newson 2017), ale kosztowny |
| Winieta | cos⁴ | 4 | |
| Dithering | TPDF ±0,5, jedna kwantyzacja na końcu korekt | 5 | |
| Silnik filmów | LUT 3D, domyślnie trójliniowy, wynik 8-bit | 3,5 | tetrahedralna jest w kodzie, ale nieużywana domyślnie; wynik filmu kwantyzowany do 8 bitów bez ditheringu przed korektami |
| Gamut | sRGB (zdjęcia P3 wracają przez kanwę) | 3 | telefony zapisują P3; przycinanie do sRGB zubaża nasycone kolory |
| Zmiana rozmiaru | Lanczos 3, jądro rozciągane | 3,5 | skalowanie w kodowaniu gamma (ciemnieją drobne jasne detale); lepsze w świetle liniowym |
| Maski lokalne | liniowa, radialna + maska tonalna cieni/świateł | 3 | brak pędzla i masek luminancji/koloru (najczęściej używane w programach pro) |
| Auto-ulepszenie | percentyle + scena (skóra, niebo) | 3,5 | heurystyki; sensowne granice, brak uczenia |
| Geometria, perspektywa | Catmull–Rom, homografia, auto-kadr | 4 | |

## 3. Plan ulepszeń (priorytet, nakład, ryzyko)

| # | Zmiana | Zysk | Nakład | Ryzyko |
|---|---|---|---|---|
| 1 | **Światła/cienie v5**: rozkład logarytmiczny 3-warstwowy, wzmocnienie detalu ~ suwak, chroma w światłach, ochrona prześwietleń, sterowanie szumem w cieniach | światła wyraźnie lepsze (kontrast, barwa) | 1 PR | niskie (testy metryk + zestawienia przed/po) |
| 2 | Suwak **Tekstura** (skala drobna), **Biele/Czernie**, skala średnia osobno | pełny zestaw jak w programach pro | 1 PR | niskie |
| 3 | Shoulder **filmic/AgX**: odbarwianie do bieli zachowujące odcień, obejmuje kolano ekspozycji | mniej „plastikowych” przepaleń | 1 PR | średnie (zmiana wyglądu) |
| 4 | Film w potoku float: LUT tetrahedralny, LUT 16-bit/float, dither przed pierwszą kwantyzacją | mniej pasmowania w niebie (potwierdzone w literaturze SMPTE) | 2 PR | średnie (wydajność GPU) |
| 5 | Przestrzeń robocza szeroki gamut (P3) | pełne kolory z telefonu | 2 PR | średnie |
| 6 | Zmiana rozmiaru w świetle liniowym | poprawne drobne detale | 1 PR | niskie |
| 7 | **Odszumianie jasności**: model szumu zależny od sygnału (Poisson–Gaussa: σ²(y) = a·y + b), lokalne σ, NLM-lite lub falki ze stabilizacją wariancji (GAT) | ostatni słaby moduł | 2–3 PR | średnie (czas) |
| 8 | Maski: luminancja, zakres koloru, pędzel z krawędzią z filtru prowadzonego | najczęściej używane narzędzie pro | 2 PR | niskie |
| 9 | Dehaze: A z regionu nieba, opcjonalnie haze-lines | dehaze na niebie i bieli | 1–2 PR | średnie |
| 10 | Ziarno: parametry z metadanych filmu, opcjonalny model Boolean | autentyczność | 2 PR | niskie |
| 11 | Auto WB Grey-Edge, Auto-tone z krzywą S ważoną twarzami | lepsze „Auto” | 1 PR | niskie |

Proponowana kolejność: 1 → 2 → 7 → 4 → 3 → reszta. Każda zmiana z raportem jakości na zdjęciach stockowych i testem
równoważności tam, gdzie zachowanie ma się nie zmienić.

## 4. Źródła

- darktable, moduł *tone equalizer* (maska z filtra prowadzonego niezależnego od ekspozycji, typowo promień 1–10% dłuższego boku): https://docs.darktable.org/usermanual/5.6/en/module-reference/processing-modules/tone-equalizer/
- Dyskusja o *tone equalizer* (kompresja histogramu przez filtr prowadzony, kompensacja kontrastu maski): https://discuss.pixls.us/t/a-tone-equalizer-in-darktable/10678/101
- Aubry, Paris, Hasinoff, Kautz, Durand, *Fast Local Laplacian Filters* (SIGGRAPH 2014; funkcje remapowania detalu i krawędzi, ~350 ms/Mpx na jednym rdzeniu): https://imagine.enpc.fr/~aubrym/projects/llf/data/Aubry_fastLLF_siggraph14.pdf
- Paris, Hasinoff, Kautz, *Local Laplacian Filters* (2011); Durand, Dorsey, *Fast Bilateral Filtering* (2002); He, Sun, Tang, *Guided Image Filtering* (PAMI 2013); He, Sun, Tang, *Dark Channel Prior* (2009).
- Berman, Treibitz, Avidan, *Non-Local Image Dehazing* (CVPR 2016): https://openaccess.thecvf.com/content_cvpr_2016/papers/Berman_Non-Local_Image_Dehazing_CVPR_2016_paper.pdf
- SMPTE, *A Review of 3D-LUT Performance in 10-bit and 12-bit HDR BT.2100 PQ* (tetrahedralna przewyższa trójliniową; ta sama jakość przy ok. 20–25% mniejszej LUT): https://journal.smpte.org/periodicals/SMPTE%20Motion%20Imaging%20Journal/129/2/17/
- Newson, Delon, Galerne, *A Stochastic Film Grain Model for Resolution-Independent Rendering* (CGF 2017): https://diglib.eg.org:443/handle/10.1111/cgf13159
- Foi i in., stabilizacja wariancji i uogólniona transformacja Anscombego dla szumu Poisson–Gaussa; Dabov i in., BM3D (2007); Buades i in., NLM (2005): https://webpages.tuni.fi/foi/
- Ottosson, *A perceptual color space for image processing* (OKLab, 2020); Mertens, Kautz, Van Reeth, *Exposure Fusion* (2007); van de Weijer i in., *Edge-Based Color Constancy* (2007).

## 5. Ograniczenia tego przeglądu

- Oceny 1–5 są moim osądem, nie badaniem użytkowników.
- Prototyp świateł nie obejmuje całego potoku Kotlina (np. efektów po drodze); wartości końcowe mogą się różnić.
- Nie oceniałem wyglądu na telefonie; przegląd opiera się na kodzie, metrykach CI i porównaniach z numpy.
