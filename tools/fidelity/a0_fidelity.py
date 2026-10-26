#!/usr/bin/env python3
"""A0: pomiar wierności filmów (HaldCLUT) w potoku aplikacji.

Mierzy trzy rzeczy, które mogą psuć wierność wyniku względem pełnego LUT-a:
  1. luki poziomów: ile różnych wartości 8-bit zostaje po przejściu osi neutralnej przez LUT (wzmocnienie schodków);
  2. podwójna kwantyzacja: film -> 8 bit -> korekta -> 8 bit  vs  film -> korekta -> jedna kwantyzacja (float);
  3. przycinanie gamutu: kolory Display P3 spoza sRGB przycięte kanałami vs mapowane w OKLab (stałe L i odcień).

Użycie: a0_fidelity.py <katalog_z_PNG_HaldCLUT> <zdjęcie.jpg> [--max-side 1200]
Zależności: numpy, Pillow. Wynik: tekst na stdout.
"""
import argparse
import glob
import os

import numpy as np
from PIL import Image


def load_cube(path):
    """HaldCLUT PNG (bok = N^1.5) -> kostka [b][g][r] w float32 0..1 (R zmienia się najszybciej)."""
    im = np.asarray(Image.open(path).convert("RGB"))
    n = int(round(im.shape[0] ** (2 / 3)))
    return im.reshape(n, n, n, 3).astype(np.float32) / 255.0


def apply_lut(cube, rgb):
    """Interpolacja trójliniowa; rgb: (..., 3) float 0..1 (kodowanie sRGB) -> float 0..1."""
    n = cube.shape[0]
    p = np.clip(rgb, 0, 1) * (n - 1)
    i0 = np.minimum(p.astype(np.int32), n - 2)
    f = p - i0
    r0, g0, b0 = i0[..., 0], i0[..., 1], i0[..., 2]
    fr, fg, fb = f[..., 0:1], f[..., 1:2], f[..., 2:3]
    out = 0
    for db in (0, 1):
        for dg in (0, 1):
            for dr in (0, 1):
                w = (fr if dr else 1 - fr) * (fg if dg else 1 - fg) * (fb if db else 1 - fb)
                out = out + w * cube[b0 + db, g0 + dg, r0 + dr]
    return out


def level_gaps(cube):
    """Oś neutralna: 256 wejść -> ile różnych wyjść 8-bit na kanał, ile skoków >= 2 poziomy."""
    t = np.linspace(0, 1, 256, dtype=np.float32)
    out = np.round(apply_lut(cube, np.stack([t, t, t], -1)) * 255).astype(int)
    res = []
    for c in range(3):
        o = out[:, c]
        d = np.diff(o)
        res.append((len(np.unique(o)), float((np.abs(d) >= 2).mean()), int(np.abs(d).max())))
    return res


def correction(x):
    """Łagodna korekta testowa: cienie +, kontrast + (krzywa gładka, działa na kanałach 0..1)."""
    y = np.clip(x, 0, 1)
    lifted = y + 0.10 * (1 - y) ** 3 * y * 4
    return np.clip(0.5 + (lifted - 0.5) * 1.12, 0, 1)


def double_quantization(cube, photo):
    film = apply_lut(cube, photo)
    a = np.round(correction(np.round(film * 255) / 255) * 255)
    b = np.round(correction(film) * 255)
    d = np.abs(a - b)
    return float(d.mean()), float(np.percentile(d, 99)), float(d.max())


# --- OKLab i gamut -------------------------------------------------------------------------------
M1 = np.array([[0.4122214708, 0.5363325363, 0.0514459929], [0.2119034982, 0.6806995451, 0.1073969566],
               [0.0883024619, 0.2817188376, 0.6299787005]])
M2 = np.array([[0.2104542553, 0.7936177850, -0.0040720468], [1.9779984951, -2.4285922050, 0.4505937099],
               [0.0259040371, 0.7827717662, -0.8086757660]])
I2 = np.linalg.inv(M2)
I1 = np.linalg.inv(M1)
P3_TO_SRGB = np.array([[3.2404542, -1.5371385, -0.4985314], [-0.9692660, 1.8760108, 0.0415560],
                       [0.0556434, -0.2040259, 1.0572252]]) @ np.array(
    [[0.4865709, 0.2656677, 0.1982173], [0.2289746, 0.6917385, 0.0792869], [0.0, 0.0451134, 1.0439444]])


def to_linear(v):
    return np.where(v <= 0.04045, v / 12.92, ((v + 0.055) / 1.055) ** 2.4)


