# Komponenty zewnętrzne

| Komponent | Źródło | Licencja | Status |
|---|---|---|---|
| Film-Simulator | github.com/YahiaAngelo/Film-Simulator (fork: Jack4B/Film-Simulator) | MIT | Inspiracja; parser `.cube` i silnik przenoszone z oryginału |
| LUT-y filmowe – RawTherapee Film Simulation Collection (autorzy: Pat David, Pavlov Dmitry, Michael Ezra) | https://rawpedia.rawtherapee.com/Film_Simulation (przepakowane w YahiaAngelo/Film-Luts) | CC BY-SA 4.0 | jeszcze nie dołączone do repo |
| Font Caveat (podpis) | Google Fonts (github.com/googlefonts/caveat) | SIL OFL 1.1 | używany – dołączony: `core/ui/src/main/res/font/caveat.ttf`, licencja: `core/ui/src/main/assets/licenses/OFL-Caveat.txt` (dołączana do APK jako asset `licenses/OFL-Caveat.txt`) |
| Shadery silnika (AGSL, GLES) w `core:engine` | własne (Retro-Film) | MIT | Nie przeniesiono kodu z YahiaAngelo/Film-Simulator – napisane od zera; przy ewentualnym przeniesieniu zachować nagłówek `Copyright YahiaAngelo` |

## LUT-y
- Autorzy kolekcji: Pat David, Pavlov Dmitry, Michael Ezra.
- LUT-y zostaną wygenerowane ponownie z HaldCLUT do siatki 33³ – to utwór zależny, również na **CC BY-SA 4.0**. Katalog `luts/` jest wyłączony z licencji MIT.
- Nazwy filmów są wyłącznie opisowe; brak powiązania z producentami, brak logo i znaków towarowych.
- Szczegóły: [docs/LUTS.md](docs/LUTS.md).

Lista bibliotek (AndroidX, Hilt, Coil itd.) zostanie uzupełniona i pokazana w aplikacji w sekcji „Licencje i autorzy”.
