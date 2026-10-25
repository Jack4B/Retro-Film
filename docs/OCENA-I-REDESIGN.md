# Ocena koncepcji i redesign UI/UX (v2)

Stan zbadany: `main` @ 68aa9bf (docs, kod `app/` + `feature/*` + `core/*`, zrzuty Roborazzi, PR #5–#25). Makiety: [makiety/index.html](makiety/index.html) (otwórz w przeglądarce). Priorytety: P0 blokuje, P1 przed merge kolejnej fazy, P2 później; „ESKALACJA” = decyzja Jack4B.

## 1. Ocena koncepcji

**Werdykt: koncepcja jest dobra i spójna, ale dziś jest to silnik z dobrym zapleczem, a nie jeszcze produkt.** Rynek aplikacji „filtry filmowe / LUT” jest zatłoczony; Retro-Film może się wyróżnić trzema rzeczami naraz: (1) prywatność i praca offline (brak konta, GPS usuwany, internet tylko do pobierania filmów), (2) obróbka **serii** z jedną ramką i podpisem, (3) wierność filmów (natywne HaldCLUT 144³, golden testy). Żadna z tych rzeczy nie jest dziś widoczna w UI.

### Mocne strony
- Jasne granice: Android, offline, bez ffmpeg, bez telemetrii, jeden host pobierania, SHA-256 przed przyjęciem pliku.
- Dojrzała inżynieria jakości: golden testy GPU vs CPU, D9 (jedna jakość LUT-ów), D10 (zarządzanie barwami), `versionCode` pilnowany w CI.
- Dostępność wpisana w reguły od początku (48 dp, AA, TalkBack, `strings.xml`).
- Specyfikacja ramki jest dopracowana (reguła `fits`, brak „cichego mikrotekstu”).

### Ryzyka i luki (od najważniejszych)

| ID | Pri | Uwaga |
|---|---|---|
| T1 | ~~P0 / ESKALACJA~~ **Zamknięte (D11)** | **Znaki towarowe w katalogu.** Decyzja Jack4B 2026-10-25: nazwy filmów zostają prawdziwe; AGENTS.md i LUTS.md zaktualizowane. Pierwotna uwaga: AGENTS.md: „bez znaków towarowych firm trzecich”; LUTS.md: „nazwy filmów tylko opisowe”. Tymczasem `catalog.json` ma `displayName` „Ilford HP5 Plus 400”, ścieżki `kodak_*`, `fuji_*`, `agfa_*`, a makiety/fake’i pokazują „Portra 400”, „Ektar 100”, „Superia”. Nazwa producenta lub linii produktowej widoczna w UI i w Releases to ryzyko prawne i sklepowe (Obtainium/F-Droid-like jest łagodniejsze, ale reguła zespołu jest jednoznaczna). Propozycja: nazwy własne/opisowe w UI („Ciepły negatyw 400”, „Czarno-biały ziarnisty 400”), nazwę źródłową tylko w ekranie licencji/„Informacje” jako atrybucja pochodzenia plików; do sprawdzenia, co dokładnie wymaga CC BY-SA (atrybucja autorów kolekcji – tak; nazwy producentów – nie). Niepewne kwestie licencyjne → eskalacja, więc tu je zgłaszam. |
| T2 | P1 | **Rozjazd dokumentów z kodem.** README: „Brak kodu, żadna funkcja nie jest gotowa”; PLAN: F2 Seria / F4 Eksport jako „do zrobienia”, a HANDOFF ma 2 wpisy z F1/F3 i nic o F4 (PR #17–#25 bez wpisu). Zespół i nowy kontrybutor dostają fałszywy obraz stanu. |
| T3 | P1 | **Aplikacja ≠ ekrany Lumena.** Działa jeden przewijany ekran testowy (`MainScreen`: przycisk wyboru, lista `FilterChip`, przełączniki). `EditorScreen`, `GalleryScreen`, `FrameScreen`, `ExportScreen` są bezstanowe, ale **nie są podpięte**; `core:batch`, `core:data` (Room), `feature:luts` to puste placeholdery. Seria – główny wyróżnik – nie istnieje w produkcie. |
| T4 | P1 | **`GalleryScreen` sprzeczny z zasadami.** Własna siatka zdjęć wymaga uprawnienia do mediów; reguły i PR #18 wymagają Photo Pickera bez uprawnień (a Photo Picker ma wielokrotny wybór: `PickMultipleVisualMedia`). Własna galeria też dubluje funkcję systemu. Rekomendacja: usunąć `GalleryScreen`, Start = jeden przycisk. |
| T5 | P1 | **Skala katalogu vs UI.** 260 filmów w 7 kategoriach (`instant_pro` 66, `instant_consumer` 54, `negative_old` 39, `negative_new` 38, `colorslide` 26, `bw` 24, `negative_color` 13) wybieranych z jednego `LazyRow` kafelków 72 dp. Bez kategorii, wyszukiwania i ulubionych wybór jest nieużywalny (a to **główna** czynność aplikacji). Dodatkowo filmy do pobrania (ok. 360 MB) nie mają podglądu przed pobraniem – patrz Pytanie 2. |
| T6 | P1 | **Obiecane, a nieobecne w UI:** przed/po „jednym gestem”, kopiuj/wklej ustawienia, przełącznik „cała seria / to zdjęcie”, eksport PNG, domyślna ramka/podpis/jakość w Ustawieniach, „Dodaj LUT” z komunikatem błędu `.cube`, „Ponów nieudane”. Z kolei silnik ma ekspozycję, kontrast, cienie/światła, nasycenie, temperaturę, ziarno, aberrację – i **żadnej** kontrolki korekt. |
| T7 | P1 | **Rozbity przepływ.** Zdjęcie → film (Edytor) → *osobny ekran* Ramka (bez podglądu w Edytorze; tylko chip „Ramka: wył.”) → *osobny ekran* Eksport z 3 ustawieniami. Dużo przeskoków dla zadania „zrób 12 zdjęć w tym samym stylu”. |
| T8 | P2 | **Żargon i powtórzenia.** „Filtry (LUT)”, „Pamięć podręczna”, „Pobrane filtry” w jednym nagłówku; zdanie o prywatności powtórzone na Eksporcie, w Filtrach i w Ustawieniach; karta z 4 liczbami (pobrane/cache/wolne/do pobrania) przed samą listą. Użytkownik nie rozróżnia „film” (nazwa w UI), „filtr” i „LUT”. Ujednolicić: **Film**. |
| T9 | P2 | **„Ostatnie projekty” (EKRANY.md §1).** Photo Picker daje URI z ograniczonym czasem życia; nie da się wiarygodnie wznowić edycji po restarcie bez uprawnień. Zamiast projektów: ostatnie filmy + ostatnie własne eksporty (czytelne bez uprawnień jako pliki aplikacji w MediaStore). Room w `core:data` można odłożyć. |
| T10 | P2 | **Material 3 Expressive beta (D6)** jest ryzykiem utrzymaniowym, ale UI korzysta z niego minimalnie (`MaterialExpressiveTheme` + ruch). Trzymać się komponentów stabilnych, by przejście na 1.5.0 było bezbolesne. |
| T11 | P2 | **Rozmiary:** APK debug 33,8 MB (10 PNG 144³ w APK), pobranie 360 MB, cache LRU 180 MB, pierwsze otwarcie filmu = dekodowanie PNG ~9 MB. Wymaga stanu „ładowanie filmu” na miniaturach i w podglądzie (jest wymóg stanów w EKRANY.md, brak w kodzie edytora). |
| T12 | P2 | **Proces.** Trzystopniowe review (Gauge → Critic → Jack4B) przy jednym właścicielu jest ciężkie, ale tu zostawiam bez zmian – to decyzja zespołu. |

### Pytania do Jack4B – odpowiedzi (D11, D12)
Odpowiedzi Jack4B (2026-10-25): 1) nazwy prawdziwe; 2) miniatury w APK – tak; 3) eksport tylko JPEG; 4) seria: jeden film i jedna ramka, podpis osobno dla każdego zdjęcia, bez Room; 5) zakładki w Edytorze – tak; do tego `GalleryScreen` usuwamy (T4). Treść pytań poniżej zostaje jako zapis kontekstu.

