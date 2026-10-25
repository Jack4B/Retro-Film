#!/usr/bin/env python3
"""Porownanie na zdjeciach: HaldCLUT 144^3 (oryginal) | LUT 33^3 RGB8 (trojliniowo, jak CpuLut) | |roznica| x10.

Uzycie: python3 tools/photocompare.py <katalog_HaldCLUT> tools/packs/catalog-33-full.json tools/golden
Wynik: tools/golden/compare/photos/<film>__<zdjecie>.jpg (JPEG q95) + stats.json + README.md
ΔE2000 (sRGB D65 -> Lab) liczone na wyjsciach float (przed kwantyzacja do 8 bit).
"""
import json, sys
from pathlib import Path
import numpy as np
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
from hald2lut import load_hald, trilinear, grid, quant, lab, de2000  # noqa: E402

FILMS = ["colorslide/fuji_superia_200_xpro", "bw/fuji_neopan_1600", "negative_old/fuji_neopan_1600_p",
         "negative_old/fuji_neopan_1600_pp", "instant_consumer/polaroid_px_70_cold_p"]
PHOTOS = ["sky", "skin", "green"]
ROWS = ["gora", "srodek", "dol"]; COLS = ["lewo", "srodek", "prawo"]

def u8(a): return (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)

def regions(mask):
    h, w = mask.shape; out = {}
    for i, rn in enumerate(ROWS):
        for j, cn in enumerate(COLS):
            n = int(mask[i * h // 3:(i + 1) * h // 3, j * w // 3:(j + 1) * w // 3].sum())
            if n: out[f"{rn}-{cn}"] = n
    return out

def main():
    hald, assets, golden = map(Path, sys.argv[1:4])
    cat = {c["id"]: c for c in json.load(assets.open())["luts"]}
    od = golden / "compare" / "photos"; od.mkdir(parents=True, exist_ok=True)
    imgs = {p: np.asarray(Image.open(golden / "photos" / f"{p}.jpg").convert("RGB"), np.float64) / 255 for p in PHOTOS}
    stats = {}
    for f in FILMS:
        h = load_hald(hald / cat[f]["source_hald"])
        # 33^3 RGB8 liczone tak samo jak w hald2lut.py (bitowo identyczne z paczka)
        l33 = quant(trilinear(h, grid(33)), 8)[1]
        for p, img in imgs.items():
            a = trilinear(h, img); b = trilinear(l33, img)
            d = de2000(lab(np.clip(a, 0, 1)), lab(np.clip(b, 0, 1)))
            m3 = d > 3; m2 = d > 2
            stats.setdefault(f, {})[p] = {
                "mean": round(float(d.mean()), 3), "p99": round(float(np.percentile(d, 99)), 3),
                "p999": round(float(np.percentile(d, 99.9)), 3), "max": round(float(d.max()), 3),
                "px": int(d.size), "gt2": int(m2.sum()), "gt3": int(m3.sum()),
                "gt3_pct": round(100 * float(m3.mean()), 4), "gt3_regions": regions(m3),
                "u8_maxdiff": int(np.abs(u8(a).astype(int) - u8(b).astype(int)).max())}
            diff = np.clip(np.abs(a - b) * 10, 0, 1)
            H, W = img.shape[:2]
            c = np.concatenate([u8(a), u8(b), u8(diff)], 1)
            fn = od / f"{f.replace('/', '__')}__{p}.jpg"
            Image.fromarray(c).save(fn, quality=95)
            stats[f][p]["file"] = fn.name; stats[f][p]["bytes"] = fn.stat().st_size
            print(f, p, stats[f][p], flush=True)
    (od / "stats.json").write_text(json.dumps(stats, indent=1, ensure_ascii=False) + "\n")

if __name__ == "__main__":
    main()
