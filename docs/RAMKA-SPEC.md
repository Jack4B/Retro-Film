# Ramka i podpis — specyfikacja v2 (ostateczna)

Wszystkie wymiary są ułamkami krótszego boku `u = min(W, H)` płótna, więc ramka wygląda tak samo w każdej rozdzielczości (podgląd i eksport liczą ten sam układ).

## Włączanie
- Ramka jest **opcjonalna i domyślnie wyłączona** — podstawową funkcją aplikacji są filtry LUT. Włącza się ją w Edytorze albo przy Eksporcie.
- Podpis jest opcjonalny (domyślnie wyłączony po włączeniu ramki).
- Trzy stany: bez ramki · ramka bez podpisu · ramka z podpisem.

## Płótno
- Proporcje: **1:1 (domyślna)**, 4:5, 3:2, Oryginał (proporcje zdjęcia).
- Tło: biały (domyślnie), kremowy, czarny.

## Zdjęcie
- Margines boczny i górny: parametr **0–15% `u`**, domyślnie **5%**.
- **Podpis wyłączony:** margines równy ze wszystkich stron (`m·u`), bez pasa podpisu; pole zdjęcia `W − 2·m` × `H − 2·m`.
- **Podpis włączony:** pole zdjęcia `W − 2·m` × `H − m − pas_podpisu`.
- Zdjęcie wpasowane w całości (contain), bez kadrowania, wyśrodkowane w polu.

## Podpis
- Pas podpisu (tylko gdy podpis włączony): **dolne 20% wysokości** płótna; podpis wyśrodkowany w pasie.
- Font: Caveat (OFL), kolor do wyboru.
- Wysokość bazowa: **0,075·u**.
- Za długi tekst: czcionka zmniejsza się najwyżej do **60%** bazowej; gdy nadal nie mieści się w 90% szerokości pola zdjęcia, tekst **zawija się do 2 linii** (podział po słowach).
- Limit: **40 znaków**, licznik widoczny w polu. Placeholder: „Np. Tatry, Jezioro Bachotek”.
- Podpis nigdy nie wychodzi poza ramkę. Reguła gwarancji (`FrameGeometry.fitSignature`): jeśli 2 linie przy 60% nadal przekraczają 90% szerokości pola zdjęcia albo wysokość pasa podpisu (lub łamanie daje więcej niż 2 linie, np. bardzo długie słowo), czcionka zmniejsza się dalej krokami ×0,9, ostatnia próba dokładnie na **progu czytelności = 40% bazowej (0,03·u)**. Poniżej progu czcionka się nie zmniejsza. Blok jest wyśrodkowany poziomo na płótnie i pionowo w pasie (`FrameGeometry.signatureBounds`).
- **Jawny wynik `fits`:** `SignatureFit.fits = true` — podpis mieści się w pasie przy rozmiarze ≥ 40% bazowej; `fits = false` — nie mieści się nawet na progu (zwracany rozmiar = próg). Nigdy nie ma „cichego” mikrotekstu.
- **Gdy `fits = false`:**
  - podgląd **nie rysuje** podpisu (pas zostaje pusty);
  - ekran Ramka pokazuje pod podglądem komunikat błędu „Podpis za długi dla tej ramki – skróć go lub zmniejsz grubość ramki” (TalkBack: opis błędu + region live, informacja, że podpis nie zostanie wyeksportowany). Mniejsza grubość ramki = szersze pole zdjęcia = więcej miejsca na podpis;
  - **eksport** musi sprawdzić stan funkcją `checkSignatureFit(...)` / `rememberSignatureFitStatus(...)` (`feature:editor`, `SignatureFitStatus.None | Fits | TooLong`) i przy `TooLong` zablokować eksport zdjęcia z podpisem albo ostrzec użytkownika — nigdy nie renderować podpisu wychodzącego poza pas.
- Pomiar (Caveat, Robolectric): najgorszy przypadek 40×„W” na 4:5 — 60% bazowej przy marginesie 0% i 5%, 54% przy 15%; zawsze `fits = true`. Przy limicie 40 znaków stan `TooLong` praktycznie nie występuje; to zabezpieczenie na tekst spoza pola (np. import) i przyszłe zmiany fontu/układu.

## Seria
- Ta sama ramka (proporcje, margines, tło, font, kolor) dla całej serii; podpis można zmienić dla pojedynczego zdjęcia.

## Bez parametrów zdjęcia
- Brak paska EXIF (ISO, przysłona, ogniskowa, czas) i brak przełącznika do niego.

## Układ „TightFrame” (aplikacja, od alpha.3)
- Góra i boki: równy margines `m·u`. Z podpisem tylko dół jest szerszy o `0,07·u` (pas podpisu = `m + 0,07·u`, podpis wyśrodkowany w pasie); wcześniej pas to 20% wysokości płótna, co przy „Oryginał” dawało grube boki.
- „Oryginał”: płótno = zdjęcie + marginesy (skalowane do dłuższego boku zdjęcia), bez pustych pól. Stałe proporcje (1:1, 4:5, 3:2) bez zmian, zdjęcie wpasowane w pole.
- Implementacja: `core:frame` `TightFrame`; starszy `FrameGeometry.layout` zostaje dla podglądu w `feature:editor`.
