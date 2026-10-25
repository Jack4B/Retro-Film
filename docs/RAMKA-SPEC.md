# Ramka i podpis — specyfikacja v2 (ostateczna)

Wszystkie wymiary są ułamkami krótszego boku `u = min(W, H)` płótna, więc ramka wygląda tak samo w każdej rozdzielczości (podgląd i eksport liczą ten sam układ).

## Płótno
- Proporcje: **1:1 (domyślna)**, 4:5, 3:2, Oryginał (proporcje zdjęcia).
- Tło: biały (domyślnie), kremowy, czarny.

## Zdjęcie
- Margines boczny i górny: parametr **0–15% `u`**, domyślnie **5%**.
- Pole zdjęcia: szerokość `W − 2·m`, wysokość `H − m − pas_podpisu`.
- Zdjęcie wpasowane w całości (contain), bez kadrowania, wyśrodkowane w polu.

## Podpis
- Pas podpisu: **dolne 20% wysokości** płótna; podpis wyśrodkowany w pasie.
- Font: Caveat (OFL), kolor do wyboru.
- Wysokość bazowa: **0,075·u**.
- Za długi tekst: czcionka zmniejsza się najwyżej do **60%** bazowej; gdy nadal nie mieści się w 90% szerokości pola zdjęcia, tekst **zawija się do 2 linii** (podział po słowach).
- Limit: **40 znaków**, licznik widoczny w polu. Placeholder: „Np. Tatry, Jezioro Bachotek”.
- Podpis nigdy nie wychodzi poza ramkę.

## Seria
- Ta sama ramka (proporcje, margines, tło, font, kolor) dla całej serii; podpis można zmienić dla pojedynczego zdjęcia.

## Bez parametrów zdjęcia
- Brak paska EXIF (ISO, przysłona, ogniskowa, czas) i brak przełącznika do niego.
