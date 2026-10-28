# Narzędzia sesji Claude Code (Ponytail, Agent Skills, Graphify)

Konfiguracja w repozytorium: `.claude/settings.json` (pluginy) i `.claude/hooks/session-start.sh` (instalacja na starcie
sesji w chmurze). Obowiązuje po scaleniu do `main`; hook działa tylko w sesjach zdalnych i jest powtarzalny.
**`AGENTS.md` ma pierwszeństwo** przed zaleceniami tych narzędzi (commity `[Jack4B]` bez `Co-Authored-By`, lokalnie
tylko ktlint i detekt, testy na CI, mało większych PR-ów).

## Co jest zainstalowane

| Narzędzie | Do czego | Koszt kontekstu |
|---|---|---|
| **Ponytail** 5.3.2 | najprostsze rozwiązanie: YAGNI, najpierw stdlib/istniejący kod, mało abstrakcji | ok. 0,6 tys. tokenów w każdej sesji |
| **Agent Skills** 0.6.13 | 34 skille procesu: spec, plan, build, test, review, uproszczenie, wydanie; 4 agenci (security, web-performance, code-review, test) | ok. 3,7 tys. tokenów w każdej sesji (przy limicie abonamentu to realny koszt; wyłączenie: `claude plugin disable agent-skills@addy-agent-skills` albo `false` w `enabledPlugins`) |
| **Graphify** | mapa wiedzy projektu (graf zależności kodu i dokumentów) do pytań o architekturę zamiast grepowania | kod analizowany drzewem składni lokalnie (bez kosztu), dokumenty przez model |

## Jak z tego korzystać

**Ponytail** działa sam: przy pisaniu kodu podpowiada najprostszą drogę. Komendy: `/ponytail` (tryb), `/ponytail-review`
(przegląd zmian pod kątem zbędnej złożoności), `/ponytail-audit` (przegląd projektu), `/ponytail-debt` (lista długu),
`/ponytail-gain` (ile kodu zaoszczędzono), `/ponytail-help`.

**Agent Skills**: wpisz `/` i nazwę skilla; najprzydatniejsze u nas: `plan` (plan przed większą zmianą), `spec`
(specyfikacja), `build` (małe kroki), `test`, `review` (przegląd przed PR), `code-simplify` (upraszczanie),
`performance-optimization`, `security-and-hardening`, `ship` (lista przed wydaniem). Przy kolizji nazw użyj pełnej
nazwy `agent-skills:plan`. Przykład: „zrób `plan` dla etapu D1 Dodge & Burn” przed pisaniem kodu.

**Graphify**: najpierw zbuduj mapę: `/graphify app/src` (powstaje `graphify-out/`, ignorowane przez git). Potem pytania
z konsoli: `graphify explain "ToneMask"` (opis węzła i sąsiadów), `graphify path "StripPipeline" "Dither"` (najkrótsza
ścieżka zależności), albo zwykłe pytanie o architekturę w sesji (skill sięga do `graphify-out/graph.json`). Mapę trzeba
odświeżyć po większych zmianach (`/graphify app/src` ponownie).

## Jak to działa i czego nie robi

- Nowa sesja w chmurze (po scaleniu do `main`): hook instaluje oba pluginy i Graphify; pluginy ładują się przy starcie,
  więc działają od pierwszej wiadomości. W sesji już trwającej `/reload-plugins` nie jest dostępne (połączenie zdalne).
- Sesja lokalna na komputerze: Claude Code zapyta o zaufanie do folderu i zaproponuje instalację pluginów z
  `settings.json`; Graphify: `uv tool install graphifyy` i `graphify install --platform claude`.
- Graphify nie jest pluginem, więc deklaruje go wyłącznie hook, nie `settings.json`.
