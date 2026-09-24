#!/usr/bin/env python3
"""
Cuts the book's own pictures out of the page, one picture per file, for the animated scenes.

The list lives in project/art/art.txt, one picture per line:

    name | page | x1 y1 x2 y2 | flags

  page       printed page number (the PDF's first page is the cover, so printed N = index N)
  x1 … y2    the picture's rectangle in thousandths of the page (0…1000, from the top-left)
  flags      k  key out the background: flat light colour touching the edges becomes transparent
             r  round the corners (for photos)
             (nothing) keep the rectangle as it is

Output: app/src/main/assets/art/<name>.webp, at most 640 px on the long side.

    pip install pymupdf pillow numpy scipy
    python3 tools/cut_art.py            # every picture
    python3 tools/cut_art.py sun cup    # only names starting with sun or cup
"""
import io
import os
import sys

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "project/uploads/sevom-olom-tajrobi-1405.pdf")
LIST = os.path.join(ROOT, "project/art/art.txt")
OUT = os.path.join(ROOT, "app/src/main/assets/art")
DPI = 200
MAX_SIDE = 640

_pages = {}


def page_image(doc, printed):
    if printed not in _pages:
        pix = doc[printed].get_pixmap(dpi=DPI)
        _pages[printed] = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
    return _pages[printed]


def key_out(img, tol=34):
    """Flat background that touches the border → transparent, with a soft edge."""
    a = np.asarray(img).astype(np.int16)
    h, w, _ = a.shape
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    # the background is the commonest border colour, taken coarsely
    q = (border // 12).astype(np.int32)
    codes = q[:, 0] * 10000 + q[:, 1] * 100 + q[:, 2]
    keys, counts = np.unique(codes, return_counts=True)
    order = np.argsort(-counts)
    # up to two background colours (a page can put a coloured band behind part of a picture)
    near = np.zeros((h, w), dtype=bool)
    for k in order[:2]:
        if counts[k] < 0.12 * len(codes) and k != order[0]:
            continue
        top = keys[k]
        bg = np.array([top // 10000, (top // 100) % 100, top % 100]) * 12 + 6
        near |= np.abs(a - bg).max(axis=2) < tol
    labels, _ = ndimage.label(near)
    edge_labels = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))) - {0}
    mask = np.isin(labels, list(edge_labels))
    alpha = np.where(mask, 0, 255).astype(np.uint8)
    alpha_img = Image.fromarray(alpha).filter(ImageFilter.GaussianBlur(1.2))
    out = img.convert("RGBA")
    out.putalpha(alpha_img)
    bbox = alpha_img.point(lambda v: 255 if v > 20 else 0).getbbox()
    return out.crop(bbox) if bbox else out


def round_corners(img, radius_frac=0.07):
    img = img.convert("RGBA")
    w, h = img.size
    r = int(min(w, h) * radius_frac)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], r, fill=255)
    img.putalpha(mask)
    return img


def main():
    prefixes = tuple(sys.argv[1:])
    doc = pymupdf.open(PDF)
    os.makedirs(OUT, exist_ok=True)
    made = 0
    for raw in open(LIST, encoding="utf-8"):
        raw = raw.strip()
        if not raw or raw.startswith("#"):
            continue
        parts = [p.strip() for p in raw.split("|")]
        name, page, box = parts[0], int(parts[1]), [int(v) for v in parts[2].split()]
        flags = parts[3] if len(parts) > 3 else ""
        if prefixes and not name.startswith(prefixes):
            continue
        img = page_image(doc, page)
        W, H = img.size
        crop = img.crop((box[0] * W // 1000, box[1] * H // 1000, box[2] * W // 1000, box[3] * H // 1000))
        if "k" in flags:
            crop = key_out(crop)
        elif "r" in flags:
            crop = round_corners(crop)
        crop.thumbnail((MAX_SIDE, MAX_SIDE), Image.LANCZOS)
        crop.save(os.path.join(OUT, name + ".webp"), "webp", quality=80, method=6)
        made += 1
    print("pictures:", made)


if __name__ == "__main__":
    main()
