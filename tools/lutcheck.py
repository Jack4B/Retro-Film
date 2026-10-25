#!/usr/bin/env python3
"""Walidacja i normalizacja LUT-ow .cube dla Retro-Film (Jack4B)."""
import argparse, hashlib, json, re, sys
from pathlib import Path

def parse_cube(p):
    size = None; title = None; data = []; dmin = [0, 0, 0]; dmax = [1, 1, 1]; errs = []
    for ln, raw in enumerate(p.read_text(errors="replace").splitlines(), 1):
        s = raw.strip()
        if not s or s.startswith("#"): continue
        k = s.split()[0].upper()
        try:
            if k == "TITLE": title = s[5:].strip().strip('"'); continue
            if k == "LUT_3D_SIZE": size = int(s.split()[1]); continue
            if k == "LUT_1D_SIZE": errs.append("LUT 1D nieobslugiwany"); break
            if k in ("DOMAIN_MIN", "DOMAIN_MAX"):
                v = list(map(float, s.split()[1:4]))
                if len(v) != 3: raise ValueError
                if k == "DOMAIN_MIN": dmin = v
                else: dmax = v
                continue
        except (ValueError, IndexError):
            errs.append(f"linia {ln}: niepoprawny naglowek {k}"); break
        if True:
            try:
                v = list(map(float, s.split()))
                if len(v) != 3: raise ValueError
                data.append(v)
            except ValueError:
                errs.append(f"linia {ln}: niepoprawne dane"); break
    if size is None: errs.append("brak LUT_3D_SIZE")
    elif not 2 <= size <= 128: errs.append(f"rozmiar siatki {size} poza 2..128")
    elif len(data) != size ** 3: errs.append(f"{len(data)} wierszy, oczekiwano {size**3}")
    if dmin != [0, 0, 0] or dmax != [1, 1, 1]:
        errs.append(f"domena {dmin}..{dmax} inna niz 0..1 (nieobslugiwana)")
    if data:
        lo = min(min(r) for r in data); hi = max(max(r) for r in data)
        if lo < -0.01 or hi > 1.01: errs.append(f"wartosci poza 0..1 ({lo:.3f}..{hi:.3f})")
    else: lo = hi = None
    return dict(size=size, title=title, rows=len(data), min=lo, max=hi, domain=[dmin, dmax], errors=errs), data

def slug(s): return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")

def normalize(meta, data, title, out):
    with out.open("w") as f:
        f.write(f'TITLE "{title}"\nLUT_3D_SIZE {meta["size"]}\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n')
        for r, g, b in data:
            f.write(f"{min(max(r,0),1):.6f} {min(max(g,0),1):.6f} {min(max(b,0),1):.6f}\n")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", type=Path, help="katalog repo Film-Luts")
    ap.add_argument("--out", type=Path, default=Path("out"))
    ap.add_argument("--license", default="CC BY-SA 4.0", help="licencja zrodla")
    ap.add_argument("--source", default="https://github.com/YahiaAngelo/Film-Luts")
    a = ap.parse_args()
    idx = {}
    j = a.src / "film_luts.json"
    if j.exists():
        for e in json.load(j.open())["filmLUTs"]: idx[e["lut_file"]] = e
    (a.out / "luts").mkdir(parents=True, exist_ok=True)
    seen = {}; catalog = []; rejected = []; dupes = []
    for p in sorted(a.src.rglob("*.cube")):
        rel = p.relative_to(a.src).as_posix()
        meta, data = parse_cube(p)
        e = idx.get(rel, {})
        if meta["errors"]:
            rejected.append({"file": rel, "errors": meta["errors"]}); continue
        h = hashlib.sha256(json.dumps([[round(x, 4) for x in r] for r in data]).encode()).hexdigest()[:16]
        if h in seen:
            dupes.append({"file": rel, "same_as": seen[h]}); continue
        seen[h] = rel
        cat = e.get("category") or p.parent.name
        name = e.get("name") or p.stem.replace("_", " ").title()
        nid = f"{slug(cat)}/{slug(p.stem)}"
        out = a.out / "luts" / f"{nid}.cube"; out.parent.mkdir(parents=True, exist_ok=True)
        normalize(meta, data, name, out)
        catalog.append({"id": nid, "name": name, "category": cat, "size": meta["size"],
                        "file": f"luts/{nid}.cube", "thumbnail": e.get("thumbnail"),
                        "source": a.source, "source_file": rel, "license": a.license, "sha": h})
    json.dump({"luts": catalog}, (a.out / "catalog.json").open("w"), ensure_ascii=False, indent=1)
    json.dump({"rejected": rejected, "duplicates": dupes}, (a.out / "report.json").open("w"), ensure_ascii=False, indent=1)
    print(f"OK {len(catalog)}, odrzucone {len(rejected)}, duplikaty {len(dupes)}")
    sizes = {}
    for c in catalog: sizes[c["size"]] = sizes.get(c["size"], 0) + 1
    print("rozmiary siatek:", sizes)
    return 0 if catalog else 1

if __name__ == "__main__": sys.exit(main())
