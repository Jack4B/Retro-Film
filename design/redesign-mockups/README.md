# Retro Film — makiety redesignu UX/UI

Same makiety, kod aplikacji bez zmian. Stan zbadany: `main` @ `cb0e3e9` (Kotlin + Jetpack Compose, Material 3, `app/src/main/kotlin/app/retrofilm/ui/*`).
Otwórz `index.html` w przeglądarce (przycisk „Motyw” albo `?theme=dark`). Rendery w `png/` (390 i 1280 px, jasny i ciemny; `full-*` to cała strona).

Uwaga: to aplikacja na Androida, więc „Obecnie” odtworzono z kodu i `strings.xml` (nie ze zrzutów z urządzenia). Zrzuty Roborazzi na gałęziach `ci/screenshots-run-*` są starsze niż `main`.

## Audyt — główne problemy
1. **Seria ukryta.** Zakres „cała seria / to zdjęcie” i „Zastosuj do wszystkich” siedzą w menu tytułu „✎ 2/5 ▾”; brak paska miniatur.
2. **Dwie biblioteki filmów.** Ustawienia › „Filtry (LUT)” (lista, pobieranie, pamięć) i Edytor › „Filmy” (siatka, szukaj) robią to samo inaczej. Słownictwo miesza film / filtr / LUT.
3. **Dwie taksonomie.** Zakładka Film filtruje po markach (Kodak, Fuji…), Biblioteka po kategoriach (Negatyw, Slajd…).
4. **Zakładki edytora.** 5 etykiet tylko tekstowych o szerokości zależnej od długości słowa; „Wykończenie” chowa Wyostrzanie, Tonowanie i Krzywe.
5. **Brak cofnij/ponów** i resetu pojedynczego suwaka przy kilkudziesięciu suwakach.
6. **Zapis bez kontroli.** „Zapisz” działa od razu, jakość/rozmiar są w panelu, postęp tylko w tytule, wynik w snackbarze, brak „Ponów nieudane” i „Udostępnij”.
7. **Start.** Trzy przyciski o tej samej wadze; brak skrótów do ostatnich filmów i presetów.
8. **Ramka.** Długa lista przełączników zmniejsza podgląd; „Ramka do wszystkich” dubluje zakres serii.
9. **Ustawienia.** Brak domyślnych (ramka, podpis, jakość; obiecane w `docs/EKRANY.md` §7); licencje rozwinięte na głównym ekranie.

## Decyzje projektowe
- Zdjęcie na pierwszym planie, panel ≤ 40% wysokości; **pasek serii** z segmentem „Cała seria | To zdjęcie” między podglądem a panelem.
- Dolna nawigacja z ikoną i etykietą: **Kadr · Korekty · Film · Efekty · Ramka** (kolejność pracy z D15 bez zmian, „Wykończenie” → „Efekty”). Podgrupy jako chipy.
- ↶ ↷ w pasku górnym, główna akcja „Zapisz N” zawsze w prawym górnym rogu → arkusz zapisu.
- **Jedna Biblioteka „Filmy”** (z Edytora, Startu i Ustawień); pobieranie w kafelku; pamięć i Wi-Fi w menu ⋮.
- Przed/po = przytrzymanie podglądu, z widoczną podpowiedzią; na tablecie dodatkowo przycisk.
- Adaptacja (compose-adaptive): ≥ 840 dp `NavigationSuiteScaffold` → rail, panel narzędzi po prawej, płótno w środku.
- Retro: bursztyn z obecnego motywu, etykiety „kodu krawędzi kliszy” (monospace wersalikami, np. `PORTRA 400 · 80%`), perforacja pod paskiem Startu, szeryfowe nagłówki. Kolory z tapety domyślnie wyłączone, żeby zachować charakter.
- Dostępność: cele ≥ 48 dp, kontrast ≥ 4,5:1 (patrz tokeny), etykiety tekstowe przy każdej ikonie.

## Gdzie jest każda funkcja
| Funkcja | Propozycja |
|---|---|
| Wybór zdjęć (Photo Picker do 30), z plików | Start: główna CTA + „Z plików…” |
| Seria / to zdjęcie, zastosuj do wszystkich | Pasek serii (segment, ✎ na miniaturze, przytrzymanie = menu) |
| Kadr, obrót, prostowanie, perspektywa, zoom | Kadr |
| Światło, Kolor, HSL, Detal, Maski, Auto, Przygotuj pod film | Korekty |
| Film, moc, ulubione, presety | Film → „Wszystkie 260 ›” = Biblioteka |
| Blaknięcie, ziarno, winieta, wyostrzanie, tonowanie, krzywe | Efekty |
| Ramka, proporcje, grubość, podpis (font, atrament, wielkość), znak wodny QR | Ramka: Styl / Podpis / Znak wodny |
| Info o zdjęciu | Etykieta filmu na płótnie (dotknięcie = szczegóły) |
| Cofnij / ponów | Pasek górny |
| Zapis, jakość, rozmiar, postęp, ponów, udostępnij | Arkusz „Zapisz” |
| Pobieranie filmów, Wi-Fi, pamięć podręczna | Filmy › ⋮ Pamięć i pobieranie |
| Motyw, kolory dynamiczne, domyślne, licencje, prywatność | Ustawienia (+ „O aplikacji”) |

## Tokeny
| Token | Jasny | Ciemny |
|---|---|---|
| bg | `#FBF8F3` | `#0E0E10` |
| surface | `#F3EDE4` | `#1A1A1E` |
| text | `#211B13` | `#F2F2F2` |
| text-2 | `#51473B` | `#B4B4BC` (obecny `#A0A0A8` podniesiony) |
| primary | `#8A5200` | `#F5A33B` |
| primary-container / on | `#FFDDB3` / `#2D1600` | `#3A2A12` / `#FFDDB3` |

Typografia: nagłówki szeryfowe (w aplikacji np. Fraunces/Young Serif, OFL), UI Roboto, etykiety kliszy monospace. Promienie 8/14/22, siatka 4/8 dp.

## QA
Playwright: brak poziomego przewijania przy 320, 360, 390, 768, 1024 i 1280 px (jasny i ciemny), 0 błędów w konsoli.

## Do potwierdzenia
- Czy „Efekty” to dobra nazwa dla „Wykończenia”.
- Cofnij/ponów i „Udostępnij” to nowe funkcje (dziś ich nie ma).
- Gdzie dokładnie dziś jest panel jakości JPEG (`ExportSettingsUi`).
