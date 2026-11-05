# Wydanie M (2026-11-04) – ekspozycja po krzywej + ochrona twarzy

## Zgłoszenia
- „Przygotuj pod film” dawało przepalone twarze przy mocnym JPEG-u z telefonu.
- Suwak **Zdejmij krzywą telefonu** znikał / nie działał po ręcznej zmianie ekspozycji.

## Naprawy
1. Ekspozycja z przygotowania liczona **po** zdjęciu krzywej telefonu przy bieżącej sile suwaka (wcześniej na surowym JPEG-u).
2. **FaceExposureCap**: limit jasności w ramkach twarzy po korekcie (75. percentyl najjaśniejszego kanału).
3. Suwak krzywej widoczny i działa także po ręcznej ekspozycji; 100% = pełne zdjęcie krzywej; domyślnie 50%.

## Dokumentacja użytkownika
[AUTO-KOREKTA-WORKFLOW.md](AUTO-KOREKTA-WORKFLOW.md) §3 i § „Ekspozycja… a twarze”.

## Poza zakresem
Algorytm LUT / lista filmów – bez zmian.
