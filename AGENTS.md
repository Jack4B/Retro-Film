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

## Zasady kluczowe (Jack4B, 2026-10-26; mają pierwszeństwo przy każdej pracy nad kodem)
1. **Wyróżnik aplikacji: maksimum z JPEG.** Algorytmy mają dawać fantastyczną barwę, nie niszczyć detalu i wyostrzać
   to, co ważne (włosy, oczy, krawędzie) odpowiednimi metodami, w tym świadomymi treści (twarz, włosy, niebo).
   Każdą zmianę kodu obrazu oceniamy pod tym kątem i opieramy na aktualnej literaturze i dobrych praktykach
   (przegląd: docs/PRZEGLAD-ALGORYTMOW.md); kodu copyleft nie kopiujemy.
2. **Efektywna praca, szanujemy limit abonamentu.** Nie subskrybujemy PR-ów (powiadomienia są drogie): `send_later`
   + jeden odczyt `get_check_runs`; logi tylko ogon (nie 400 wierszy, gdy wystarczy lista testów); mało większych
   PR-ów zamiast wielu małych; pomiary i testy na CI, lokalnie tylko ktlint i detekt; nie czytamy dużych plików w
   całości; podsumowania krótkie. Gdy pomiar jest zaszumiony, porównujemy A/B w jednym przebiegu zamiast powtarzać.

## Twarde reguły
- `versionCode` zawsze rośnie (zob. D5).
- LUT-y tylko zgodnie z docs/LUTS.md (CC BY-SA 4.0, `luts/` poza MIT).
- Eksport zawsze usuwa GPS.
- UI tylko po polsku w `strings.xml`; cele ≥ 48 dp, kontrast AA, TalkBack.
- Golden testy silnika (identity LUT dE < 1; GPU vs CPU ≤ 1–2 poziomy/kanał) – przekroczenie = P0.
- Bez ffmpeg-kit; bez logo i znaków graficznych firm trzecich. Nazwy filmów w katalogu LUT zostają prawdziwe, jak w paczce (D11, decyzja Jack4B); poza nazwami filmów nie używamy cudzych znaków towarowych.
- ktlint + detekt + testy przechodzą przed PR.
- Decyzji z DECYZJE.md nie zmieniamy bez Jack4B; niepewne kwestie licencyjne → ESKALACJA.
