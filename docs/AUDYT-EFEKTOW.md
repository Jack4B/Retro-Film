# Audyt efektów – wydanie I

| Efekt | Przed (H) | Po (I) | Uzasadnienie |
|---|---|---|---|
| Ziarno | szum kafelkowy w gammie, maska luminancji | bez zmian (współrzędne globalne w kaflach 2D) | już zgodne z filmem; poprawka: identyczny wynik niezależnie od podziału na kafle |
| Halacja | brak | nowa (`Halation.kt`): próg 0,55 w świetle liniowym, podwójny box blur r=min/28, odcień (1; 0,32; 0,08), mieszanie „screen” w liniowym | prawdziwa halacja to światło odbite od podłoża kliszy – czerwono-pomarańczowa poświata wokół świateł |
| Winieta | cos⁴, eliptyczna (dopasowana do proporcji) | cos⁴ kołowa (d względem połowy przekątnej), mnożnik w świetle liniowym | obiektyw daje spadek kołowy; eliptyczna winieta w kadrze panoramicznym wyglądała sztucznie |
| Wyostrzanie | tylko luminancja | bez zmian; sumy biegnące w double | bit-exact przy kaflach 2D (wcześniej 1 LSB różnicy na szwach) |
| Deband | dither przed LUT | bez zmian, współrzędne globalne | brak szwów w kaflach |
| Fade | uniesienie czerni | bez zmian | poprawne |
| Ramka | Lanczos całego zdjęcia przy różnicy zaokrąglenia | kopia 1:1 gdy okno różni się ≤ 2 px | ~480 ms → koszt kopiowania (do potwierdzenia na telefonie) |
| Dither TPDF | globalne współrzędne | bez zmian | — |

## Sprawdzone i odrzucone
- Wykluczenie przepalonych bloków z `AutoEnhance.key()` – test ColorChecker (A i LED +2 EV) przekraczał próg (rezyduum ~0,83 EV). Przepalone bloki są już pomijane w p995 i w pewności. Możliwa kontynuacja: pomijać tylko bloki przepalone w większości.

## Nie zrobione w I
- Pełna ścieżka zapisu GPU (AGSL/GLES) dla wszystkich efektów wykończenia: LUT **może** iść na GPU (Agsl/GlesLutEngine, test GpuVsCpu), ale od L przełącznik diagnostyki jest **domyślnie OFF** (Mali: bench zwykle wybiera CPU×wątki). Wyostrzanie, deband, halacja i ziarno są nielokalne i zostają na CPU (kafle 2D). Port reszty na GPU – osobne wydanie.
