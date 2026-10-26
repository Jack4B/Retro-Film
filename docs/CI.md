# CI i wydania

## Workflowy
- `ci.yml` — na każdym PR: ktlint, detekt (pobrane binaria weryfikowane sumą SHA-256), sprawdzenie `versionCode`,
  build debug + testy jednostkowe; APK debug (podpisany kluczem debug) jako artefakt z retencją 14 dni.
  Na push do `main` dodatkowo buduje APK release podpisany kluczem z sekretów i wrzuca go jako artefakt.
- `release.yml` — tylko ręcznie (`workflow_dispatch`) lub po tagu `v*`. Dla tagu tworzy GitHub Release,
  w opisie zapisuje `versionCode=...` (używane przez kontrolę w CI).

## Wersjonowanie
- `versionName` pochodzi z pliku `VERSION` (np. `1.0.0`).
- `versionCode = (major*10000 + minor*100 + patch) * 1000 + (GITHUB_RUN_NUMBER % 1000)`.
- CI przerywa build, jeśli `versionCode` nie jest większy niż w ostatnim release (pomijane, gdy brak releasów).

## Tagi
Tag `v*` tworzymy **wyłącznie po decyzji Jack4B**. Boty nie tworzą tagów ani release'ów same.

## Akcje GitHub
Wszystkie akcje są przypięte do pełnego SHA commita (z komentarzem `# vX`).

## Sekrety podpisu (wymagane dla release)
Job `release-apk` (push do `main`) oraz `release.yml` **kończą się błędem**, jeśli sekret `RELEASE_KEYSTORE_BASE64`
jest pusty. Kluczem debug podpisywane są tylko artefakty z PR. Kluczy **nigdy** nie commitujemy.

| Sekret | Opis |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | keystore (`.jks`) zakodowany base64 |
| `RELEASE_KEYSTORE_PASSWORD` | hasło keystore |
| `RELEASE_KEY_ALIAS` | alias klucza |
| `RELEASE_KEY_PASSWORD` | hasło klucza |

## Lokalnie
Wymagane JDK 17+ i Android SDK (compileSdk 37). `./gradlew assembleDebug testDebugUnitTest`.
