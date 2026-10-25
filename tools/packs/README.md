# Pomiary wariantów paczki LUT (do wyboru siatki – D8)

ΔE2000 (sRGB D65 → Lab) względem HaldCLUT 144³ we wszystkich 144³ węzłach, interpolacja trójliniowa (jak `CpuLut`). 260 filmów. Pliki paczek nie są w repo.

| Wariant | max ΔE<1 | D7 (mean<0,5 i p99<1,5) | śr. mean | max p99 | Najgorszy max | Raw | ZIP (deflate -9) |
|---|---|---|---|---|---|---|---|
| 33³ RGB8 | 5/260 | 260/260 | 0,192 | 1,360 | 6,39 (Superia 200 XPRO) | 28 030 860 B | 19 122 939 B |
| 33³ RGB16 | 4/255* | 255/255* | 0,181 | 1,349 | 6,31 | 56 061 720 B | – |
| 48³ RGB8 | 23/260 | 260/260 | 0,176 | 1,272 | 4,99 (Neopan 1600) | 86 261 760 B | – |
| 65³ RGB8 | 30/260 | 260/260 | 0,166 | 1,101 | 4,23 (Polaroid PX-70 Warm +) | 214 207 500 B | 131 146 341 B |
| 65³ RGB16 | 31/260 | 260/260 | 0,152 | 1,089 | 4,12 (Polaroid PX-70 Warm +) | 428 415 000 B | 329 379 017 B |
| 144³ natywnie RGB8 raw | 260/260 (ΔE=0) | 260/260 | 0 | 0 | 0 | 2 370 441 216 B | 1 272 784 789 B |
| 144³ natywnie PNG (HaldCLUT) | 260/260 (ΔE=0) | 260/260 | 0 | 0 | 0 | 378 138 252 B | 378 164 602 B |

\* 33³ RGB16 liczone tylko dla 255 filmów z max ≥ 1 przy RGB8.

Najgorsze 5 dla 65³ RGB8: polaroid_px_70_warm_p 4,23; polaroid_px_70_cold_p 4,03; polaroid_px_70_warm_pp 3,75; polaroid_px_70_cold 3,70; polaroid_px_70_pp 3,61.

144³ i błąd próbkowania między węzłami: w węzłach ΔE=0. Między węzłami nie ma prawdy źródłowej. Jako miarę niejednoznaczności przyjąłem różnicę tri vs tetra w środkach komórek 143³: śr. mean 0,084, max p99 0,524, max 1,307.

Skąd biorą się maxy (`worst-diagnosis.json`): źródłowe HaldCLUT-y mają skoki między sąsiednimi węzłami 144³ (max ΔE między sąsiadami: XPRO 10,1, Neopan 1600 5,5, PX-70 Cold + 4,5; mediana ≈ 0,6–0,8).
- XPRO: max w głębokich cieniach (wejście ≈ RGB 7,5,4 → wyjście 0,3,34, ciemny niebieski). Lokalnie skok ΔE do 8,8 między sąsiadami.
- Neopan: max w rogu gamutu (czerwień nasycona: R=255, G≈20–40, B≈0).
- PX-70: nasycone fiolety/niebieskie, lokalny skok ΔE 2,7–4,2.

Siatka rzadsza niż 144³ wygładza te skoki, więc max<1 dla wszystkich jest osiągalne tylko natywnie 144³.

Skrypty: `gridmeasure.py` (65³ i 144³ → `grid-65-144-stats.json`), `worstdiag.py` (→ `worst-diagnosis.json`), `photo65.py` (→ `tools/golden/compare/photos/stats-65.json`).
