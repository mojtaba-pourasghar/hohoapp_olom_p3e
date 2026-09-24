#!/usr/bin/env python3
"""
Draws the last frame of every scene in the page lessons, roughly the way the app shows it,
so layouts can be checked without a phone.

    python3 tools/preview_scenes.py OUT_DIR [key-prefix]   e.g. t008
"""
import glob
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

SRC = "app/src/main/java/com/hoohooolom/app/data"
ART = "app/src/main/assets/art"
FONT = "app/src/main/res/font/vazirmatn_bold.ttf"
COLORS = {"orange": (232, 151, 58), "teal": (58, 167, 154), "pink": (217, 79, 122), "blue": (62, 142, 208),
          "green": (76, 175, 80), "red": (224, 70, 60), "yellow": (245, 197, 24), "purple": (142, 92, 194),
          "white": (255, 255, 255), "ink": (46, 59, 54)}
BG = {"sky": (170, 220, 245), "farm": (170, 220, 170), "park": (180, 225, 180), "desert": (243, 211, 139),
      "snow": (235, 243, 250), "sea": (120, 190, 230), "water": (90, 170, 220), "night": (30, 45, 90),
      "kitchen": (253, 235, 205), "warm": (253, 235, 205), "class": (225, 243, 230), "shop": (255, 240, 220),
      "lab": (215, 240, 236), "plain": (253, 245, 228)}


def scenes():
    for path in sorted(glob.glob(os.path.join(SRC, "Chapter*Pages.java"))):
        src = open(path, encoding="utf-8").read()
        for m in re.finditer(r'"([tp]\d{3}_\d+)",', src):
            key = m.group(1)
            at = src.find("scene(", m.end())
            nxt = re.search(r'LessonStep\.\w+\("', src[m.end():])
            if at < 0 or (nxt and at > m.end() + nxt.start()):
                continue
            depth, i, lits = 0, at + 5, []
            while i < len(src):
                c = src[i]
                if c == '"':
                    j = i + 1
                    buf = ""
                    while src[j] != '"':
                        if src[j] == "\\":
                            buf += src[j + 1]; j += 2; continue
                        buf += src[j]; j += 1
                    lits.append(buf); i = j + 1; continue
                if c == "(": depth += 1
                if c == ")":
                    depth -= 1
                    if depth == 0: break
                i += 1
            yield key, lits


def tokens(line):
    return re.findall(r'"[^"]*"|\S+', line)


def main():
    out, prefix = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else ""
    os.makedirs(out, exist_ok=True)
    fonts = {}
    n = 0
    for key, lines in scenes():
        if prefix and not re.match(prefix, key):
            continue
        im = Image.new("RGB", (1000, 600), BG["plain"])
        d = ImageDraw.Draw(im)
        actors = []
        for line in lines:
            tk = [t.strip('"') for t in tokens(line)]
            op = tk[0]
            if op == "bg":
                im.paste(BG.get(tk[1], (255, 255, 255)), (0, 0, 1000, 600))
            elif op == "img":
                x, y, w = float(tk[2]), float(tk[3]), float(tk[4])
                try:
                    a = Image.open(os.path.join(ART, tk[1] + ".webp")).convert("RGBA")
                    h = w * a.height / a.width
                    a = a.resize((int(w), int(h)))
                    im.paste(a, (int(x - w / 2), int(y - h / 2)), a)
                except FileNotFoundError:
                    h = w * .75
                    d.rectangle([x - w / 2, y - h / 2, x + w / 2, y + h / 2], outline=(255, 0, 0), width=4)
                actors.append((x, y, w, h))
            elif op == "move":
                i = int(tk[1]) - 1
                x0, y0, w0, h0 = actors[i]
                d.line([x0, y0, float(tk[2]), float(tk[3])], fill=(0, 0, 255), width=3)
            elif op == "text":
                size = 34
                rest = tk[4:]
                if rest and re.match(r"^[\d.]+$", rest[0]): rest = rest[1:]
                col = COLORS["orange"]
                if rest and rest[0] in COLORS: col = COLORS[rest[0]]; rest = rest[1:]
                if rest: size = float(rest[0])
                size = int(size)
                if size not in fonts: fonts[size] = ImageFont.truetype(FONT, size)
                txt = tk[1][2:] if tk[1].startswith("?:") else tk[1]
                f = fonts[size]
                tw = d.textlength(txt, font=f, direction="rtl") if False else d.textlength(txt, font=f)
                x, y = float(tk[2]), float(tk[3])
                d.rounded_rectangle([x - tw / 2 - size * .45, y - size * .8, x + tw / 2 + size * .45, y + size * .55],
                                    radius=size // 2, fill=(255, 255, 255), outline=col, width=3)
                d.text((x, y - size * .15), txt, font=f, fill=col if col != (255, 255, 255) else (0, 0, 0), anchor="mm")
            elif op in ("arrow", "beam"):
                c = COLORS["red" if op == "arrow" else "yellow"]
                for t in tk[5:]:
                    if t in COLORS: c = COLORS[t]
                d.line([float(tk[1]), float(tk[2]), float(tk[3]), float(tk[4])], fill=c, width=8)
                d.ellipse([float(tk[3]) - 10, float(tk[4]) - 10, float(tk[3]) + 10, float(tk[4]) + 10], fill=c)
            elif op == "box":
                c = COLORS.get(tk[-1], (255, 255, 255))
                d.rounded_rectangle([float(tk[1]), float(tk[2]), float(tk[3]), float(tk[4])], radius=20, fill=c)
            elif op == "rays":
                x, y, r = float(tk[1]), float(tk[2]), float(tk[3])
                d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 214, 70))
            elif op in ("rain", "snow"):
                d.rectangle([float(tk[1]), float(tk[2]), float(tk[1]) + float(tk[3]), float(tk[2]) + float(tk[4])], outline=(80, 140, 220), width=2)
            elif op == "ring":
                i = int(tk[1]) - 1
                x, y, w, h = actors[i]
                d.rounded_rectangle([x - w / 2 - 10, y - h / 2 - 10, x + w / 2 + 10, y + h / 2 + 10], radius=20, outline=COLORS["orange"], width=6)
        d.text((8, 8), key, fill=(0, 0, 0))
        im.save(os.path.join(out, key + ".png"))
        n += 1
    print("scenes:", n)


if __name__ == "__main__":
    main()
