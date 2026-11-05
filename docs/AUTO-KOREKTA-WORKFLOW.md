# Autokorekta w Retro-Film — jak jej używać

Dokument aktualny względem wydań **F–M** (autokorekta) oraz **N–R** (zoom podglądu nie zmienia algorytmu auto). Ustawienia / GPU / zoom: [USTAWIENIA-I-ZOOM.md](USTAWIENIA-I-ZOOM.md).

Nazwy przycisków, suwaków i przełączników są przepisane z `strings.xml` aplikacji.

## 1. Wybór zdjęcia źródłowego

| Źródło | Kiedy | Co robi autokorekta |
|---|---|---|
| **JPEG z telefonu** (realme itp.) | Zwykłe zdjęcia | Zdjęcie ma już krzywą tonalną i wyostrzenie telefonu. Suwak **Zdejmij krzywą telefonu** częściowo cofa tę krzywą, zanim zadziała film. |
| **JPEG Ultra HDR** (z gain mapą) | Sceny z jasnym niebem, światłami lub oknem | Autokorekta odczytuje z metadanych XMP zapas jasności `HDRCapacityMax`/`GainMapMax`, w zakresie 0–6 EV. Dzięki niemu ostrożniej przyciemnia światła. |
| **DNG (RAW)** | Kiedy liczy się jakość albo telefon „przepalił” JPEG | Po komunikacie „Wczytywanie RAW…” autokorekta odczytuje z DNG tag `BaselineExposure`. Używa go jako podpowiedzi ekspozycji, gdy sam pomiar jest niepewny. |

**Chip „HDR Wł./Wył.”** (opis: *HDR ze zdjęcia (gain mapa Ultra HDR) dla tego zdjęcia*):
- **Wł.**: używa gain mapy ze zdjęcia. Zapis idzie wtedy w 4:2:0 i bez ramki (zgodnie z opisem w Ustawieniach).
- **Wył.**: zdjęcie otwiera się i zapisuje szybciej, można dodać ramkę i zapisać w 4:4:4.
- Domyślne ustawienie dla nowych zdjęć zmienisz w Ustawienia → Zdjęcia → **HDR ze zdjęcia**.
- Zalecenie: zostaw **Wył.** do zwykłej pracy z filmem i ramką. Włącz **Wł.** tylko wtedy, gdy chcesz zachować efekt HDR na ekranie HDR.

## 2. „Auto” czy „Przygotuj pod film”

Oba przyciski są w zakładce **Korekty**.

- **Auto: popraw światło i kolor** daje pełną propozycję: ekspozycję, kontrast, światła, cienie, nasycenie i balans bieli. Używaj, gdy chcesz ładne zdjęcie bez filmu albo z filmem na niskiej mocy. Przycisk zmienia się wtedy w **Cofnij auto (wykryto: …)** i pokazuje rozpoznaną scenę.
- **Przygotuj pod film (balans bieli, ekspozycja)** zmienia tylko balans bieli i ekspozycję. Kontrast i nasycenie najwyżej delikatnie zdejmuje, a światła i cienie zostawia na 0. Film (LUT) dostaje wtedy neutralne wejście, czyli to, czego oczekuje. **To zalecany tryb przy pracy z filmami.** Cofasz go przyciskiem **Cofnij przygotowanie pod film**.

## 3. Suwak „Zdejmij krzywą telefonu”

Suwak jest widoczny pod przyciskiem przygotowania, gdy przygotowanie jest włączone, **także po ręcznej zmianie Ekspozycji czy innych suwaków** (od wydania M; wcześniej ręczna zmiana chowała suwak i blokowała zmianę). Wartość domyślna to **50%**; **100% = pełne zdjęcie krzywej**.

- **Bez ręcznych zmian** przesunięcie suwaka przelicza propozycję (ekspozycję i balans bieli) na zdjęciu *po* zdjęciu krzywej z nową siłą, czyli na tym, co dostanie film.
- **Po ręcznych zmianach** suwak zmienia tylko zdjęcie krzywej; Twoja ekspozycja i reszta zostają.

Działanie: telefon podbija kontrast półtonów krzywą w kształcie litery S. Suwak cofa tę krzywą, więc film dostaje bardziej „płaskie” wejście. Szarość 18% oraz czerń i biel prawie się nie zmieniają.

- **0%**: brak inwersji. Kontrast może tylko lekko spaść z propozycji.
- **50%**: umiarkowane spłaszczenie. Zalecane dla **JPEG z realme i innych telefonów**.
- **100%**: maksymalne spłaszczenie (siła krzywej 0,3). Przydaje się, gdy JPEG jest bardzo kontrastowy (mocne słońce, tryb AI).
- **DNG**: zalecane **0–25%**. Obraz z RAW nie ma krzywej z procesora telefonu, więc mocna inwersja tylko go spłaszczy.

