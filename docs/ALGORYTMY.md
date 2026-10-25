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
| Nasycenie | vibrance: plus wzmacnia głównie kolory mało nasycone, minus odbarwia równomiernie, luminancja Rec.709 | opis publiczny (Photoshop/Lightroom vibrance), własna implementacja |
| Światła i cienie | lokalna jasność otoczenia (rozmyta siatka ~512 komórek, `ToneMask`) steruje wpływem, wpływ kwadratowy | zasada „Shadow/Highlight” (publicznie opisana) |
| Blaknięcie, winieta | podniesiona czerń i przytłumiona biel; przyciemnienie rogów (d⁴) | standardowe modele |
| Ziarno | deterministyczny szum na hashu całkowitoliczbowym, stały dla obrazu, skalowany z rozmiarem, silniejszy w półtonach | własne |
| Ostrość, wyrazistość | unsharp mask na jasności z progiem szumu; wyrazistość = ta sama maska z dużym promieniem i wagą półtonów | klasyczna maska unsharp |
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

Zmiana tej polityki (np. przejście aplikacji na GPL, żeby móc korzystać z darktable/RawTherapee) to decyzja Jack4B, nie robimy jej sami.

## Kandydaci na kolejne kroki
HSL per kolor, tonowanie cieni i świateł (split toning, „klisza"), krzywe tonalne, dehaze (np. ciemny kanał), CLAHE.
