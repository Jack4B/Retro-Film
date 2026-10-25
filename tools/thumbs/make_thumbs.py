#!/usr/bin/env python3
"""Miniatury filmow (JPG) do APK dla filmow do pobrania (D12) – Retro-Film (Jack4B).

Dla kazdego HaldCLUT PNG (144^3, wyjatkowo 256^3) naklada film trojliniowo na wspolne zdjecie wzorcowe
(ta sama interpolacja co silnik) i zapisuje `<kategoria>/<nazwa>.jpg` + `thumbs.json` (bajty, sha256).

Wejscie: katalog z `<kategoria>/<nazwa>.png` (np. `luts/` z Jack4B/Retro-Film-LUTs@luts-v1 albo
`core/engine/src/main/assets/luts/`). Zdjecie: `--photo` (zalecane CC0, np. z tools/golden/fetch_photos.py);
bez `--photo` uzywana jest deterministyczna scena testowa (niebo, skora, zielen, szarosci, plansza barw).

Uzycie:
  python3 tools/thumbs/make_thumbs.py <katalog_png> --out <katalog_wyjsciowy> [--photo foto.jpg]
         [--size 160] [--quality 78] [--only id1,id2] [--budget-mb 5]
Budzet APK (D12): ok. 4 MB dla 260 filmow; skrypt konczy sie bledem, gdy suma przekroczy --budget-mb.
Wymaga numpy + Pillow. Wynik nie jest zapisywany w repo przez to narzedzie (kopiuje go czlowiek do assets).
"""
import argparse, hashlib, json, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from hald2lut import load_hald, trilinear  # noqa: E402


def test_scene(size: int) -> np.ndarray:
    """Deterministyczna scena 0..1 (size x size x 3): niebo, gory, zielen, skora, szarosci, plansza barw."""
    y, x = np.mgrid[0:size, 0:size] / (size - 1)
    img = np.zeros((size, size, 3))
    sky = np.stack([0.35 + 0.55 * y, 0.55 + 0.30 * y, 0.90 - 0.25 * y], -1)
    img[:] = sky
    ridge = 0.50 + 0.10 * np.sin(x * 9) + 0.06 * np.sin(x * 23 + 1)
    mount = (y > ridge)[..., None]
    img = np.where(mount, np.array([0.32, 0.30, 0.38]), img)
    grass = (y > 0.68)[..., None]
    img = np.where(grass, np.array([0.20, 0.45, 0.16]) * (0.8 + 0.2 * np.sin(x * 40))[..., None], img)
    sun = ((x - 0.78) ** 2 + (y - 0.22) ** 2 < 0.004)[..., None]
    img = np.where(sun, np.array([1.0, 0.93, 0.70]), img)
    face = (((x - 0.30) / 0.14) ** 2 + ((y - 0.58) / 0.17) ** 2 < 1)[..., None]
    img = np.where(face, np.array([0.87, 0.64, 0.52]), img)
    ramp_y = (y > 0.86) & (x < 0.5)
    img = np.where(ramp_y[..., None], np.repeat((x * 2)[..., None], 3, -1), img)
    patches = [(0.85, 0.15, 0.15), (0.95, 0.80, 0.15), (0.15, 0.55, 0.20), (0.15, 0.40, 0.85), (0.60, 0.20, 0.70)]
    for i, c in enumerate(patches):
        x0 = 0.52 + i * 0.096
        m = ((y > 0.86) & (x >= x0) & (x < x0 + 0.09))[..., None]
        img = np.where(m, np.array(c), img)
    return np.clip(img, 0, 1)


def load_photo(path, size):
    im = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    im = ImageOps.fit(im, (size, size), Image.LANCZOS)
    return np.asarray(im, dtype=np.float64) / 255.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("luts", type=Path, help="katalog z <kategoria>/<nazwa>.png")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--photo", type=Path, default=None)
    ap.add_argument("--size", type=int, default=160)
    ap.add_argument("--quality", type=int, default=78)
    ap.add_argument("--only", default="", help="lista id (kategoria/nazwa) po przecinku")
    ap.add_argument("--budget-mb", type=float, default=5.0)
    a = ap.parse_args()
    src = load_photo(a.photo, a.size) if a.photo else test_scene(a.size)
    only = {s for s in a.only.split(",") if s}
    files = sorted(p for p in a.luts.rglob("*.png") if p.parent != a.luts)
    manifest, total = {}, 0
    for p in files:
        fid = f"{p.parent.name}/{p.stem}"
        if only and fid not in only:
            continue
        out = a.out / p.parent.name / f"{p.stem}.jpg"
        out.parent.mkdir(parents=True, exist_ok=True)
        lut = load_hald(p)
        res = np.clip(trilinear(lut, src), 0, 1)
        Image.fromarray((res * 255 + 0.5).astype(np.uint8)).save(out, "JPEG", quality=a.quality, optimize=True)
        data = out.read_bytes()
        total += len(data)
        manifest[fid] = {"file": f"{p.parent.name}/{p.stem}.jpg", "bytes": len(data),
                         "sha256": hashlib.sha256(data).hexdigest()}
        print(f"{fid}: {len(data)} B")
    (a.out / "thumbs.json").write_text(json.dumps(
        {"size": a.size, "quality": a.quality, "photo": a.photo.name if a.photo else "test-scene",
         "count": len(manifest), "total_bytes": total, "thumbs": manifest}, indent=1, sort_keys=True))
    print(f"{len(manifest)} miniatur, {total} B ({total / 1e6:.2f} MB)")
    if total > a.budget_mb * 1e6:
        sys.exit(f"Przekroczony budzet {a.budget_mb} MB")


if __name__ == "__main__":
    main()
