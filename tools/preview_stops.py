#!/usr/bin/env python3
"""
Draws every zoom stop (orange) and tap target (green) of the page lessons onto the page images,
so the rectangles in Chapter*Pages.java can be checked by eye.

    python3 tools/preview_stops.py OUT_DIR [page ...]
"""
import glob
import os
import re
import sys

from PIL import Image, ImageDraw

SRC = "app/src/main/java/com/hoohooolom/app/data"
BOOK = re.compile(r"StageSpec\.book\((\d+)((?:\s*,\s*\d+)*)\)")
TAP = re.compile(r"at\(((?:\s*\d+\s*,?)+)\)")


def nums(s):
    return [int(x) for x in re.findall(r"\d+", s)]


def main():
    out = sys.argv[1]
    only = {int(p) for p in sys.argv[2:]}
    os.makedirs(out, exist_ok=True)
    boxes = {}
    for path in glob.glob(os.path.join(SRC, "Chapter*Pages.java")):
        src = open(path, encoding="utf-8").read()
        # a TAP step's targets belong to the page of the stage right before them
        for m in re.finditer(r"StageSpec\.book\((\d+)((?:\s*,\s*\d+)*)\)(?:\.\w+\([^)]*\))*,?\s*(at\([^)]*\))?", src):
            page = int(m.group(1))
            stops = nums(m.group(2))
            entry = boxes.setdefault(page, {"stops": set(), "taps": set()})
            for i in range(0, len(stops) - 3, 4):
                entry["stops"].add(tuple(stops[i:i + 4]))
            if m.group(3):
                t = nums(m.group(3))
                for i in range(0, len(t) - 3, 4):
                    entry["taps"].add(tuple(t[i:i + 4]))
    for page, entry in sorted(boxes.items()):
        if only and page not in only:
            continue
        im = Image.open("app/src/main/assets/book/p%03d.webp" % page).convert("RGB")
        im = im.resize((760, int(760 * im.height / im.width)))
        d = ImageDraw.Draw(im)
        W, H = im.size
        for color, key, width in (((230, 120, 20), "stops", 3), ((20, 170, 60), "taps", 4)):
            for x1, y1, x2, y2 in entry[key]:
                d.rectangle([x1 * W / 1000, y1 * H / 1000, x2 * W / 1000, y2 * H / 1000], outline=color, width=width)
        im.save(os.path.join(out, "p%03d.png" % page))


if __name__ == "__main__":
    main()
