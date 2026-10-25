#!/usr/bin/env python3
"""Golden PNG 17^3 dla LUT-ow z APK (identity + 10 filmow startowych, core/engine/src/main/assets/luts).
D9: filmy startowe to HaldCLUT PNG 144^3 (format png144, bajty 1:1 z Jack4B/Retro-Film-LUTs@0432c5a);
identity zostaje rgb8 33^3 (tozsamosc jest dokladna przy kazdej siatce). Test: core/engine/src/test/.../GoldenLutTest.kt (tolerancja <=1 poziom).

Uzycie: python3 tools/golden/make_golden.py [--assets DIR] [--out tools/golden]
"""
import argparse, hashlib, io, json
from pathlib import Path
import numpy as np
from PIL import Image

def trilinear(lut, pts):
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

def main():
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", type=Path, default=root / "core/engine/src/main/assets/luts")
    ap.add_argument("--out", type=Path, default=root / "tools/golden")
    a = ap.parse_args()
    n = 17; v = np.round(np.arange(n) * 255 / (n - 1)).astype(np.uint8)
    b, g, r = np.meshgrid(v, v, v, indexing="ij"); img = np.stack([r, g, b], -1)  # [b,g,r,3]
    Image.fromarray(img.reshape(n * n, n, 3)).save(a.out / "grid17.png")
    cases = []
    for c in json.load((a.assets / "catalog.json").open())["luts"]:
        data = (a.assets.parent / c["file"]).read_bytes()
        assert hashlib.sha256(data).hexdigest() == c["sha256"], c["id"]
        s = c.get("grid", c["size"])
        if c.get("format", "rgb8") in ("png144", "png-hald"):
            img_h = np.asarray(Image.open(io.BytesIO(data)).convert("RGB"), np.uint8)  # Hald: wiersze, r najszybciej
            assert img_h.shape[0] == img_h.shape[1] and round(img_h.shape[0] ** (1 / 3)) ** 2 == s, c["id"]
            raw = img_h.reshape(-1)
        else:
            raw = np.frombuffer(data, np.uint8)
        assert raw.size == c.get("unpackedLength", s ** 3 * 3), c["id"]
        lut = raw.reshape(s, s, s, 3) / 255.0
        res = np.round(np.clip(trilinear(lut, img / 255.0), 0, 1) * 255).astype(np.uint8)
        fn = c["id"].replace("/", "__") + ".png"
        Image.fromarray(res.reshape(n * n, n, 3)).save(a.out / fn)
        cases.append({"lut": c["id"], "file": c["file"], "input": "grid17.png", "expected": fn})
    json.dump({"layout": "17^3 siatka: wiersz y = b*17+g, kolumna x = r; wartosci round(i*255/16); wynik = trilinear na siatce LUT z APK (filmy: Hald PNG 144^3, identity: rgb8 33^3; po dekwantyzacji), round do 8 bit; tolerancja testu <=1 poziom",
               "source": "LUT-y z APK (core/engine/src/main/assets/luts/catalog.json): identity + filmy startowe; generator tools/golden/make_golden.py",
               "cases": cases}, (a.out / "golden.json").open("w"), ensure_ascii=False, indent=1)
    print(len(cases), "przypadkow")

if __name__ == "__main__":
    main()
