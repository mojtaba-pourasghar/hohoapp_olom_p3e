#!/usr/bin/env python3
"""
Turns the printed science textbook into what the app and the lesson writer need:

  • one WebP per printed page in app/src/main/assets/book/pNNN.webp — the lessons zoom, pan and
    point inside these real pages, so the child works with the book's own pictures;
  • project/book/pages.json — every page's text plus the rectangles of its pictures (in
    thousandths of the page), used while writing the lessons. It is not shipped in the app.

The PDF's first page is the cover, so printed page N is PDF page N + 1.

    pip install pymupdf pillow
    python3 tools/extract_book.py project/uploads/sevom-olom-tajrobi-1405.pdf
"""
import io
import json
import os
import sys

import pymupdf
from PIL import Image

ASSETS = "app/src/main/assets/book"
TEXT = "project/book/pages.json"
FIRST_PAGE, LAST_PAGE = 7, 128   # the lessons: درس ۱ starts on 7, درس ۱۴ ends on 128
DPI = 150


def figures(page):
    """Rectangles of the page's pictures, ignoring slivers, as [x1, y1, x2, y2] in ‰."""
    w, h = page.rect.width, page.rect.height
    out = []
    for info in page.get_image_info():
        x1, y1, x2, y2 = info["bbox"]
        box = [round(x1 / w * 1000), round(y1 / h * 1000), round(x2 / w * 1000), round(y2 / h * 1000)]
        if box[2] - box[0] < 40 or box[3] - box[1] < 30:
            continue
        if box[2] - box[0] > 950 and box[3] - box[1] > 950:
            continue                                   # a full-page background
        out.append(box)
    return out


def main():
    pdf = pymupdf.open(sys.argv[1])
    os.makedirs(ASSETS, exist_ok=True)
    os.makedirs(os.path.dirname(TEXT), exist_ok=True)
    pages = []
    for printed in range(FIRST_PAGE, LAST_PAGE + 1):
        page = pdf[printed]                            # printed N = index N (cover is index 0)
        pix = page.get_pixmap(dpi=DPI)
        img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
        img.save(os.path.join(ASSETS, "p%03d.webp" % printed), "webp", quality=68, method=6)
        lines = [l.strip() for l in page.get_text().split("\n") if l.strip()]
        pages.append({"p": printed, "lines": lines, "figures": figures(page)})
    with open(TEXT, "w", encoding="utf-8") as f:
        json.dump({"pages": pages}, f, ensure_ascii=False, indent=0)
    print("pages:", len(pages))


if __name__ == "__main__":
    main()
