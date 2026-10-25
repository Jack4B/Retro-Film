# Plan

| Faza | Zakres | Gotowe, gdy |
|---|---|---|
| **F0** Szkielet + CI | Moduły, Hilt, motyw, nawigacja, ktlint/detekt, GitHub Actions | CI zielone, pusta aplikacja startuje |
| **F1** Silnik | Parser `.cube`, AGSL + fallback GLES, interpolacja, korekty, ścieżka CPU | Golden testy przechodzą |
| **F2** Seria | Wybór wielu zdjęć, ekran Seria, wspólne ustawienia, WorkManager | Seria 12+ zdjęć z postępem i anulowaniem |
| **F3** Ramka | `core:frame`, ekran Ramka i podpis, podpis (D2) | Zgodność z RAMKA-SPEC, testy Roborazzi |
| **F4** Eksport | JPEG/PNG, jakość, EXIF bez GPS, „Ponów nieudane” | Brak GPS w plikach wynikowych |
| **F5** Szlif/wydanie | Dostępność, Baseline Profiles, Licencje i autorzy, podpisane APK | Release w GitHub Releases, działa w Obtainium |
