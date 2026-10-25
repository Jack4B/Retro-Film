#!/usr/bin/env python3
"""HaldCLUT (RawTherapee, poziom 12 -> 144^3) -> binarne LUT-y 33^3 dla Retro-Film (Jack4B).

Format pliku .rgb8 (zob. docs/LUT-BIN.md, PR #5): surowe RGB8 BEZ naglowka,
dokladnie N^3*3 bajtow (N=33 -> 107811 B), kolejnosc jak w .cube: R najszybciej,
potem G, potem B (indeks = r + g*N + b*N*N), wartosc v/255. Rozmiar, format i
sha256 w catalog.json. RGB16 liczone tylko diagnostycznie (raport), nie zapisywane.

Dopasowanie do katalogu (id kategoria/slug jak w lutcheck.py) po znormalizowanej
nazwie (np. "Kodak Portra 400 3 +" <-> kodak_portra_400_+); RMSE vs .cube 13^3 tylko informacyjnie.
Dowod: dE2000 (sRGB D65 -> Lab) miedzy 33^3 (po kwantyzacji, trilinearnie) a
oryginalem 144^3 we WSZYSTKICH 144^3 punktach. Wymaganie: max dE < 1.
"""
import argparse, hashlib, json, re, struct, sys
from pathlib import Path
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from lutcheck import parse_cube, slug  # noqa: E402

def load_hald(p):
    im = np.asarray(Image.open(p).convert("RGB") if Image.open(p).mode not in ("I;16", "RGB;16") else Image.open(p))
    a = im.astype(np.float64) / (65535.0 if im.dtype == np.uint16 else 255.0)
    n = round((a.shape[0] * a.shape[1]) ** (1 / 3))
    assert n ** 3 == a.shape[0] * a.shape[1], p
    return a.reshape(n, n, n, 3)  # [b, g, r]

def trilinear(lut, pts):
    """lut[b,g,r,3], pts (...,3) rgb w 0..1."""
    n = lut.shape[0]; x = np.clip(pts, 0, 1) * (n - 1)
    i0 = np.minimum(np.floor(x).astype(np.int64), n - 2); f = x - i0
    r0, g0, b0 = i0[..., 0], i0[..., 1], i0[..., 2]
    fr, fg, fb = f[..., 0:1], f[..., 1:2], f[..., 2:3]
    out = 0
    for db, wb in ((0, 1 - fb), (1, fb)):
        for dg, wg in ((0, 1 - fg), (1, fg)):
            for dr, wr in ((0, 1 - fr), (1, fr)):
                out = out + lut[b0 + db, g0 + dg, r0 + dr] * (wb * wg * wr)
    return out

def grid(n):
    v = np.linspace(0, 1, n)
    b, g, r = np.meshgrid(v, v, v, indexing="ij")
    return np.stack([r, g, b], -1)  # [b,g,r,3]

def lab(rgb):
    c = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)
    M = np.array([[0.4124564, 0.3575761, 0.1804375], [0.2126729, 0.7151522, 0.0721750], [0.0193339, 0.1191920, 0.9503041]])
    xyz = c @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)

def de2000(l1, l2):
    L1, a1, b1 = l1[..., 0], l1[..., 1], l1[..., 2]; L2, a2, b2 = l2[..., 0], l2[..., 1], l2[..., 2]
    C1 = np.hypot(a1, b1); C2 = np.hypot(a2, b2); Cb = (C1 + C2) / 2
    G = 0.5 * (1 - np.sqrt(Cb ** 7 / (Cb ** 7 + 25 ** 7)))
    a1p = (1 + G) * a1; a2p = (1 + G) * a2
    C1p = np.hypot(a1p, b1); C2p = np.hypot(a2p, b2)
    h1 = np.degrees(np.arctan2(b1, a1p)) % 360; h2 = np.degrees(np.arctan2(b2, a2p)) % 360
    dL = L2 - L1; dC = C2p - C1p
    dh = h2 - h1; dh = np.where(dh > 180, dh - 360, np.where(dh < -180, dh + 360, dh))
    dh = np.where(C1p * C2p == 0, 0, dh)
    dH = 2 * np.sqrt(C1p * C2p) * np.sin(np.radians(dh / 2))
    Lb = (L1 + L2) / 2; Cbp = (C1p + C2p) / 2
    hs = h1 + h2
    hb = np.where(C1p * C2p == 0, hs, np.where(np.abs(h1 - h2) <= 180, hs / 2, np.where(hs < 360, (hs + 360) / 2, (hs - 360) / 2)))
    T = (1 - 0.17 * np.cos(np.radians(hb - 30)) + 0.24 * np.cos(np.radians(2 * hb)) + 0.32 * np.cos(np.radians(3 * hb + 6)) - 0.20 * np.cos(np.radians(4 * hb - 63)))
    dT = 30 * np.exp(-(((hb - 275) / 25) ** 2))
    Rc = 2 * np.sqrt(Cbp ** 7 / (Cbp ** 7 + 25 ** 7))
    SL = 1 + 0.015 * (Lb - 50) ** 2 / np.sqrt(20 + (Lb - 50) ** 2); SC = 1 + 0.045 * Cbp; SH = 1 + 0.015 * Cbp * T
    RT = -np.sin(np.radians(2 * dT)) * Rc
    return np.sqrt((dL / SL) ** 2 + (dC / SC) ** 2 + (dH / SH) ** 2 + RT * (dC / SC) * (dH / SH))

