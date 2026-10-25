# CI i wydania

## Workflowy
- `ci.yml` — na każdym PR: ktlint, detekt, sprawdzenie `versionCode`, build debug + testy jednostkowe.
  Na push do `main` dodatkowo buduje podpisany APK release i wrzuca go jako artefakt.
- `release.yml` — tylko ręcznie (`workflow_dispatch`) lub po tagu `v*`. Dla tagu tworzy GitHub Release,
  w opisie zapisuje `versionCode=...` (używane przez kontrolę w CI).

## Wersjonowanie
- `versionName` pochodzi z pliku `VERSION` (np. `0.1.0`).
- `versionCode = (major*10000 + minor*100 + patch) * 1000 + (GITHUB_RUN_NUMBER % 1000)`.
- CI przerywa build, jeśli `versionCode` nie jest większy niż w ostatnim release (pomijane, gdy brak releasów).

## Sekrety podpisu (opcjonalne)
Bez nich release jest podpisany kluczem debug. Kluczy **nigdy** nie commitujemy.

| Sekret | Opis |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | keystore (`.jks`) zakodowany base64 |
| `RELEASE_KEYSTORE_PASSWORD` | hasło keystore |
| `RELEASE_KEY_ALIAS` | alias klucza |
| `RELEASE_KEY_PASSWORD` | hasło klucza |

## Lokalnie
Wymagane JDK 17+ i Android SDK (compileSdk 37). `./gradlew assembleDebug testDebugUnitTest`.
