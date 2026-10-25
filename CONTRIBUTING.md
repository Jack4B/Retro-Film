# Współtworzenie

- Gałąź od `main`, PR z wypełnionym szablonem.
- Commity: `[Jack4B] Opis po polsku`, bez `Co-Authored-By`.
- Przed PR: `./gradlew ktlintCheck detekt test` (oraz testy Roborazzi/golden, gdy dotyczy).
- Review: [Forge], priorytety P0 (blokuje), P1 (przed merge), P2 (później). Merge po `[Forge] APPROVE`.
- `versionCode` zawsze rośnie.
- Nowy LUT: podaj źródło i licencję; bez potwierdzonej licencji nie trafia do repo.
- Teksty UI po polsku w zasobach; dostępność: 48 dp, kontrast AA, TalkBack.