Gdy inwersja jest większa od 0, suwak **Kontrast** z przygotowania zostaje na 0. Za kontrast odpowiada wtedy film.

### Ekspozycja z „Przygotuj pod film” a twarze (wydanie M)

- Ekspozycja jest mierzona po zdjęciu krzywej telefonu przy bieżącej sile suwaka (wcześniej na surowym JPEG-u, niezależnie od suwaka).
- Gdy wykrywacz znajdzie twarze, propozycja ma górną granicę: 75. percentyl najjaśniejszego kanału w ramkach twarzy po korekcie nie przekroczy ok. sRGB 0,81 (liniowo 0,62) ani ramienia filmu. Przepalone bloki twarzy liczą się jako biel, więc przepalonej twarzy auto już nie rozjaśnia.
- Wcześniej przy filmach o jasnej szarości docelowej (do 0,36 zamiast 0,18) cel jasności skóry rósł razem z nią, a ograniczenie świateł patrzyło tylko na luminancję nieprzepalonych bloków (do 1,0): twarz mogła dostać do +0,9 EV i czerwony kanał 255.

## 4. Kolejność pracy

1. Otwórz zdjęcie. Przy Ultra HDR zdecyduj o chipie **HDR**.
2. W zakładce **Film** wybierz film i ustaw **Moc filmu**.
3. W zakładce **Korekty** dotknij **Przygotuj pod film (balans bieli, ekspozycja)**.
4. Ustaw suwak **Zdejmij krzywą telefonu** (patrz punkt 3).
5. Dostrój suwaki **Ekspozycja**, **Temperatura** i **Odcień (zielony ↔ magenta)**, a potem **Światła**, **Cienie**, **Biele** i **Czernie**.
6. W zakładkach **Efekty**, **Kadr** i **Ramka** dokończ zdjęcie i zapisz.

Ważne:
- **Ręczna zmiana Temperatury lub Odcienia zeruje ukryte przesunięcie bieli.** Autokorekta potrafi skorygować światło leżące poza zakresem tych dwóch suwaków (adaptacja Bradforda, na przykład zielonkawe LED). Takiej korekty nie widać na suwakach. Gdy ruszysz Temperaturę lub Odcień, liczy się już tylko to, co pokazują suwaki.
- Gdy **zmienisz film po przygotowaniu i niczego nie edytowałeś ręcznie**, propozycja przelicza się sama pod nowy film. Jeśli już coś poprawiałeś, Twoje ustawienia zostają. Żeby dostać propozycję pod nowy film, cofnij przygotowanie i dotknij przycisku jeszcze raz.

## 5. Szarość docelowa dla każdego filmu

Każdy film inaczej oddaje szarość: jedne są jaśniejsze, inne ciemniejsze. Aplikacja przepuszcza przez LUT filmu rampę szarości i znajduje jasność wejścia, przy której wyjście daje 18% szarości. Sprawdza też, gdzie zaczyna się ramię (roll-off) świateł. Autokorekta celuje w tę wartość zamiast w stałe 0,18. Dlatego po zmianie filmu ekspozycja z autokorekty może się przesunąć: to zamierzone. Kalibracja trwa ułamki milisekundy i jest zapamiętywana.

## 6. Presety i ulubione

- **Presety** (zakładka Film): plus z opisem *Zapisz jako preset: film, moc i wszystkie korekty*. Preset zapamiętuje także ukryte przesunięcie bieli i wartość **Zdejmij krzywą telefonu**, więc odtwarza wynik dokładnie.
- Pamiętaj, że preset zapisuje **wartości dla konkretnego zdjęcia**. Na innym zdjęciu lepiej użyć presetu dla filmu, a potem znów dotknąć **Przygotuj pod film**.
- **Ulubione**: gwiazdka przy filmie, albo przytrzymanie filmu w bibliotece.

## 7. Zapis

| Cel | Ustawienia |
|---|---|
| Najlepsza jakość, druk, archiwum | **Format pliku**: JPEG, **Kolor w pliku**: **4:4:4 (pełny kolor)**, jakość 95–100% |
| Dalsza obróbka | **PNG 16** (bez strat, około 10 bitów precyzji bez ramki i bez zmiany rozmiaru; bez EXIF) |
| Mały plik | **HEIC** (8 bitów, bez EXIF) albo JPEG **4:2:0 (mniejszy plik)** |
| Ekran telefonu lub nowego monitora | **Przestrzeń barw**: **Display P3 (szerszy gamut)** |
| Strony WWW, starsze ekrany, druk w zakładzie | **sRGB** |

