#!/usr/bin/env python3
"""Ewaluacja LUT-ow Retro-Film (Jack4B): dE2000 przy pelnych 144^3 punktach dla
33^3 (pliki .rgb8 z assetow) i 48^3 (tylko statystyki), interpolacja trilinearna
(jak CpuLut w core:engine, PR #5 - metoda glowna) i tetraedryczna (dodatkowo).
Kryterium D7 (proponowane): mean < 0.5 i p99 < 1.5; max informacyjnie, approx gdy max > 3.
Uzycie: luteval.py <HaldCLUT/> <katalog paczki z hald2lut.py --out (pelna paczka, nie w repo)> [--golden tools/golden] [-j 4]
"""
import argparse, json, sys
from multiprocessing import Pool
from pathlib import Path
import numpy as np
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
from hald2lut import load_hald, trilinear, grid, lab, de2000, quant  # noqa: E402

D7_MEAN, D7_P99, APPROX_MAX = 0.5, 1.5, 3.0

def tetrahedral(lut, pts):
    """lut[b,g,r,3], pts (...,3) rgb 0..1; klasyczna interpolacja czworoscienna."""
    n = lut.shape[0]; x = np.clip(pts, 0, 1) * (n - 1)
    i0 = np.minimum(np.floor(x).astype(np.int64), n - 2); f = x - i0
    r, g, b = i0[..., 0], i0[..., 1], i0[..., 2]
    fr, fg, fb = f[..., 0:1], f[..., 1:2], f[..., 2:3]
    V = lambda dr, dg, db: lut[b + db, g + dg, r + dr]
    c000, c111 = V(0, 0, 0), V(1, 1, 1)
    c100, c010, c001 = V(1, 0, 0), V(0, 1, 0), V(0, 0, 1)
    c110, c101, c011 = V(1, 1, 0), V(1, 0, 1), V(0, 1, 1)
    out = np.empty_like(c000)
    cases = [  # (warunek, wierzcholek1, wierzcholek2, wagi)
        ((fr >= fg) & (fg >= fb), c100, c110, (fr, fg, fb)),
        ((fr >= fb) & (fb > fg), c100, c101, (fr, fb, fg)),
        ((fb > fr) & (fr >= fg), c001, c101, (fb, fr, fg)),
        ((fg > fr) & (fr >= fb), c010, c110, (fg, fr, fb)),
        ((fg >= fb) & (fb > fr), c010, c011, (fg, fb, fr)),
        ((fb > fg) & (fg > fr), c001, c011, (fb, fg, fr)),
    ]
    done = np.zeros(fr.shape, bool)
    for cond, v1, v2, (a, bb, c) in cases:
        m = cond & ~done
        val = (1 - a) * c000 + (a - bb) * v1 + (bb - c) * v2 + c * c111
        out = np.where(m, val, out); done |= m
    return out

def stats(d):
    return dict(mean=round(float(d.mean()), 4), p99=round(float(np.percentile(d, 99)), 4), max=round(float(d.max()), 4))

def evaluate(ref, lut, interp):
    n = ref.shape[0]; pts = grid(n); out = []
    for bi in range(0, n, 16):
        got = np.clip(interp(lut, pts[bi:bi + 16]), 0, 1)
        out.append(de2000(lab(ref[bi:bi + 16]), lab(got)).ravel())
    return stats(np.concatenate(out))

def d7(s): return s["mean"] < D7_MEAN and s["p99"] < D7_P99

def work(args):
    lid, hald_path, rgb8 = args
    h = load_hald(hald_path)
    l33 = np.frombuffer(Path(rgb8).read_bytes(), np.uint8).reshape(33, 33, 33, 3) / 255.0
    l48 = quant(trilinear(h, grid(48)), 8)[1]
    r = {"id": lid}
    for tag, l in (("33", l33), ("48", l48)):
        r[tag] = {"trilinear": evaluate(h, l, trilinear), "tetrahedral": evaluate(h, l, tetrahedral)}
    print(lid, r["33"]["trilinear"], flush=True)
    return r