def quant(lut, bits):
    m = 255 if bits == 8 else 65535
    q = np.round(np.clip(lut, 0, 1) * m)
    return q.astype(np.uint8 if bits == 8 else "<u2"), q / m

def write(path, q, bits):
    """Surowe RGB, bez naglowka, R najszybciej; RGB16 = uint16 little-endian."""
    path.write_bytes(q.reshape(-1, 3).astype(np.uint8 if bits == 8 else "<u2").tobytes())

def evaluate(hald, lut33):
    n = hald.shape[0]; des = []
    pts = grid(n)
    for bi in range(0, n, 16):  # kawalki po B, zeby oszczedzac pamiec
        ref = hald[bi:bi + 16]; got = trilinear(lut33, pts[bi:bi + 16])
        des.append(de2000(lab(ref), lab(np.clip(got, 0, 1))).ravel())
    d = np.concatenate(des)
    return dict(mean=round(float(d.mean()), 4), p99=round(float(np.percentile(d, 99)), 4), max=round(float(d.max()), 4), points=int(d.size))

SUF = {"+", "++", "+++", "-", "--", "---", "alt"}

def hald_key(stem):
    m = re.match(r"^(.*?)(?: (\d))?((?: (?:\+{1,3}|-{1,3}))?(?: Alt)?)$", stem)
    return re.sub("[^a-z0-9]", "", m.group(1).lower()) + "".join("_" + t.lower() for t in m.group(3).split())

def cube_key(stem):
    t = stem.lower().split("_"); s = []
    while len(t) > 1 and t[-1] in SUF: s.insert(0, t.pop())
    return re.sub("[^a-z0-9]", "", "_".join(t)) + "".join("_" + x for x in s)

