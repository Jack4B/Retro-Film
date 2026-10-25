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
- Podpis nigdy nie wychodzi poza ramkę. Reguła gwarancji (`FrameGeometry.fitSignature`): jeśli 2 linie przy 60% nadal przekraczają 90% szerokości pola zdjęcia albo wysokość pasa podpisu (lub łamanie daje więcej niż 2 linie, np. bardzo długie słowo), czcionka zmniejsza się dalej krokami ×0,9, aż blok tekstu zmieści się w pasie (granica bezpieczeństwa: 1% bazowej). Blok jest wyśrodkowany poziomo na płótnie i pionowo w pasie (`FrameGeometry.signatureBounds`).

## Seria
- Ta sama ramka (proporcje, margines, tło, font, kolor) dla całej serii; podpis można zmienić dla pojedynczego zdjęcia.

## Bez parametrów zdjęcia
- Brak paska EXIF (ISO, przysłona, ogniskowa, czas) i brak przełącznika do niego.
