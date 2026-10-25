#!/usr/bin/env python3
"""Paczka do pobrania 144^3 PNG (D9): oryginalne HaldCLUT skopiowane pod nazwami id z katalogu
+ catalog-144.json (sha256 PNG, rozmiar PNG, dlugosc po rozpakowaniu 144^3*3 B, format png144).
Wynik NIE jest commitowany (~378 MB); publikacja Release tylko na polecenie Jack4B.

Uzycie: python3 tools/packs/build144png.py --hald <HaldCLUT/> [--out DIR] [--catalog tools/packs/catalog-33-full.json]
(lub zmienne HALD_DIR, OUT_DIR)
"""
import argparse, hashlib, json, os, shutil
from pathlib import Path
from PIL import Image

N = 144; UNPACKED = N ** 3 * 3  # 8957952

def main():
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser()
    ap.add_argument("--hald", default=os.environ.get("HALD_DIR"))
    ap.add_argument("--out", default=os.environ.get("OUT_DIR", "pack144"))
    ap.add_argument("--catalog", type=Path, default=root / "tools/packs/catalog-33-full.json")
    a = ap.parse_args(); assert a.hald, "podaj --hald lub HALD_DIR"
    hald, out = Path(a.hald), Path(a.out); out.mkdir(parents=True, exist_ok=True)
    luts = []; total = 0; odd = []
    for c in json.load(a.catalog.open())["luts"]:
        if not c.get("source_hald"): continue
        src = hald / c["source_hald"]
        with Image.open(src) as im:
            n = round((im.size[0] * im.size[1]) ** (1 / 3))
            assert n ** 3 == im.size[0] * im.size[1], (src, im.size)
        # kilka HaldCLUT w kolekcji ma poziom 16 (256^3, 4096x4096) - zapisujemy natywny rozmiar
        if n != N: odd.append((c["id"], n))
        rel = f"luts/{c['id']}.png"; dst = out / rel; dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst); data = dst.read_bytes(); total += len(data)
        e = {k: c[k] for k in ("id", "name", "displayName", "category", "license", "source_hald", "approx") if k in c}
        e.setdefault("displayName", c.get("name"))
        e.update(grid=n, format="png144" if n == N else f"png{n}", file=rel, bytes=len(data), unpackedLength=n ** 3 * 3,
                 sha256=hashlib.sha256(data).hexdigest())
        luts.append(e)
    json.dump({"format": "png144 (HaldCLUT poziom 12, PNG; po rozpakowaniu RGB8 144^3, R najszybciej)", "grid": N,
               "unpackedLength": UNPACKED, "total_bytes": total, "luts": luts},
              (out / "catalog-144.json").open("w"), ensure_ascii=False, indent=1)
    shutil.copyfile(root / "luts/LICENSE", out / "LICENSE")
    shutil.copyfile(root / "THIRD_PARTY.md", out / "THIRD_PARTY.md")
    (out / "README.md").write_text(f"""# Retro-Film – filtry 144³ PNG (D9)

{len(luts)} filmów, oryginalne HaldCLUT (RawTherapee Film Simulation Collection), PNG pod nazwami `luts/<kategoria>/<id>.png`.
Łącznie {total} B PNG. Po rozpakowaniu: RGB8, R najszybciej, {UNPACKED} B dla 144³ (`unpackedLength` w `catalog-144.json`).
Wyjątki od 144³ (natywnie inny poziom HaldCLUT): {', '.join(f'{i} ({g}³)' for i, g in odd) or 'brak'}.
Weryfikacja: `sha256sum -c SHA256SUMS`. Licencja: CC BY-SA 4.0 (`LICENSE`), atrybucja: Pat David, Pavlov Dmitry, Michael Ezra.
Zbudowane przez `tools/packs/build144png.py` z https://github.com/Jack4B/Retro-Film.
""")
    files = sorted(f for f in out.rglob("*") if f.is_file() and f.name != "SHA256SUMS")
    (out / "SHA256SUMS").write_text("".join(f"{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.relative_to(out).as_posix()}\n" for f in files))
    print(len(luts), "LUT-ow,", total, "B ->", out, "| nie-144^3:", odd)

if __name__ == "__main__":
    main()