def to_gamma(v):
    v = np.clip(v, 0, 1)
    return np.where(v <= 0.0031308, 12.92 * v, 1.055 * v ** (1 / 2.4) - 0.055)


def lin_to_oklab(lin):
    lms = np.cbrt(lin @ M1.T)
    return lms @ M2.T


def oklab_to_lin(lab):
    return (lab @ I2.T) ** 3 @ I1.T


def hue_deg(lab):
    return np.degrees(np.arctan2(lab[..., 2], lab[..., 1])) % 360


def gamut_map(lin):
    """Chroma zmniejszana bisekcją przy stałym L i odcieniu, aż kolor mieści się w sRGB."""
    lab = lin_to_oklab(lin)
    lo = np.zeros(len(lab))
    hi = np.ones(len(lab))
    for _ in range(14):
        mid = (lo + hi) / 2
        cand = lab.copy()
        cand[:, 1:] *= mid[:, None]
        ok = np.all((oklab_to_lin(cand) >= -1e-4) & (oklab_to_lin(cand) <= 1 + 1e-4), axis=1)
        lo = np.where(ok, mid, lo)
        hi = np.where(ok, hi, mid)
    out = lab.copy()
    out[:, 1:] *= lo[:, None]
    return np.clip(oklab_to_lin(out), 0, 1)


def gamut_clip_report(cube, n=200000, seed=3):
    rnd = np.random.default_rng(seed)
    p3 = rnd.random((n, 3))
    lin = to_linear(p3) @ P3_TO_SRGB.T
    outside = np.any((lin < 0) | (lin > 1), axis=1)
    frac = float(outside.mean())
    lin = lin[outside][:20000]
    ref_lab = lin_to_oklab(np.clip(lin, 1e-6, None))
    clipped = np.clip(lin, 0, 1)
    mapped = gamut_map(np.clip(lin, 1e-6, None))
    hs_clip = np.abs((hue_deg(lin_to_oklab(np.clip(clipped, 1e-6, None))) - hue_deg(ref_lab) + 180) % 360 - 180)
    hs_map = np.abs((hue_deg(lin_to_oklab(np.clip(mapped, 1e-6, None))) - hue_deg(ref_lab) + 180) % 360 - 180)
    # wpływ na wynik filmu: te same kolory przez LUT po przycięciu i po mapowaniu (kodowanie sRGB)
    f_clip = apply_lut(cube, to_gamma(clipped).astype(np.float32))
    f_map = apply_lut(cube, to_gamma(mapped).astype(np.float32))
    d = np.abs(f_clip - f_map) * 255
    return frac, np.percentile(hs_clip, [50, 95, 99]), np.percentile(hs_map, [50, 95, 99]), float(d.mean()), float(np.percentile(d, 95))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("luts")
    ap.add_argument("photo")
    ap.add_argument("--max-side", type=int, default=1200)
    a = ap.parse_args()
    im = Image.open(a.photo).convert("RGB")
    s = min(1.0, a.max_side / max(im.size))
    if s < 1:
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    photo = np.asarray(im).astype(np.float32) / 255.0
    print("film | poziomy R/G/B po osi szarości (z 256) | skoki>=2 % (R) | maks. skok | 2x kwant.: średnia/p99/max (poziomy)")
    for path in sorted(glob.glob(os.path.join(a.luts, "*.png"))):
        cube = load_cube(path)
        g = level_gaps(cube)
        dq = double_quantization(cube, photo)
        print("%-22s N=%3d | %3d/%3d/%3d | %5.1f%% | %d | %.2f / %.0f / %.0f" % (
            os.path.basename(path)[:-4], cube.shape[0], g[0][0], g[1][0], g[2][0], 100 * g[0][1], max(x[2] for x in g),
            *dq))
    color = [f for f in sorted(glob.glob(os.path.join(a.luts, "*.png"))) if "portra_400" in f] or sorted(
        glob.glob(os.path.join(a.luts, "*.png")))
    cube = load_cube(color[0])
    frac, hc, hm, dm, d95 = gamut_clip_report(cube)
    print("\nGamut P3 -> sRGB (film: %s; losowe kolory kostki P3): poza sRGB %.1f%%" % (os.path.basename(color[0]), 100 * frac))
    print("zmiana odcienia przy przycinaniu kanałami: p50/p95/p99 = %.1f / %.1f / %.1f stopni" % tuple(hc))
    print("zmiana odcienia przy mapowaniu OKLab:      p50/p95/p99 = %.1f / %.1f / %.1f stopni" % tuple(hm))
    print("różnica wyniku filmu (przycięcie vs mapowanie), poziomy 0..255: średnia %.1f, p95 %.1f" % (dm, d95))


if __name__ == "__main__":
    main()