def lut_id(cat, stem):
    """Jak lutcheck.py (slug kategorii/slug nazwy), ale koncowe +/-/alt zachowane
    jako _p/_pp/_m/_mm/_alt - inaczej np. fuji_fp-100c, _+ i _++ koliduja."""
    t = stem.lower().split("_"); s = []
    while len(t) > 1 and t[-1] in SUF: s.insert(0, t.pop())
    tag = {"+": "p", "++": "pp", "+++": "ppp", "-": "m", "--": "mm", "---": "mmm", "alt": "alt"}
    return f"{slug(cat)}/" + "_".join([slug("_".join(t))] + [tag[x] for x in s])

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("hald", type=Path, help="katalog HaldCLUT (rozpakowany HaldCLUT.zip)")
    ap.add_argument("filmluts", type=Path, help="klon YahiaAngelo/Film-Luts (nazwy i kategorie)")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--size", type=int, default=33)
    ap.add_argument("--bits", type=int, choices=(8, 16), default=8, help="RGB8 (.rgb8) lub RGB16 LE (.rgb16)")
    ap.add_argument("--only", help="lista id po przecinku (np. zestaw startowy do APK)")
    ap.add_argument("--identity", action="store_true", help="dodaj identity.rgbN (id: identity)")
    ap.add_argument("--golden", type=Path)
    a = ap.parse_args()
    only = set(a.only.split(",")) if a.only else None
    ext = f"rgb{a.bits}"
    halds = sorted(a.hald.rglob("*.png"))
    g13 = grid(13); h13 = {}; cache = {}
    for p in halds:
        h13[p] = trilinear(load_hald(p), g13)
    # katalog referencyjny jak w lutcheck.py
    idx = {}
    j = a.filmluts / "film_luts.json"
    if j.exists():
        for e in json.load(j.open())["filmLUTs"]: idx[e["lut_file"]] = e
    seen = set(); entries = []
    for p in sorted(a.filmluts.rglob("*.cube")):
        rel = p.relative_to(a.filmluts).as_posix(); meta, data = parse_cube(p)
        if meta["errors"]: continue
        h = hashlib.sha256(json.dumps([[round(x, 4) for x in r] for r in data]).encode()).hexdigest()[:16]
        if h in seen: continue
        seen.add(h); e = idx.get(rel, {})
        cat = e.get("category") or p.parent.name; name = e.get("name") or p.stem.replace("_", " ").title()
        cube = np.array(data).reshape(13, 13, 13, 3)
        cands = [q for q in halds if hald_key(q.stem) == cube_key(p.stem)]
        best = min(cands, key=lambda q: float(np.sqrt(((h13[q] - cube) ** 2).mean()))) if cands else None
        rmse = float(np.sqrt(((h13[best] - cube) ** 2).mean())) if best else None
        entries.append(dict(id=lut_id(cat, p.stem), name=name, category=cat, hald=best, rmse=rmse, src=rel))
    used = {e["hald"] for e in entries if e["hald"]}
    out = a.out; out.mkdir(parents=True, exist_ok=True)
    catalog = []; report = []; unmatched = []
    if only:
        entries = [e for e in entries if e["id"] in only]
        assert {e["id"] for e in entries} == only, only - {e["id"] for e in entries}
    if a.identity:
        q, _ = quant(grid(a.size), a.bits); f = out / f"identity.{ext}"; write(f, q, a.bits)
        catalog.append({"id": "identity", "name": "Identity", "displayName": "Bez filtra", "category": "identity", "size": a.size, "format": ext,
                        "bytes": f.stat().st_size, "file": f"luts/identity.{ext}", "source_hald": None, "license": "CC0 1.0 (siatka tozsamosciowa)",
                        "sha256": hashlib.sha256(f.read_bytes()).hexdigest(), "approx": False, "available": True})
    for e in entries:
        if e["hald"] is None: unmatched.append({"id": e["id"], "source_file": e["src"], "reason": "brak HaldCLUT o tej nazwie w HaldCLUT.zip"}); continue
        hald = load_hald(e["hald"]); lut = trilinear(hald, grid(a.size))
        bits = a.bits; q, deq = quant(lut, bits); st = evaluate(hald, deq); st16 = None
        if bits == 8 and st["max"] >= 1:  # informacyjnie: czy RGB16 dalby max < 1
            q16, deq16 = quant(lut, 16); st16 = evaluate(hald, deq16)
        f = out / f"{e['id']}.{ext}"; f.parent.mkdir(parents=True, exist_ok=True); write(f, q, bits)
        hrel = e["hald"].relative_to(a.hald).as_posix()
        catalog.append({"id": e["id"], "name": e["name"], "category": e["category"], "size": a.size, "format": ext, "bytes": a.size ** 3 * 3 * bits // 8,
                        "file": f"luts/{e['id']}.{ext}", "source_hald": hrel, "license": "CC BY-SA 4.0",
                        "sha256": hashlib.sha256(f.read_bytes()).hexdigest()})
        report.append({"id": e["id"], "source_hald": hrel, "match_rmse_13": round(e["rmse"], 5), "bits": bits, "deltaE2000": st, "deltaE2000_rgb16": st16,
                       "pass": st["max"] < 1, "pass_rgb16": None if st16 is None else st16["max"] < 1})
        print(e["id"], bits, st, st16, flush=True)
    halds_unused = [p.relative_to(a.hald).as_posix() for p in halds if p not in used]
    json.dump({"format": f"{ext} (raw, bez naglowka, R najszybciej" + (", uint16 LE)" if a.bits == 16 else ")"), "size": a.size, "luts": catalog}, (out / "catalog.json").open("w"), ensure_ascii=False, indent=1)
    worst = max(report, key=lambda r: r["deltaE2000"]["max"])
    json.dump({"method": f"dE2000, sRGB(D65)->Lab, {a.size}^3 po kwantyzacji + trilinear vs HaldCLUT 144^3, wszystkie 144^3 punktow",
               "count": len(report), "all_pass": all(r["pass"] for r in report), "pass_count": sum(r["pass"] for r in report),
               "global": {"mean_of_means": round(float(np.mean([r["deltaE2000"]["mean"] for r in report])), 4),
                          "max_p99": max(r["deltaE2000"]["p99"] for r in report), "max_max": worst["deltaE2000"]["max"]}, "worst": worst,
               "unmatched_catalog": unmatched, "hald_not_in_catalog": halds_unused, "luts": report},
              (out / "report.json").open("w"), ensure_ascii=False, indent=1)
    files = sorted(p for p in out.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
    (out / "SHA256SUMS").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(out).as_posix()}\n" for p in files))
    if a.golden:
        a.golden.mkdir(parents=True, exist_ok=True)
        n = 17; img = (np.round(grid(n) * 255)).astype(np.uint8)  # [b,g,r] -> obraz 289x17: wiersz = b*17+g, kolumna = r
        Image.fromarray(img.reshape(n * n, n, 3)).save(a.golden / "grid17.png")
        picks = catalog[::max(1, len(catalog) // 5)][:5]
        meta = []
        for c in picks[:5]:
            q = np.frombuffer((out / f"{c['id']}.rgb8").read_bytes(), dtype=np.uint8)
            l = q.reshape(a.size, a.size, a.size, 3) / 255.0
            res = np.round(np.clip(trilinear(l, img / 255.0), 0, 1) * 255).astype(np.uint8)
            fn = c["id"].replace("/", "__") + ".png"; Image.fromarray(res.reshape(n * n, n, 3)).save(a.golden / fn)
            meta.append({"lut": c["id"], "input": "grid17.png", "expected": fn})
        json.dump({"layout": "17^3 siatka: wiersz y = b*17+g, kolumna x = r; wartosci round(i*255/16); wynik = trilinear na LUT 33^3 (po dekwantyzacji), round do 8 bit; tolerancja testu <=1 poziom",
                   "cases": meta}, (a.golden / "golden.json").open("w"), ensure_ascii=False, indent=1)
    print("OK", len(catalog), "unmatched", len(unmatched), "worst", worst["id"], worst["deltaE2000"])

if __name__ == "__main__": sys.exit(main())