### Pytania (kontekst)
1. **T1:** nazwy filmów w UI – opisowe/własne (rekomendacja), czy zostają z nazwami producentów? Czy zlecić Forge/Python Programista Master mapowanie `id → displayName` (plik, nie zmiana LUT-ów)?
2. **Podgląd filmów do pobrania:** wbudować miniatury JPG (~15 KB × 260 ≈ 4 MB w APK), żeby można było wybierać przed pobraniem, czy pokazywać tylko nazwy? (Rekomendacja: miniatury; użytkownik wybiera filmy po wyglądzie.)
3. **PNG w eksporcie:** potrzebny (EKRANY.md), czy tylko JPEG? (Rekomendacja: tylko JPEG w v0.1; PNG po feedbacku.)
4. **Seria:** czy w v0.1 wystarczy „jedna ramka + jeden film dla całej serii + podpis per zdjęcie”, bez Room i bez zapisu projektów? (Rekomendacja: tak.)
5. **Nawigacja:** zgoda na zakładki w Edytorze (Film / Korekty / Ramka / Więcej) zamiast osobnych ekranów?

## 2. Redesign – zasady

1. **Jedno miejsce pracy.** Po wyborze zdjęć użytkownik jest w Edytorze i tam kończy: film, korekty, ramka. Eksport to arkusz, nie ekran.
2. **Zdjęcie na pierwszym planie.** Panel narzędzi ≤ 40% wysokości; podgląd ramki na tym samym płótnie.
3. **Seria jest zawsze widoczna** (pasek miniatur) i ma jawny zakres zmian: „cała seria / tylko to zdjęcie”.
4. **Wybór filmu jest głównym zadaniem:** kategorie, ulubione, miniatury na własnym zdjęciu, przytrzymanie = przed/po, ☁ = do pobrania.
5. **Jedno słownictwo:** Film, Korekty, Ramka, Zapisz. Żargon (LUT, cache) tylko tam, gdzie konieczny.
6. **Dostępność jak dotąd:** ≥ 48 dp, AA, `heading()`, `stateDescription`, układ odporny na czcionkę 200%; zakładki i arkusz mają role i etykiety.
7. **Prywatność bez kazań:** jedna obietnica na Starcie, szczegóły w „O aplikacji”.