**Dane aparatu (EXIF, bez GPS)** to opcja zapisu tylko dla JPEG.

## 8. Rozwiązywanie problemów

- **Za ciemno lub za jasno**: popraw suwak **Ekspozycja** o ±0,3 EV. Pamiętaj, że cel zależy od filmu (punkt 5). Przy DNG z niepewnym pomiarem decyduje `BaselineExposure`.
- **Zabarwienie pod LED lub żarówką**: najpierw **Przygotuj pod film**. Zwykle wystarcza, bo ukryte przesunięcie bieli usuwa zieleń LED. Jeśli trzeba poprawić, rób to drobnymi ruchami suwaka **Temperatura**, pamiętając, że zeruje on ukryte przesunięcie (punkt 4). Przy bardzo jednobarwnej scenie, na przykład zachodzie słońca, autokorekta celowo koryguje słabiej.
- **Prześwietlone sceny** (okno, niebo): suwakiem **Światła** w dół. Jeśli zdjęcie jest w DNG albo Ultra HDR, otwórz to źródło: autokorekta wykorzysta zapas jasności. Przy JPEG bez HDR przepalonych pikseli nie da się odzyskać.
- **Skóra**: wykrycie twarzy włącza ważenie ekspozycji na twarz i ochronę odcienia skóry przy balansie bieli. Bez wykrytej twarzy kolor skóry nie wpływa na ekspozycję. Jeśli skóra jest zbyt różowa lub żółta, popraw ją suwakiem **Odcień (zielony ↔ magenta)**.
- Wszystko możesz cofnąć przyciskami **Cofnij auto** lub **Cofnij przygotowanie pod film** oraz **Cofnij**. **Wyzeruj korekty** czyści wszystkie korekty.

## 9. Znane ograniczenia

- Przepalone próbki są **liczone** w pomiarze ekspozycji (w kluczu jasności). Przy dużych przepalonych obszarach autokorekta może lekko przyciemnić zdjęcie.
- Na syntetycznym teście ColorChecker przy +2 EV i świetle żarowym lub LED błąd koloru szarości dochodzi do ΔE00 ≈ 3,6.
- HDR jest obsługiwany **tylko przez metadane** (BaselineExposure, HDRCapacityMax). Aplikacja nie dekoduje obrazu w trybie float.
- Autokorekta mierzy siatkę 128 próbek na dłuższym boku, więc bardzo małe obiekty mają mały wpływ.
- Wszystko działa offline. Zdjęcia nie opuszczają telefonu.

## 10. Logi, diagnostyka i kopia zapasowa

- **Ustawienia → Diagnostyka → Logi czasów**: włącz, zrób edycję lub zapis, potem **Eksportuj log czasów** (`retrofilm-czasy-RRRR-MM-DD.csv`) albo **Udostępnij log czasów**. Wpis `auto_suggest` = czas autokorekty; w logu zapisu widać też `save_backend` / `save_backend_bench` (gdy włączysz GPU). **Wyczyść log** usuwa bufor. Dane zostają w telefonie.
- **Ustawienia → Diagnostyka → Zapis na GPU (eksperymentalny)**: **domyślnie wyłączony** (L). Na Mali zwykle i tak wraca CPU po porównaniu z wieloma wątkami. Nie zmienia wyniku autokorekty – tylko backend LUT przy zapisie. Szczegóły: [USTAWIENIA-I-ZOOM.md](USTAWIENIA-I-ZOOM.md).
- **Ustawienia → Kopia zapasowa → Eksportuj kopię**: ustawienia, presety (z wartościami autokorekty) i ulubione w `retrofilm-kopia-RRRR-MM-DD.json`. **Przywróć z pliku**: **Scal** albo **Zastąp**.

## 11. Zoom podglądu a twarze / wyostrzanie (O–R)

Autokorekta i suwaki nie zależą od zoomu. Przy **wyostrzaniu świadomym treści** i pinch ≥ 1,5× **bez ramki** podgląd liczy wycinek ROI; twarze brane są z pełnego kadru i mapowane do wycinka (R), żeby mapa twarzy nie była pusta/zła na detalu. Z **włączoną ramką** zoom Compose skaluje całą kompozycję (P/Q). Double-tap: jedno lądowanie (Q). Pełna tabela: [USTAWIENIA-I-ZOOM.md](USTAWIENIA-I-ZOOM.md).
