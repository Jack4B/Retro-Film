# Ustawienia, diagnostyka i zoom (stan po wydaniu S)

Krótki opis **tego, co jest w aplikacji** na gałęzi `test` po `v-test-2026-11-06-S`. Nie obejmuje funkcji niewydanych. Nazwy z `strings.xml`.

Powiązane: [CHANGELOG-TEST-WYDANIA.md](CHANGELOG-TEST-WYDANIA.md), [ZIARNO.md](ZIARNO.md), [AUTO-KOREKTA-WORKFLOW.md](AUTO-KOREKTA-WORKFLOW.md), [PLAN-WYDANIE-L.md](PLAN-WYDANIE-L.md)–[S.md](PLAN-WYDANIE-S.md), [RAMKA-SPEC.md](RAMKA-SPEC.md).

## Ustawienia → Zdjęcia → Tryb ramki (K)

| Opcja | Zachowanie przy zapisie |
|---|---|
| **Ramka na zewnątrz** (domyślnie) | Zdjęcie 1:1, plik rośnie o margines – najszybsze. |
| **Oryginalny rozmiar pliku** | Zdjęcie zmniejszane do okna ramki (Lanczos przed LUT), dłuższy bok pliku = dłuższy bok oryginału. |

Dotyczy **zapisu** z włączoną ramką w edytorze. Geometria / proporcje ramki: [RAMKA-SPEC.md](RAMKA-SPEC.md).

## Ustawienia → Diagnostyka

Wszystko lokalnie; nic nie jest wysyłane z telefonu.

| Element | Domyślnie | Co robi |
|---|---|---|
| **Logi czasów** | Wył. | Bufor ~200 pomiarów (`open_*`, `preview`, `save_*`, `auto_suggest`, …). Wpis `last_crash` może pojawić się także przy wyłączonych logach. |
| **Eksportuj / Udostępnij log czasów** | — | CSV `retrofilm-czasy-…` (aktywne gdy są wpisy). |
| **Wyczyść log** | — | Kasuje bufor w pamięci. |
| **Zapis na GPU (eksperymentalny)** | **Wył.** (L) | Tylko etap LUT przy zapisie (AGSL/GLES). Przy pierwszym zapisie z GPU: probe vs CPU, potem bench `gpu_ms × wątki` vs CPU – na Mali zwykle zostaje CPU. AGSL po błędzie formatu 22 → `skipAgsl` + DataStore (O). Efekty sąsiedztwa (wyostrzanie, ziarno, …) zawsze na CPU w pasach. |

Kopia zapasowa (osobna sekcja): ustawienia / presety / ulubione → JSON; Scal albo Zastąp.

## Zoom podglądu (O → R)

Progi: detail od **1,5×**, double-tap **2,5×**, max **6×** (`ZoomState`).

| Kontekst | Zachowanie |
|---|---|
| **Bez ramki**, pinch ≥ 1,5× | **ROI**: liczony widoczny wycinek (+halo), max bok 1280. `previewBase` w cache = **1280**; źródło cropu lokalnie do 2560 (R – bez pełnego 2560 w cache). |
| **Z włączoną ramką** | ROI **wyłączone** (P). Compose skaluje bitmapę po `FrameRenderer` (zdjęcie+ramka razem). Detail zostaje na 1280 (Q). |
| **Double-tap bez ramki** | `awaitingRoi`: brak skoku 2,5× na pełnym kadrze; jeden skok gdy wycinek gotowy (Q). |
| **Po ROI** | Gesty **live** względem bazy wycinka (R) – pan/pinch bez czekania na rebuild; rebuild przy większej zmianie viewportu. |
| **Wyostrzanie świadome treści przy ROI** | Twarze z **pełnego kadru**, mapowane do wycinka (R). |
| **OOM** | Catch w `refreshPreview` → fallback 1280 (N); limity `MAX_CELLS` / `REDUCE_GROUPS_HEAVY` (O/R). |

Film / LUT / kolor: bez zmian w N–R (tylko ścieżka podglądu / pamięć / UI zoom).

## Wykończenie → Ziarno (S)

Suwaki **Ziarno** i **Rozmiar ziarna** (zakładka Wykończenie → Klisza):

- Ziarno domyślnie **wyłączone** (0). Wybór filmu **nie** włącza suwaka — tylko dobiera profil emulsji, gdy użytkownik sam włączy ziarno.
- Przy **100%** amplituda = karta techniczna wybranego filmu (`amount · σ(gEq)`, obwiednia Selwyna).
- Przy rozmiarze **50%** plamka = `grainUm` z profilu na klatce 35 mm.
- Warianty LUT `+`/`++` dziedziczą ziarno rodzica. B&W: tylko jasność. Instant: duże miękkie chmury barwnika.

Pełna tabela emulsji, mostki RMS/PGI/Fuji ×1,8 i testy: [ZIARNO.md](ZIARNO.md).