def synth(w=192, h=96):
    """Obraz testowy: gora - odcienie x jasnosc, dol - nasycenie i rampa szarosci."""
    import colorsys
    a = np.zeros((h, w, 3))
    for y in range(h):
        for x in range(w):
            if y < h * 2 // 3:
                a[y, x] = colorsys.hsv_to_rgb(x / w, 0.85, 1 - y / (h * 2 / 3) * 0.85)
            elif y < h * 5 // 6:
                a[y, x] = colorsys.hsv_to_rgb(0.07, x / w * 0.6, 0.85)  # tony skory
            else:
                a[y, x] = x / (w - 1)
    return a

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("hald", type=Path); ap.add_argument("assets", type=Path)
    ap.add_argument("--golden", type=Path); ap.add_argument("-j", type=int, default=4)
    a = ap.parse_args()
    cat = json.load((a.assets / "catalog.json").open())
    jobs = [(c["id"], str(a.hald / c["source_hald"]), str(a.assets / f"{c['id']}.rgb8")) for c in cat["luts"]]
    with Pool(a.j) as p: res = {r["id"]: r for r in p.map(work, jobs)}
    # identity
    ident = quant(grid(33), 8)[1]; ref = grid(144)
    idr = {"trilinear": evaluate(ref, ident, trilinear), "tetrahedral": evaluate(ref, ident, tetrahedral)}
    idr["pass"] = idr["trilinear"]["max"] < 1 and idr["tetrahedral"]["max"] < 1
    rep = json.load((a.assets / "report.json").open())
    old = {x["id"]: x for x in rep["luts"]}
    luts = []; summary = {}
    for c in cat["luts"]:
        r = res[c["id"]]; o = old[c["id"]]
        prim = r["33"]["trilinear"]
        luts.append({"id": c["id"], "source_hald": c["source_hald"],
                     "deltaE2000": prim, "deltaE2000_tetrahedral": r["33"]["tetrahedral"],
                     "deltaE2000_rgb16_trilinear": o.get("deltaE2000_rgb16"),
                     "grid48": r["48"], "d7_pass": d7(prim), "approx": prim["max"] > APPROX_MAX})
    for g in ("33", "48"):
        for m in ("trilinear", "tetrahedral"):
            s = [res[c["id"]][g][m] for c in cat["luts"]]
            worst = sorted(((res[c["id"]][g][m]["max"], c["id"]) for c in cat["luts"]), reverse=True)[:5]
            summary[f"{g}_{m}"] = {"d7_pass": sum(d7(x) for x in s), "max_lt_1": sum(x["max"] < 1 for x in s),
                                   "mean_of_means": round(float(np.mean([x["mean"] for x in s])), 4),
                                   "worst5_by_max": [{"id": i, **res[i][g][m]} for _, i in worst]}
    summary["48_pack_bytes"] = len(cat["luts"]) * 48 ** 3 * 3
    summary["33_pack_bytes"] = len(cat["luts"]) * 33 ** 3 * 3
    out = {"method": "dE2000 (CIEDE2000), sRGB D65 -> Lab; LUT po kwantyzacji RGB8, interpolowany i porownany z HaldCLUT 144^3 we wszystkich 2985984 punktach. "
                     "Metoda glowna: trilinearna (jak CpuLut w core:engine, PR #5); tetraedryczna jako dodatkowa kolumna. 48^3: tylko statystyki (pliki nie w repo).",
           "primary_interpolation": "trilinear",
           "criterion": {"id": "D7 (proponowane, czeka na Jack4B)", "pass_if": f"mean < {D7_MEAN} i p99 < {D7_P99} (33^3, trilinear)",
                         "max": "informacyjnie; approx=true gdy max > 3"},
           "identity_33": idr, "count": len(luts), "d7_pass": [x["id"] for x in luts if x["d7_pass"]],
           "d7_fail": [x["id"] for x in luts if not x["d7_pass"]], "summary": summary,
           "unmatched_catalog": rep.get("unmatched_catalog"), "hald_not_in_catalog": rep.get("hald_not_in_catalog"), "luts": luts}
    json.dump(out, (a.assets / "report.json").open("w"), ensure_ascii=False, indent=1)
    for c in cat["luts"]: c["approx"] = res[c["id"]]["33"]["trilinear"]["max"] > APPROX_MAX
    json.dump(cat, (a.assets / "catalog.json").open("w"), ensure_ascii=False, indent=1)
    if a.golden:
        d = a.golden / "compare"; d.mkdir(parents=True, exist_ok=True)
        img = synth(); worst = summary["33_trilinear"]["worst5_by_max"]
        Image.fromarray(np.round(img * 255).astype(np.uint8)).save(d / "input.png")
        for w in worst:
            c = next(x for x in cat["luts"] if x["id"] == w["id"])
            h = load_hald(a.hald / c["source_hald"])
            l33 = np.frombuffer((a.assets / f"{c['id']}.rgb8").read_bytes(), np.uint8).reshape(33, 33, 33, 3) / 255.0
            o = trilinear(h, img); t = trilinear(l33, img)
            diff = np.clip(np.abs(o - t) * 10, 0, 1)
            sep = np.ones((img.shape[0], 4, 3))
            row = np.concatenate([o, sep, t, sep, diff], 1)
            Image.fromarray(np.round(np.clip(row, 0, 1) * 255).astype(np.uint8)).save(d / (c["id"].replace("/", "__") + ".png"))
        (d / "README.md").write_text("Lewo: oryginalny HaldCLUT 144^3, srodek: 33^3 RGB8 (trilinear), prawo: |roznica| x10.\n"
                                     "Wejscie: input.png (syntetyczne). 5 LUT-ow o najwiekszym max dE (33^3 trilinear).\n")
    print(json.dumps(summary, indent=1)); print("identity", idr)

if __name__ == "__main__": sys.exit(main())
