# Zasady dla Claude Code

Najpierw przeczytaj: AGENTS.md, docs/ARCHITEKTURA.md, docs/PLAN.md, docs/DECYZJE.md, ostatni wpis w docs/HANDOFF.md.

## Przepływ pracy
1. Każda zmiana na osobnej gałęzi (`f<faza>/<krótki-opis>`) i przez PR do `main`. Bez pushy na `main`.
2. Review robi **[Forge]** (uwagi P0/P1/P2). P0 blokuje, P1 naprawić przed merge, P2 można odłożyć (zanotować w HANDOFF).
3. Merge **tylko** po komentarzu `[Forge] APPROVE` na PR i zielonym CI.
4. Commity z prefiksem `[Jack4B]`, autor Jack4B. **Bez** `Co-Authored-By` i bez innych nazwisk.
5. Po sesji dopisz wpis do docs/HANDOFF.md.

## Twarde reguły
- `versionCode` **zawsze rośnie** (nigdy nie cofać ani nie powtarzać).
- Brak LUT-ów w repo do czasu rozstrzygnięcia D1.
- Eksport zawsze usuwa GPS z EXIF.
- Teksty UI tylko po polsku, w `strings.xml` (bez hardkodowania).
- Cele dotyku ≥ 48 dp, kontrast AA, etykiety TalkBack.
- Golden testy silnika (identity LUT dE < 1; GPU vs CPU ≤ 1–2 poziomy/kanał) – przekroczenie = blocker.
- Bez ffmpeg-kit. Bez znaków towarowych firm trzecich w ramkach.
- ktlint + detekt + testy muszą przejść lokalnie przed PR.
- Nie zmieniaj decyzji z DECYZJE.md bez akceptacji – zgłoś jako pytanie.
