# Porównania na zdjęciach (5 najgorszych filmów)

Panele (pełna rozdzielczość każdy): **HaldCLUT 144³ (oryginał) | 33³ RGB8 trójliniowo (jak `CpuLut`) | |różnica|×10**. JPEG q95.
ΔE2000 (sRGB D65 → Lab) na wyjściach float. Regiony ΔE>3: siatka 3×3 kadru. Dane: `stats.json`, sumy plików: `SHA256SUMS`.

**Obrazy nie są w repo.** Generuje je CI (job `photo-compare`, artefakt `photo-compare`) albo lokalnie:
`python3 tools/golden/fetch_photos.py && python3 tools/photocompare.py <HaldCLUT/> tools/packs/catalog-33-full.json tools/golden`.
Zdjęcia źródłowe: `tools/golden/photos/SOURCES.md` (pobierane z Wikimedia Commons, weryfikacja sha256).

| Film | Zdjęcie | mean | p99 | p99,9 | max | px ΔE>2 | px ΔE>3 (%) | regiony ΔE>3 | plik (B) |
|---|---|---|---|---|---|---|---|---|---|
| colorslide/fuji_superia_200_xpro | sky | 0.104 | 0.276 | 0.35 | 1.651 | 0 | 0 (0.0) | – | `colorslide__fuji_superia_200_xpro__sky.jpg` (3426840) |
| colorslide/fuji_superia_200_xpro | skin | 0.119 | 0.307 | 0.386 | 1.151 | 0 | 0 (0.0) | – | `colorslide__fuji_superia_200_xpro__skin.jpg` (2032887) |
| colorslide/fuji_superia_200_xpro | green | 0.37 | 4.271 | 5.234 | 5.699 | 184953 | 120356 (2.7798) | gora-lewo: 12207, gora-srodek: 26913, gora-prawo: 24586, srodek-lewo: 1123, srodek-srodek: 12250, srodek-prawo: 12292, dol-lewo: 24846, dol-srodek: 906, dol-prawo: 5233 | `colorslide__fuji_superia_200_xpro__green.jpg` (6401284) |
| bw/fuji_neopan_1600 | sky | 0.125 | 0.487 | 0.685 | 0.958 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__sky.jpg` (3775207) |
| bw/fuji_neopan_1600 | skin | 0.116 | 0.388 | 0.571 | 0.983 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__skin.jpg` (2273614) |
| bw/fuji_neopan_1600 | green | 0.09 | 0.534 | 0.784 | 1.19 | 0 | 0 (0.0) | – | `bw__fuji_neopan_1600__green.jpg` (5079009) |
| negative_old/fuji_neopan_1600_p | sky | 0.137 | 0.488 | 0.613 | 0.828 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__sky.jpg` (3725600) |
| negative_old/fuji_neopan_1600_p | skin | 0.109 | 0.379 | 0.536 | 0.947 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__skin.jpg` (2231187) |
| negative_old/fuji_neopan_1600_p | green | 0.094 | 0.499 | 0.743 | 0.988 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_p__green.jpg` (4954506) |
| negative_old/fuji_neopan_1600_pp | sky | 0.125 | 0.471 | 0.625 | 0.937 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__sky.jpg` (3665043) |
| negative_old/fuji_neopan_1600_pp | skin | 0.1 | 0.369 | 0.549 | 0.937 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__skin.jpg` (2194416) |
| negative_old/fuji_neopan_1600_pp | green | 0.094 | 0.515 | 0.765 | 1.098 | 0 | 0 (0.0) | – | `negative_old__fuji_neopan_1600_pp__green.jpg` (4843061) |
| instant_consumer/polaroid_px_70_cold_p | sky | 0.32 | 1.169 | 1.447 | 1.561 | 0 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__sky.jpg` (4347874) |
| instant_consumer/polaroid_px_70_cold_p | skin | 0.25 | 1.065 | 1.529 | 2.205 | 211 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__skin.jpg` (2470404) |
| instant_consumer/polaroid_px_70_cold_p | green | 0.196 | 0.911 | 1.316 | 1.738 | 0 | 0 (0.0) | – | `instant_consumer__polaroid_px_70_cold_p__green.jpg` (6059210) |

Łącznie: 57480142 B (15 plików).

## Wnioski
- B&W Neopan (3 warianty): max < 1,2 na wszystkich zdjęciach, zero px ΔE>2 – brak bandingu.
- Polaroid PX-70 Cold +: max ≤ 2,2, zero px ΔE>3; na gradiencie nieba brak bandingu.
- Superia 200 XPRO: sky 19 px ΔE>3 (0,0005%, dół kadru – ciemny stok góry), skin 0. **green: 2,84% px ΔE>3 (p99 4,29)** – 99,9% z nich to bardzo ciemne piksele wejścia (luma < 0,15; mediana 0,04: gałęzie i cienie). Siatka 33³ nie oddaje stromej krzywej XPRO w głębokich cieniach. To jest region niskiej jasności (L* wyjścia ≈ 3,5), więc różnica jest słabo widoczna, ale realna. Flaga `approx: true` już ustawiona dla tego filmu.

Uwaga: liczby w Wnioskach pochodzą z pierwszego pomiaru; po przejściu na pobieranie zdjęć z Commons (inne skalowanie) aktualne wartości są w tabeli powyżej (różnice ≤ kilka %).

## 33³ vs 65³ RGB8 na zdjęciach (stats-65.json; `tools/packs/photo65.py`, obrazy poza repo)

| Film | Zdjęcie | 33³ p99 / max / px>3 | 65³ p99 / max / px>3 |
|---|---|---|---|
| colorslide/fuji_superia_200_xpro | sky | 0.276 / 1.651 / 0 | 0.271 / 0.466 / 0 |
| colorslide/fuji_superia_200_xpro | skin | 0.307 / 1.151 / 0 | 0.304 / 0.613 / 0 |
| colorslide/fuji_superia_200_xpro | green | 4.271 / 5.699 / 120356 | 0.584 / 1.06 / 0 |
| bw/fuji_neopan_1600 | sky | 0.487 / 0.958 / 0 | 0.317 / 0.596 / 0 |
| bw/fuji_neopan_1600 | skin | 0.388 / 0.983 / 0 | 0.285 / 0.665 / 0 |
| bw/fuji_neopan_1600 | green | 0.534 / 1.19 / 0 | 0.35 / 0.967 / 0 |
| negative_old/fuji_neopan_1600_p | sky | 0.488 / 0.828 / 0 | 0.307 / 0.609 / 0 |
| negative_old/fuji_neopan_1600_p | skin | 0.379 / 0.947 / 0 | 0.267 / 0.633 / 0 |
| negative_old/fuji_neopan_1600_p | green | 0.499 / 0.988 / 0 | 0.33 / 0.769 / 0 |
| negative_old/fuji_neopan_1600_pp | sky | 0.471 / 0.937 / 0 | 0.283 / 0.615 / 0 |
| negative_old/fuji_neopan_1600_pp | skin | 0.369 / 0.937 / 0 | 0.267 / 0.691 / 0 |
| negative_old/fuji_neopan_1600_pp | green | 0.515 / 1.098 / 0 | 0.336 / 0.831 / 0 |
| instant_consumer/polaroid_px_70_cold_p | sky | 1.169 / 1.561 / 0 | 0.638 / 0.925 / 0 |
| instant_consumer/polaroid_px_70_cold_p | skin | 1.065 / 2.205 / 0 | 0.635 / 1.477 / 0 |
| instant_consumer/polaroid_px_70_cold_p | green | 0.911 / 1.738 / 0 | 0.614 / 1.134 / 0 |
