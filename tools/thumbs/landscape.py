#!/usr/bin/env python3
"""Deterministyczny krajobraz (bez twarzy) jako zdjecie wzorcowe miniatur filmow – Retro-Film (Jack4B).

Wlasna, proceduralna scena (zadnych cudzych zdjec, wiec bez kwestii licencyjnych): niebo z chmurami
i sloncem, gory w mgle, jezioro z odbiciem, piasek, laka z kwiatami, drzewa zielone i jesienne.
Duzo odcieni (niebieskie, zielenie, cieple swiatlo, czerwienie, fiolety), zeby roznice miedzy filmami
byly dobrze widoczne. Uzycie: python3 tools/thumbs/landscape.py out.png [--size 512]
Prawdziwe zdjecie CC0 mozna podac w make_thumbs.py przez --photo (ma pierwszenstwo).
"""
import argparse

import numpy as np
from PIL import Image


def noise(rng, h, w, cells):
    """Szum wartosciowy: losowa siatka cells x cells rozciagnieta bikubicznie do h x w (0..1)."""
    grid = rng.random((cells, cells)).astype(np.float32)
    im = Image.fromarray((grid * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, dtype=np.float32) / 255.0


def fbm(rng, h, w, base=4, octaves=5):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += amp * noise(rng, h, w, base * 2**o)
        total += amp
        amp *= 0.5
    return out / total


def scene(size: int) -> np.ndarray:
    rng = np.random.default_rng(7)
    h = w = size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    y /= h - 1
    x /= w - 1
    horizon = 0.52
    img = np.zeros((h, w, 3), np.float32)

    # niebo: gradient + poswiata slonca + chmury
    top, low = np.array([0.18, 0.36, 0.78]), np.array([0.96, 0.78, 0.62])
    t = np.clip(y / horizon, 0, 1)[..., None] ** 0.8
    sky = top * (1 - t) + low * t
    sun_x, sun_y = 0.72, 0.36
    d = np.sqrt((x - sun_x) ** 2 + ((y - sun_y) * 1.2) ** 2)
    sky += np.exp(-d * 7)[..., None] * np.array([0.55, 0.38, 0.18])
    sky = np.where((d < 0.028)[..., None], np.array([1.0, 0.97, 0.85]), sky)
    clouds = np.clip((fbm(rng, h, w, 3, 5) - 0.56) * 3.0, 0, 1) * np.clip(1 - y / horizon, 0, 1) ** 0.5
    cloud_col = np.array([1.0, 0.90, 0.85]) * (0.78 + 0.22 * fbm(rng, h, w, 6, 3))[..., None]
    sky = sky * (1 - clouds[..., None] * 0.6) + cloud_col * clouds[..., None] * 0.85
    img[:] = sky

    # gory w mgle (dwie warstwy)
    for k, (col, base, amp) in enumerate([((0.45, 0.50, 0.68), 0.40, 0.09), ((0.28, 0.36, 0.50), 0.455, 0.07)]):
        ridge = base + amp * (fbm(rng, 1, w, 3, 6)[0] - 0.5) * 2
        mask = (y > ridge[None, :]) & (y < horizon + 0.01)
        shade = np.array(col) * (0.85 + 0.3 * fbm(rng, h, w, 8, 3))[..., None]
        img = np.where(mask[..., None], shade, img)

    # jezioro z odbiciem nieba i blaskiem slonca
    water = (y >= horizon) & (y < 0.70)
    refl = np.clip((y - horizon) / (0.70 - horizon), 0, 1)
    ripple = 0.5 + 0.5 * np.sin(y * 420 + fbm(rng, h, w, 10, 2) * 9)
    wc = np.array([0.20, 0.42, 0.62]) * (1 - refl[..., None]) + np.array([0.08, 0.24, 0.40]) * refl[..., None]
    wc = wc * (0.9 + 0.2 * ripple[..., None])
    glitter = np.exp(-((x - sun_x) ** 2) * 60)[..., None] * (ripple[..., None] ** 6) * np.array([0.9, 0.7, 0.4])
    img = np.where(water[..., None], np.clip(wc + glitter, 0, 1), img)

    # piasek / kamienie (cieple, neutralne szarosci)
    sand = (y >= 0.70) & (y < 0.76)
    sc = np.array([0.80, 0.68, 0.50]) * (0.85 + 0.3 * fbm(rng, h, w, 14, 3))[..., None]
    img = np.where(sand[..., None], sc, img)

    # laka: zielen z odcieniami + kwiaty
    meadow = y >= 0.76
    g = fbm(rng, h, w, 10, 4)[..., None]
    mc = np.array([0.16, 0.42, 0.12]) * (0.7 + 0.8 * g) + np.array([0.18, 0.12, 0.0]) * (y[..., None] - 0.76) * 2
    img = np.where(meadow[..., None], mc, img)
    palette = np.array([[0.88, 0.12, 0.12], [0.98, 0.84, 0.15], [0.62, 0.25, 0.75], [0.97, 0.97, 0.95], [0.95, 0.45, 0.12]])
    for _ in range(int(size * 0.45)):
        cx, cy = rng.random(), 0.77 + 0.23 * rng.random() ** 0.8
        r = (0.0025 + 0.007 * (cy - 0.76) / 0.24) * (1 + rng.random())
        m = ((x - cx) ** 2 + (y - cy) ** 2) < r * r
        img[m] = palette[rng.integers(0, len(palette))]

    # drzewa: ciemne iglaste po lewej, jesienne (pomaranczowe) po prawej
    def conifer(cx, base_y, ht, shade):
        wd = ht * 0.32
        row = np.clip((y - (base_y - ht)) / ht, 0, 1)
        m = (y > base_y - ht) & (y < base_y) & (np.abs(x - cx) < wd * row)
        return m, np.array([0.05, 0.22, 0.12]) * shade * (0.8 + 0.4 * (1 - row))[..., None]

    for cx, ht, sh in [(0.07, 0.34, 1.0), (0.17, 0.27, 0.9), (0.26, 0.22, 1.1), (0.34, 0.16, 0.95)]:
        m, c = conifer(cx, 0.78, ht, sh)
        img = np.where(m[..., None], c, img)
    for cx, rad, col in [(0.90, 0.10, (0.88, 0.40, 0.08)), (0.80, 0.07, (0.80, 0.22, 0.10)), (0.96, 0.08, (0.95, 0.65, 0.12))]:
        cy = 0.70
        m = ((x - cx) ** 2 + ((y - cy) * 1.1) ** 2) < rad * rad
        trunk = (np.abs(x - cx) < 0.006) & (y >= cy) & (y < 0.78)
        leaf = np.array(col) * (0.8 + 0.4 * fbm(rng, h, w, 18, 3))[..., None]
        img = np.where(m[..., None], leaf, img)
        img = np.where(trunk[..., None], np.array([0.25, 0.15, 0.08]), img)

    # lekkie ziarno swiatla i winieta (zeby zdjecie wygladalo naturalnie)
    img *= (1 - 0.18 * ((x - 0.5) ** 2 + (y - 0.5) ** 2))[..., None] * 1.05
    return np.clip(img, 0, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--size", type=int, default=512)
    a = ap.parse_args()
    Image.fromarray((scene(a.size) * 255 + 0.5).astype(np.uint8)).save(a.out)


if __name__ == "__main__":
    main()