### Nawigacja (docelowa)
```
Start ──(Photo Picker 1…N)──▶ Edytor [Film | Korekty | Ramka | Więcej] ──▶ arkusz „Zapisz” ──▶ postęp serii
  │                                   └─ „Więcej”: Biblioteka filmów, kopiuj/wklej ustawienia
  └─ Ustawienia ──▶ Pamięć i sieć · Licencje i autorzy
```
Ekrany `GalleryScreen` i osobny `ExportScreen` znikają; `FrameScreen` staje się zakładką; `LutDownloadScreen` staje się „Biblioteką filmów”.

### Mapowanie na kod (propozycja kolejności – bez zmian w tym PR)
| Krok | Zakres | Moduł |
|---|---|---|
| 1 | Decyzje (D11, D12) i docs (T2) – ten PR | docs |
| 2 | **PR #27 „Edytor krok 1”:** Start, Edytor z zakładkami Film / Ramka, przed/po, moc filmu, usunięty `GalleryScreen` | `app`, `feature:start` |
| 3 | Przed/po (przytrzymanie), korekty | `feature:editor` + silnik |
| 4 | Ramka jako zakładka, usunięcie osobnego ekranu | `feature:editor` |
| 5 | Seria: pasek miniatur, zakres zmian, WorkManager | `core:batch`, `feature:batch` |
| 6 | Arkusz Zapisz + postęp/ponów | `feature:batch` |
| 7 | Biblioteka filmów (scalone ze „Filtry”), ulubione | `feature:luts` |

## 3. Makiety
[makiety/index.html](makiety/index.html) (nazwy filmów prawdziwe wg D11; 260 filmów: szukaj, kategorie z licznikami, ★): 1 Start · 2 Edytor/Film · 3 Edytor/Ramka · 4 Arkusz Zapisz · 5 Korekty + przed/po · 6 Biblioteka filmów · 7 Ustawienia · 8 Postęp serii i błędy. Makiety są statycznym HTML (bez zależności, bez sieci), w palecie z `core/ui/Theme.kt`.

## 4. Zakres tego PR
Tylko dokumenty: ocena, makiety, decyzje D11/D12 (DECYZJE.md), zmiany w AGENTS.md, LUTS.md i EKRANY.md, wpis w HANDOFF. Kod idzie osobnymi PR-ami (pierwszy: #27). README i PLAN pozostają do aktualizacji (T2).
