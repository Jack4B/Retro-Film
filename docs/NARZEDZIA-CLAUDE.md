# Narzędzia sesji Claude Code (Ponytail i Graphify)

Konfiguracja w repozytorium: `.claude/settings.json` (plugin Ponytail) i `.claude/hooks/session-start.sh` (instalacja
na starcie sesji w chmurze). Hook działa tylko w sesjach zdalnych i jest powtarzalny. **`AGENTS.md` ma pierwszeństwo**
przed zaleceniami narzędzi (commity `[Jack4B]` bez `Co-Authored-By`, lokalnie tylko ktlint i detekt, testy na CI, mało
większych PR-ów, zasada kluczowa nr 1: jakość obrazu przed krótkością kodu).

Agent Skills (addyosmani) był zainstalowany i **wyłączony 2026-10-28**: dokładał ok. 3,7 tys. tokenów do każdej sesji,
a większość jego procesu (plan, review, testy) i tak robimy według `AGENTS.md`. Przywrócenie: dodać marketplace
`addyosmani/agent-skills` i `agent-skills@addy-agent-skills` do `settings.json` i hooka.

## Ponytail (ok. 0,6 tys. tokenów w sesji)

Zmusza do najmniejszej kompletnej zmiany: najpierw to, co już jest w projekcie, potem stdlib/platforma, bez zbędnych
abstrakcji; na końcu odpowiedzi jedna linijka „czego nie sprawdzono i jakie ryzyko”. Tryby: `/ponytail lite|full|ultra`,
wyłączenie: „stop ponytail”.

**Gdzie pomaga u nas:**

- **Zmiany w UI i stanie** (`MainState`, `EditBook`, `SeriesFrame`, panele Compose): reuse istniejących klas zamiast
  nowych; tak powstały ostatnie zmiany serii (ramka w `PhotoEdit` zamiast osobnego mechanizmu).
- **Przegląd przed PR:** `/ponytail-review` na większej zmianie (np. etap D2 Dodge & Burn): logika, ryzyko, czy każda
  linia jest potrzebna, czy ryzykowny kod ma test.
- **Sprzątanie:** `/ponytail-audit` co jakiś czas (np. przed 1.2.0): martwy kod i duplikaty (np. ścieżka `applyLegacy`
  trzymana dla testów, tetrahedralny LUT poza domyślną drogą); `/ponytail-debt` zbiera komentarze `shortcut:`, dobry
  początek planowania (np. w sobotę); `/ponytail-gain` to tylko ciekawostka.

**Na co uważać:**

- **Algorytmy obrazu** (ziarno, światła, wyostrzanie, Dodge & Burn): tu liczy się jakość (zasada nr 1), a nie najkrótszy
  kod; „stdlib najpierw” nie ma zastosowania do filtrów. Przy takiej pracy przełącz na `/ponytail lite` albo napisz
  „normal mode”.
- Ponytail „nigdy nie tnie” walidacji, dostępności i bezpieczeństwa; u nas dochodzą twarde reguły z `AGENTS.md`: golden
  testy silnika, usuwanie GPS przy eksporcie, UI po polsku, cele ≥ 48 dp, rosnący `versionCode`. Nie wolno ich
  traktować jako „zbędnych” przy cięciu kodu.
- Nowa nietrywialna logika dostaje jeden mały test (tak jak dotąd).

## Graphify (mapa wiedzy; kod analizowany lokalnie drzewem składni, w tym Kotlin)

Buduje graf zależności projektu, żeby pytać o architekturę zamiast grepować. Graf trafia do `graphify-out/` (w
`.gitignore`).

**Jak zacząć:** nic nie trzeba robić. Hook startu sesji w chmurze buduje mapę sam (`tools/graphify_build.py`: wszystkie
moduły, tylko kod `main`, bez testów, obrazów i dokumentów; analiza lokalna, kilka sekund, bez tokenów), więc od
pierwszej wiadomości można pytać o architekturę. Ręczna odbudowa po zmianach w trwającej sesji (z korzenia repo):
`"$(head -1 "$(command -v graphify)" | tr -d '#!')" tools/graphify_build.py`. Nie używaj `/graphify app/src`: wykrywa
260 miniatur filmów i puszcza je do kosztownej analizy przez model.

**Wskazówka:** nazwa występująca w kilku plikach jest niejednoznaczna (np. `ToneMask`); podaj `ścieżka::symbol`, np.
`graphify explain "app/src/main/kotlin/app/retrofilm/pipeline/ToneMask.kt::ToneMask"`.

**Najlepsze użycie u nas:**

- **Przed zmianą w potoku obrazu:** `graphify path "ExportPipeline" "FilmGrain"` pokazuje drogę od eksportu do ziarna;
  `graphify explain "StripWorker"` opisuje węzeł i jego sąsiadów (z `StripPipeline`, `Planes`, `Adjustments`).
- **Przed refaktorem przekrojowym** (jak ramka na zdjęcie): „co zależy od `frame`/`PhotoEdit`/`MainState`”, żeby
  niczego nie zgubić (nasza zasada „nie usuwaj funkcjonalności”).
- **Planowanie nowych funkcji** (Dodge & Burn): jak `ToneMask`, `StripAnalysis` i `StripPlans` łączą się z etapem
  tonalnym, gdzie wpiąć maskę skóry.
- **Wdrożenie nowej osoby/sesji:** jedno pytanie o architekturę zamiast czytania kilkudziesięciu plików (zasada 2:
  oszczędność tokenów).

**Ograniczenia:** mapa jest zdjęciem z chwili budowy (stara po większych zmianach); nie zastępuje czytania kodu przy
poprawce konkretnego błędu; do konkretnego symbolu zwykły `grep` bywa szybszy.

## Jak to działa

- Nowa sesja w chmurze (po scaleniu do `main`): hook instaluje Ponytail i Graphify; plugin ładuje się przy starcie. W
  trwającej sesji `/reload-plugins` nie jest dostępne (połączenie zdalne).
- Sesja lokalna: Claude Code zapyta o zaufanie do folderu i zaproponuje Ponytail z `settings.json`; Graphify:
  `uv tool install graphifyy` i `graphify install --platform claude`.
- Graphify nie jest pluginem, więc instaluje go wyłącznie hook, nie `settings.json`.
