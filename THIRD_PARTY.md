# Komponenty zewnętrzne

| Komponent | Źródło | Licencja | Status |
|---|---|---|---|
| Film-Simulator | github.com/YahiaAngelo/Film-Simulator (fork: Jack4B/Film-Simulator) | MIT | Inspiracja; parser `.cube` i silnik przenoszone z oryginału |
| LUT-y filmowe – RawTherapee Film Simulation Collection (autorzy: Pat David, Pavlov Dmitry, Michael Ezra) | https://rawpedia.rawtherapee.com/Film_Simulation (przepakowane w YahiaAngelo/Film-Luts) | CC BY-SA 4.0 | dołączone: `core/engine/src/main/assets/luts/` (33³ RGB8, utwór zależny, CC BY-SA 4.0; LICENSE w katalogu) |
| Font Caveat (podpis) | Google Fonts (github.com/googlefonts/caveat) | SIL OFL 1.1 | używany – dołączony: `core/ui/src/main/res/font/caveat.ttf`, licencja: `core/ui/src/main/assets/licenses/OFL-Caveat.txt` (dołączana do APK jako asset `licenses/OFL-Caveat.txt`) |
| Shadery silnika (AGSL, GLES) w `core:engine` | własne (Retro-Film) | MIT | Nie przeniesiono kodu z YahiaAngelo/Film-Simulator – napisane od zera; przy ewentualnym przeniesieniu zachować nagłówek `Copyright YahiaAngelo` |

## LUT-y
- Autorzy kolekcji: Pat David, Pavlov Dmitry, Michael Ezra.
- LUT-y wygenerowano z HaldCLUT (https://rawtherapee.com/shared/HaldCLUT.zip) do siatki 33³ narzędziem `tools/hald2lut.py` – to utwór zależny, również na **CC BY-SA 4.0**. Katalog `luts/` jest wyłączony z licencji MIT.
- Nazwy filmów są wyłącznie opisowe; brak powiązania z producentami, brak logo i znaków towarowych.
- Szczegóły: [docs/LUTS.md](docs/LUTS.md).

Lista bibliotek (AndroidX, Hilt, Coil itd.) zostanie uzupełniona i pokazana w aplikacji w sekcji „Licencje i autorzy”.

## LUT-y bez źródła HaldCLUT (brak w HaldCLUT.zip, nie dołączone)
27 pozycji katalogu Film-Luts nie ma odpowiednika w RawTherapee HaldCLUT.zip i nie jest w `catalog.json`:

- `fujixtransiii/fuji_xtrans_iii_acros_g`
- `fujixtransiii/fuji_xtrans_iii_acros_r`
- `fujixtransiii/fuji_xtrans_iii_acros_ye`
- `fujixtransiii/fuji_xtrans_iii_acros`
- `fujixtransiii/fuji_xtrans_iii_astia`
- `fujixtransiii/fuji_xtrans_iii_classic_chrome`
- `fujixtransiii/fuji_xtrans_iii_mono_g`
- `fujixtransiii/fuji_xtrans_iii_mono_r`
- `fujixtransiii/fuji_xtrans_iii_mono_ye`
- `fujixtransiii/fuji_xtrans_iii_mono`
- `fujixtransiii/fuji_xtrans_iii_pro_neg_hi`
- `fujixtransiii/fuji_xtrans_iii_pro_neg_std`
- `fujixtransiii/fuji_xtrans_iii_provia`
- `fujixtransiii/fuji_xtrans_iii_sepia`
- `fujixtransiii/fuji_xtrans_iii_velvia`
- `print/fuji_3510_constlclip`
- `print/fuji_3510_constlmap`
- `print/fuji_3510_cuspclip`
- `print/fuji_3513_constlclip`
- `print/fuji_3513_constlmap`
- `print/fuji_3513_cuspclip`
- `print/kodak_2383_constlclip`
- `print/kodak_2383_constlmap`
- `print/kodak_2383_cuspclip`
- `print/kodak_2393_constlclip`
- `print/kodak_2393_constlmap`
- `print/kodak_2393_cuspclip`
