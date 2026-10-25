# Współtworzenie

Kod piszą boty zespołu (zob. [AGENTS.md](AGENTS.md)); decyduje Jack4B.

- Gałąź od `main`, PR z wypełnionym szablonem (Forge otwiera PR jako Jack4B).
- Commity: `[Jack4B] Opis po polsku`, bez `Co-Authored-By`.
- Przed PR: `./gradlew ktlintCheck detekt test` (oraz Roborazzi/golden, gdy dotyczy); narzędzia Python w `tools/`.
- Review: **Gauge → Critic**, komentarze `[Rola] P0/P1/P2 …`. P0 blokuje, P1 przed merge, P2 później.
- Merge po `[Critic] APPROVE` i decyzji Jack4B.
- `versionCode` zawsze rośnie.
- LUT-y wg [docs/LUTS.md](docs/LUTS.md).
- Teksty UI po polsku w zasobach; 48 dp, kontrast AA, TalkBack.
