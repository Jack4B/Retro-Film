# Algorytmy edycji: źródła i licencje

Aplikacja jest na licencji MIT, więc **nie kopiujemy kodu na licencjach copyleft** (GPL, AGPL). Algorytmy
implementujemy samodzielnie na podstawie publicznych wzorów (standardy CIE, artykuły, macierz Bradforda, sRGB),
a z projektów open source czerpiemy wiedzę o podejściu, nie kod. Kod na licencjach permisywnych (MIT, BSD,
Apache 2.0) można by przenieść z zachowaniem noty autorskiej w `THIRD_PARTY.md`; na razie nic nie przenosiliśmy.

## Co i jak liczymy (`app/.../pipeline`)

| Narzędzie | Implementacja | Podstawa |
|---|---|---|
| Ekspozycja | mnożnik 2^EV w świetle liniowym, miękkie kolano świateł (bez ostrego przycięcia) | standard (scene-referred) |
| Temperatura i tint | adaptacja chromatyczna Bradforda do D65, temperatura w miredach (±70), tint prostopadle do krzywej dziennej; luminancja bieli zachowana (`WhiteBalance`) | CIE: krzywa światła dziennego, macierz Bradforda, macierze sRGB D65 |
| Kontrast | sigmoida wokół szarości 0,5, czerń i biel nieruchome; minus spłaszcza | standardowa krzywa logistyczna |
| Nasycenie | vibrance: plus wzmacnia głównie kolory mało nasycone, z ochroną odcieni skóry (pomarańcz–róż); minus odbarwia równomiernie, luminancja Rec.709 | opis publiczny (Photoshop/Lightroom) |
| Światła i cienie | rozkład na warstwę bazową i detal: baza = jasność na siatce ~512 komórek wygładzona filtrem prowadzonym (He i in.), więc zachowuje krawędzie; mnożnik 2^(siła · EV · waga(baza)) z przejściem liniowe→gamma, ten sam na wszystkich kanałach (barwa i lokalny kontrast detalu bez zmian); piksel jaśniejszy od bazy nie jest cieniem, ciemniejszy nie jest światłem; punkt czerni chroniony | publikacje naukowe (Durand i Dorsey 2002: rozkład baza/detal; He i in.: filtr prowadzony), własna implementacja |
| Blaknięcie, winieta | podniesiona czerń i przytłumiona biel; przyciemnienie rogów (d⁴) | standardowe modele |
| Ziarno | szum wartościowy z gładką interpolacją w dwóch oktawach (gruba + drobna) plus drobny szum barwny, deterministyczny, skalowany z rozmiarem, najsilniejszy w półtonach | własne |
| Wyostrzanie | jasność: maska nieostra (gaussowski rozmyty obraz, miękki próg szumu, soft-clip tanh ograniczający aureole) ↔ dekonwolucja Richardsona–Lucy'ego (PSF gaussowski, 2–4 iteracje) wg „Detalu”, tryby Standardowe / Maska nieostra / Dekonwolucja; maska krawędzi z gradientu rozmytej jasności (jak „Masking” w Lightroomie) z podglądem; promień skaluje się z rozmiarem obrazu; liczone paskami wierszy (stała pamięć); zestawy Uniwersalne/Krajobraz/Portret i Auto (udział skóry YCbCr wg Chai i Ngan, gęstość krawędzi, szum metodą Immerkaera 1996) | publikacje naukowe, własna implementacja |
| Kadr, obrót, prostowanie | największy prostokąt o zadanych proporcjach wpisany w obrócone zdjęcie (`GeometryMath`) | geometria |

## Przegląd projektów (licencje sprawdzone w plikach LICENSE repozytoriów)

| Projekt | Licencja | Wniosek |
|---|---|---|
| awxkee/aire (CLAHE, dehaze, tone mapping, vibrance) | MIT | można przenosić kod z notą; kandydat na dehaze/CLAHE |
| T8RIN/ImageToolbox | Apache 2.0 | wzorzec całej aplikacji, kod możliwy do użycia z notą |
| zomato/androidphotofilters (krzywe tonalne) | Apache 2.0 | możliwy do użycia; krzywe RGB to kandydat na przyszłość |
| wysaid/android-gpuimage-plus | MIT | możliwy do użycia (shadery OpenGL) |
| pch/rawmakase | MIT | młody projekt (Rust), głównie inspiracja |
| wasabeef/android-gpuimage | nie zweryfikowano pliku licencji | nie używamy, dopóki nie sprawdzimy |
| CyberTimon/RapidRAW | **AGPL-3.0** | tylko inspiracja pomysłami, kodu nie kopiujemy |
| darktable, RawTherapee, ART, Ansel, Filmulator | **GPL** | tylko inspiracja pomysłami, kodu nie kopiujemy |
| LibRaw | LGPL/CDDL | do rozważenia przy obsłudze RAW (osobna decyzja) |
| Wyrazistość | warstwa bazowa z filtru prowadzonego (guided filter, He i in. 2010/2013) w zmniejszonej skali; lokalny kontrast = jasność − baza, waga półtonów; bez halo przy krawędziach | publikacja naukowa, własna implementacja |
| Usuń zamglenie | dark channel prior (He, Sun, Tang 2009): światło atmosferyczne z górnych 0,1% ciemnego kanału, mapa transmisji t = 1 − ω·dark, udoskonalona filtrem prowadzonym, J = (I − A)/max(t, t0) + A | publikacja naukowa, własna implementacja |
| Odszumianie kolorów | rozkład na Y/Cb/Cr, wygładzanie Cb/Cr filtrem prowadzonym z jasnością jako przewodnikiem (kolory nie wyciekają przez krawędzie), jasność nietknięta | publikacja naukowa, własna implementacja |

Zmiana tej polityki (np. przejście aplikacji na GPL, żeby móc korzystać z darktable/RawTherapee) to decyzja Jack4B, nie robimy jej sami.

## Kandydaci na kolejne kroki
HSL per kolor, tonowanie cieni i świateł (split toning, „klisza"), krzywe tonalne, dehaze (np. ciemny kanał), CLAHE.
