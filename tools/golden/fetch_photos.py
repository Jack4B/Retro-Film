#!/usr/bin/env python3
"""Pobiera zdjecia testowe (CC0, Wikimedia Commons; zob. tools/golden/photos/SOURCES.md),
sprawdza sha256 ORYGINALU, skaluje (Lanczos) do 2400 px na dluzszym boku i zapisuje JPEG q90.
Zdjec nie trzymamy w repo (kilka MB kazde).

Uzycie: python3 tools/golden/fetch_photos.py [--out tools/golden/photos] [--cache DIR]
"""
import argparse, hashlib, sys, urllib.request
from pathlib import Path
from PIL import Image, ImageOps

PHOTOS = {
    "sky": ("https://upload.wikimedia.org/wikipedia/commons/2/23/Winter_in_gradient_%28Unsplash%29.jpg",
            "d27d20ad0d2c3bfa40fbe628082e040c26ec8a311eede3d280d8bf61c3a2162d"),
    "skin": ("https://upload.wikimedia.org/wikipedia/commons/0/04/Face_portrait_%28Unsplash%29.jpg",
             "7356daa8fd4ad53b946ce0036f06b014431dc89b7ae29ecd8ef18fc54edce6b5"),
    "green": ("https://upload.wikimedia.org/wikipedia/commons/2/23/Green_tree_leaves_against_sun%2C_D%C3%B6hren.jpg",
              "fb325b2abfc36b336fc97083ec57a1474845ddb39a611a5475818cb29b6c9228"),
}
UA = "RetroFilm-golden/1.0 (https://github.com/Jack4B/Retro-Film)"
LONG = 2400

def fetch(url, sha, dst):
    if dst.exists() and hashlib.sha256(dst.read_bytes()).hexdigest() == sha:
        return
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    data = urllib.request.urlopen(req, timeout=120).read()
    got = hashlib.sha256(data).hexdigest()
    if got != sha:
        sys.exit(f"sha256 niezgodne dla {url}: {got} != {sha}")
    dst.write_bytes(data)

def main():
    root = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=root / "photos")
    ap.add_argument("--cache", type=Path, default=None, help="katalog na oryginaly (domyslnie <out>/orig)")
    a = ap.parse_args()
    cache = a.cache or a.out / "orig"; cache.mkdir(parents=True, exist_ok=True)
    for name, (url, sha) in PHOTOS.items():
        src = cache / f"{name}.jpg"; fetch(url, sha, src)
        im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
        w, h = im.size; s = LONG / max(w, h)
        im.resize((round(w * s), round(h * s)), Image.LANCZOS).save(a.out / f"{name}.jpg", quality=90)
        print(name, "OK", sha[:12], flush=True)

if __name__ == "__main__":
    main()
