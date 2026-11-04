# Wydanie testowe K (2026-11-04)

## A. Tryb ramki (Ustawienia → Zdjęcia → Tryb ramki)
- **Ramka na zewnątrz** (domyślnie, zachowanie J): zdjęcie 1:1, plik rośnie o margines ramki – najszybciej.
- **Oryginalny rozmiar pliku** (dawne zachowanie): zdjęcie zmniejszane do okna ramki, dłuższy bok pliku = dłuższy bok
  oryginału. Teraz szybkie: zmniejszenie (równoległy Lanczos, etap `save_frame_fit`) dzieje się *przed* potokiem
  filmu, więc LUT/ziarno/dithering liczą mniej pikseli i nie są rozmywane, a etap ramki kopiuje 1:1.
  Uwaga: ramka zmienia proporcje płótna, więc zachowany jest dłuższy bok (jak dawniej), nie oba wymiary.
- Zapisywane w `AppSettingsRepository` (`frame_outward`), w kopii zapasowej. Zrzuty Roborazzi jasny/ciemny.

## B. Zapis na GPU (Diagnostyka → „Zapis na GPU (eksperymentalny)”, domyślnie włączony)
- Na GPU: etap LUT-a (najdroższy, `lut_render_dither`) – AGSL na RGBA_F16 (API 33+), wariant z liniowym buforem,
  zapasowo GLES 3 z FBO RGBA32F i LUT-em 3D RGB16F (interpolacja czworościenna w shaderze, jak CPU).
- Przed użyciem próbka 4913 barw vs CPU (tolerancja 0,75/255 – poniżej szumu ditheringu); zły wynik / wyjątek →
  następny backend → CPU. Błąd w trakcie zapisu → reszta pasów i kolejne zapisy na CPU (wynik identyczny z CPU).
- Backend logowany w PerfLog: `save_backend lut=gpu-agsl-f16|gpu-agsl-f16-lin|gpu-gles-f32|cpu reason=…`,
  `save_backend_fallback`. Display P3 i podglądy zostają na CPU.
- Efekty sąsiedztwa (wyostrzanie, deband, halacja, ziarno, dithering) **zostają na CPU** w pasach – port na GPU z
  kaflami z zakładką to osobna, duża zmiana; najpierw CSV z telefonu ma pokazać, ile daje sam LUT na GPU.
- Prośba: zapisać te same zdjęcia z przełącznikiem włączonym i wyłączonym i przesłać oba CSV.
