# Zasady zespołu (boty)

Retro-Film budowany jest od zera przez zespół botów. Właściciel i decydent: **Jack4B**. Żadnych prawdziwych nazwisk (poza wymaganą atrybucją licencji).

Najpierw przeczytaj: docs/ARCHITEKTURA.md, docs/PLAN.md, docs/DECYZJE.md, ostatni wpis w docs/HANDOFF.md.

## Role
- **Forge** – architektura, silnik, kod aplikacji; otwiera PR jako Jack4B (`gh`).
- **Lumen** – makiety UI.
- **Gauge** – QA i kryteria akceptacji (pierwsze review).
- **Critic** – bramka review (drugie review, `APPROVE`).
- **Python Programista Master** – narzędzia LUT w `tools/` (Python, poza APK), `lutcheck.py`.
- **Nexus** – koordynacja, priorytety, HANDOFF.

## Przepływ pracy
1. Każda zmiana na gałęzi (`f<faza>/<opis>` lub `docs/<opis>`) i przez PR do `main`. Bez pushy na `main`.
2. Review w kolejności: **Gauge → Critic**. Komentarze z prefiksem roli (`[Gauge]`, `[Critic]`) i priorytetem: P0 blokuje, P1 naprawić przed merge, P2 można odłożyć (zapisać w HANDOFF).
3. Merge **tylko** po `[Critic] APPROVE`, zielonym CI i decyzji Jack4B.
4. Commity `[Jack4B] Opis po polsku`, autor Jack4B, **bez** `Co-Authored-By`.
5. Po sesji wpis w docs/HANDOFF.md.

## Twarde reguły
- `versionCode` zawsze rośnie (zob. D5).
- LUT-y tylko zgodnie z docs/LUTS.md (CC BY-SA 4.0, `luts/` poza MIT).
- Eksport zawsze usuwa GPS.
- UI tylko po polsku w `strings.xml`; cele ≥ 48 dp, kontrast AA, TalkBack.
- Golden testy silnika (identity LUT dE < 1; GPU vs CPU ≤ 1–2 poziomy/kanał) – przekroczenie = P0.
- Bez ffmpeg-kit; bez logo i znaków graficznych firm trzecich. Nazwy filmów w katalogu LUT zostają prawdziwe, jak w paczce (D11, decyzja Jack4B); poza nazwami filmów nie używamy cudzych znaków towarowych.
- ktlint + detekt + testy przechodzą przed PR.
- Decyzji z DECYZJE.md nie zmieniamy bez Jack4B; niepewne kwestie licencyjne → ESKALACJA.
