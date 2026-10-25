# Porównania na zdjęciach (5 najgorszych filmów)

Panele (pełna rozdzielczość każdy): **HaldCLUT 144³ (oryginał) | 33³ RGB8 trójliniowo (jak `CpuLut`) | |różnica|×10**. JPEG q95 (PNG za duże).
ΔE2000 (sRGB D65 → Lab) na wyjściach float. Regiony ΔE>3: siatka 3×3 kadru. Generator: `tools/photocompare.py`, dane: `stats.json`.

| Film | Zdjęcie | mean | p99 | p99,9 | max | px ΔE>2 | px ΔE>3 (%) | regiony ΔE>3 | plik (B) |
|---|---|---|---|---|---|---|---|---|---|
| colorslide/fuji_superia_200_xpro | sky | 0.11 | 0.31 | 0.993 | 5.175 | 222 | 19 (0.0005) | srodek-prawo: 1, dol-lewo: 10, dol-srodek: 1, dol-prawo: 7 | `colorslide__fuji_superia_200_xpro__sky.jpg` (4222028) |
| colorslide/fuji_superia_200_xpro | skin | 0.119 | 0.308 | 0.387 | 2.631 | 2 | 0 (0.0) | – | `colorslide__fuji_superia_200_xpro__skin.jpg` (2388348) |
| colorslide/fuji_superia_200_xpro | green | 0.373 | 4.293 | 5.234 | 5.699 | 188622 | 122890 (2.8384) | gora-lewo: 12148, gora-srodek: 27800, gora-prawo: 25112, srodek-lewo: 1214, srodek-srodek: 12689, srodek-prawo: 12697, dol-lewo: 24783, dol-srodek: 944, dol-prawo: 5503 | `colorslide__fuji_superia_200_xpro__green.jpg` (6351945) |
| bw/fuji_neopan_1600 | sky | 0.122 | 0.487 | 0.638 | 0.958 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__sky.jpg` (4604279) |
| bw/fuji_neopan_1600 | skin | 0.116 | 0.389 | 0.579 | 0.957 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__skin.jpg` (2689757) |
| bw/fuji_neopan_1600 | green | 0.09 | 0.532 | 0.781 | 1.19 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__green.jpg` (5041801) |
| negative_old/fuji_neopan_1600_p | sky | 0.132 | 0.484 | 0.594 | 0.828 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__sky.jpg` (4552679) |
| negative_old/fuji_neopan_1600_p | skin | 0.109 | 0.38 | 0.54 | 0.947 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__skin.jpg` (2643970) |
| negative_old/fuji_neopan_1600_p | green | 0.094 | 0.498 | 0.741 | 0.988 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__green.jpg` (4916983) |
| negative_old/fuji_neopan_1600_pp | sky | 0.122 | 0.451 | 0.594 | 0.937 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__sky.jpg` (4475411) |
| negative_old/fuji_neopan_1600_pp | skin | 0.101 | 0.37 | 0.552 | 0.937 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__skin.jpg` (2597810) |
| negative_old/fuji_neopan_1600_pp | green | 0.094 | 0.511 | 0.762 | 1.098 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__green.jpg` (4808050) |
| instant_consumer/polaroid_px_70_cold_p | sky | 0.314 | 1.169 | 1.447 | 1.693 | 0 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__sky.jpg` (5238630) |
| instant_consumer/polaroid_px_70_cold_p | skin | 0.252 | 1.072 | 1.557 | 2.176 | 271 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__skin.jpg` (2861258) |
| instant_consumer/polaroid_px_70_cold_p | green | 0.196 | 0.908 | 1.307 | 1.738 | 0 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__green.jpg` (6014821) |

Łącznie: 63407770 B (15 plików).

## Wnioski
- B&W Neopan (3 warianty): max < 1,2 na wszystkich zdjęciach, zero px ΔE>2 – brak bandingu.
- Polaroid PX-70 Cold +: max ≤ 2,2, zero px ΔE>3; na gradiencie nieba brak bandingu.
- Superia 200 XPRO: sky 19 px ΔE>3 (0,0005%, dół kadru – ciemny stok góry), skin 0. **green: 2,84% px ΔE>3 (p99 4,29)** – 99,9% z nich to bardzo ciemne piksele wejścia (luma < 0,15; mediana 0,04: gałęzie i cienie). Siatka 33³ nie oddaje stromej krzywej XPRO w głębokich cieniach. To jest region niskiej jasności (L* wyjścia ≈ 3,5), więc różnica jest słabo widoczna, ale realna. Flaga `approx: true` już ustawiona dla tego filmu.

## 33³ vs 65³ RGB8 na zdjęciach (stats-65.json; obrazy 65³ niecommitowane, 58 MB)

| Film | Zdjęcie | 33³ p99 / max / px>3 | 65³ p99 / max / px>3 |
|---|---|---|---|
| colorslide/fuji_superia_200_xpro | sky | 0.31 / 5.175 / 19 | 0.271 / 0.829 / 0 |
| colorslide/fuji_superia_200_xpro | skin | 0.308 / 2.631 / 0 | 0.304 / 0.613 / 0 |
| colorslide/fuji_superia_200_xpro | green | 4.293 / 5.699 / 122890 | 0.584 / 1.06 / 0 |
| bw/fuji_neopan_1600 | sky | 0.487 / 0.958 / 0 | 0.317 / 0.59 / 0 |
| bw/fuji_neopan_1600 | skin | 0.389 / 0.957 / 0 | 0.285 / 0.665 / 0 |
| bw/fuji_neopan_1600 | green | 0.532 / 1.19 / 0 | 0.352 / 0.967 / 0 |
| negative_old/fuji_neopan_1600_p | sky | 0.484 / 0.828 / 0 | 0.322 / 0.609 / 0 |
| negative_old/fuji_neopan_1600_p | skin | 0.38 / 0.947 / 0 | 0.272 / 0.633 / 0 |
| negative_old/fuji_neopan_1600_p | green | 0.498 / 0.988 / 0 | 0.33 / 0.769 / 0 |
| negative_old/fuji_neopan_1600_pp | sky | 0.451 / 0.937 / 0 | 0.284 / 0.615 / 0 |
| negative_old/fuji_neopan_1600_pp | skin | 0.37 / 0.937 / 0 | 0.267 / 0.691 / 0 |
| negative_old/fuji_neopan_1600_pp | green | 0.511 / 1.098 / 0 | 0.336 / 0.831 / 0 |
| instant_consumer/polaroid_px_70_cold_p | sky | 1.169 / 1.693 / 0 | 0.637 / 0.899 / 0 |
| instant_consumer/polaroid_px_70_cold_p | skin | 1.072 / 2.176 / 0 | 0.638 / 1.401 / 0 |
| instant_consumer/polaroid_px_70_cold_p | green | 0.908 / 1.738 / 0 | 0.615 / 1.104 / 0 |
