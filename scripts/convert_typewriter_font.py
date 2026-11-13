#!/usr/bin/env python3
"""Converts pixel fonts into the Minecraft bitmap font craftmaster:typewriter (the task letters, the round title typed
on black, the task typed on the broadcast).

  python scripts/convert_typewriter_font.py <Salmon Typewriter 9 Regular.ttf> <Cairopixel.ttf> [<Cairopixel License.txt>]

1. Salmon Typewriter 9 (Phildjii, https://phildjii.itch.io): the main font, a pixel typewriter with slab serifs
   (Courier-like). Its licence allows it as an asset displayed in a game and modified for the game's needs, keeping
   its copyright information; it forbids redistributing the font itself. So only this bitmap conversion is kept in
   the mod (never the .ttf), with its licence text next to it (font/LICENSE-Salmon-Typewriter.txt).
2. Cairopixel (GGBotNet, SIL OFL 1.1): fallback for the characters Salmon lacks (renamed in the conversion, as the
   OFL requires; font/OFL.txt).
3. Then Minecraft's default font.

Both are drawn on a pixel grid (every outline point a multiple of the font's pixel unit: 110 units for Salmon, 64
for Cairopixel; found as the gcd of the coordinates), so each glyph is rasterised exactly, one font pixel = one
texture pixel, no anti-aliasing (sampled at pixel centres, non-zero winding). Output:
  src/main/resources/assets/craftmaster/textures/font/typewriter.png            Salmon glyph sheet
  src/main/resources/assets/craftmaster/textures/font/typewriter_fallback.png   Cairopixel glyph sheet
  src/main/resources/assets/craftmaster/font/typewriter.json                    space + both bitmaps + default
Minecraft's bitmap provider gives each glyph the advance (rightmost lit column + 1) + 1, so a glyph whose own advance
is wider gets one nearly transparent pixel (alpha 1, discarded by the text shader) at its last column. The fallback
sheet is scaled to the main font's line (its height is given as the main font's cell height).
Run with a Python that has fontTools + Pillow (the Windows one).
"""
import json
import os
import sys
from functools import reduce
from math import gcd

from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "craftmaster")
COLUMNS = 32       # glyphs per row of the sheet
# Characters the letters need (French): reported if no font has one
WANTED = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
          "éèêëàâäçùûüôöîïÿœæÉÈÊËÀÂÄÇÙÛÜÔÖÎÏŸŒÆ«»’‘“”…–—•°€!?.,;:'\"()[]{}/\\-+*=_#%&@<>~|$^`")


def contours(glyphset, name):
    pen = RecordingPen()
    glyphset[name].draw(pen)
    out, cur = [], []
    for op, args in pen.value:
        if op == "moveTo":
            cur = [args[0]]
        elif op == "lineTo":
            cur.append(args[0])
        elif op in ("qCurveTo", "curveTo"):
            cur.extend(args)  # pixel fonts have none; keep the points if any
        elif op in ("closePath", "endPath"):
            if cur:
                out.append(cur)
            cur = []
    if cur:
        out.append(cur)
    return out


def winding(polys, x, y):
    """Non-zero winding number of the point (x, y) (font units)."""
    w = 0
    for poly in polys:
        n = len(poly)
        for i in range(n):
            x0, y0 = poly[i]
            x1, y1 = poly[(i + 1) % n]
            if y0 <= y < y1 or y1 <= y < y0:
                xc = x0 + (y - y0) * (x1 - x0) / (y1 - y0)
                if xc > x:
                    w += 1 if y1 > y0 else -1
    return w


def pixel_unit(font):
    """Font units per pixel: the gcd of every outline coordinate and advance."""
    gs, hmtx, vals = font.getGlyphSet(), font["hmtx"], []
    for name in font.getBestCmap().values():
        for c in contours(gs, name):
            for x, y in c:
                vals += [abs(int(x)), abs(int(y))]
        vals.append(hmtx[name][0])
    return reduce(gcd, [v for v in vals if v])


def rasterise(ttf):
    """char -> (pixels {(px, py)} with py up from the baseline, advance px); spaces; skipped."""
    font = TTFont(ttf)
    unit = pixel_unit(font)
    gs, hmtx = font.getGlyphSet(), font["hmtx"]
    glyphs, spaces, skipped = {}, {}, []
    for cp, name in sorted(font.getBestCmap().items()):
        ch = chr(cp)
        if cp < 32 or cp == 0xFFFD or cp > 0xFFFF:
            continue
        adv = hmtx[name][0] / unit
        polys = contours(gs, name)
        if not polys:
            if adv > 0:
                spaces[ch] = int(round(adv))
            continue
        xs = [p[0] for c in polys for p in c]
        ys = [p[1] for c in polys for p in c]
        if any(v % unit for v in xs + ys):
            skipped.append((ch, "off-grid"))
            continue
        pix = set()
        for py in range(int(min(ys)) // unit, int(max(ys)) // unit):
            for px in range(int(min(xs)) // unit, int(max(xs)) // unit):
                if winding(polys, px * unit + unit / 2, py * unit + unit / 2) != 0:
                    pix.add((px, py))
        if not pix:
            continue
        if min(p[0] for p in pix) < 0:
            skipped.append((ch, "negative bearing"))
            continue
        glyphs[ch] = (pix, int(round(adv)))
    return glyphs, spaces, skipped, unit


def sheet(glyphs, file_name):
    """Writes the glyph sheet; returns (rows of chars, cell height, ascent)."""
    top = max(py for pix, _ in glyphs.values() for _, py in pix) + 1      # rows above the baseline
    bottom = min(py for pix, _ in glyphs.values() for _, py in pix)       # lowest row (negative)
    cell_h = top - bottom
    cell_w = max(max(max(px for px, _ in pix) + 1, adv - 1) for pix, adv in glyphs.values())
    chars = list(glyphs)
    rows = (len(chars) + COLUMNS - 1) // COLUMNS
    img = Image.new("RGBA", (COLUMNS * cell_w, rows * cell_h), (0, 0, 0, 0))
    px_ = img.load()
    for i, ch in enumerate(chars):
        pix, adv = glyphs[ch]
        ox, oy = (i % COLUMNS) * cell_w, (i // COLUMNS) * cell_h
        for x, y in pix:
            px_[ox + x, oy + top - 1 - y] = (255, 255, 255, 255)
        ink = max(x for x, _ in pix) + 1
        if adv - 1 > ink:
            # Pad to the font's own advance: one invisible pixel in the last column (top row)
            px_[ox + adv - 2, oy] = (255, 255, 255, 1)
    tex = os.path.join(ASSETS, "textures", "font", file_name)
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    img.save(tex)
    out_rows = []
    for r in range(rows):
        row = "".join(chars[r * COLUMNS:(r + 1) * COLUMNS])
        out_rows.append(row + "\u0000" * (COLUMNS - len(row)))
    print(f"  {file_name}: {len(chars)} glyphs, cell {cell_w}x{cell_h}, ascent {top}, sheet {img.size}")
    return out_rows, cell_h, top


def main():
    main_ttf, fallback_ttf = sys.argv[1], sys.argv[2]
    licence = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(fallback_ttf), "License.txt")

    g1, s1, k1, u1 = rasterise(main_ttf)
    g2, s2, k2, u2 = rasterise(fallback_ttf)
    print(f"main: {len(g1)} glyphs (unit {u1}), skipped {k1[:10]}; fallback: {len(g2)} glyphs (unit {u2}), skipped {len(k2)}")
    # The fallback only fills what the main font lacks
    g2 = {c: v for c, v in g2.items() if c not in g1 and c not in s1}
    rows1, h1, a1 = sheet(g1, "typewriter.png")
    rows2, h2, a2 = sheet(g2, "typewriter_fallback.png")

    spaces = dict(s2)
    spaces.update(s1)
    spaces.setdefault(" ", 4)
    # Fallback glyphs drawn at the main font's line height, baseline matched
    k = h1 / h2
    providers = [
        {"type": "space", "advances": spaces},
        {"type": "bitmap", "file": "craftmaster:font/typewriter.png", "height": h1, "ascent": a1, "chars": rows1},
        {"type": "bitmap", "file": "craftmaster:font/typewriter_fallback.png", "height": h1, "ascent": round(a2 * k),
         "chars": rows2},
        {"type": "reference", "id": "minecraft:default"},
    ]
    out = os.path.join(ASSETS, "font", "typewriter.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
        f.write("\n")

    # Licences: Salmon's own text kept intact (its licence asks for the copyright information), Cairopixel's OFL
    salmon_licence = os.path.join(os.path.dirname(os.path.dirname(main_ttf)), "LICENSE.txt")
    if os.path.exists(salmon_licence):
        with open(salmon_licence, encoding="utf-8", errors="replace") as f:
            text = f.read()
        with open(os.path.join(ASSETS, "font", "LICENSE-Salmon-Typewriter.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write("textures/font/typewriter.png is a bitmap conversion of Salmon Typewriter 9 by Phildjii\n"
                    "(https://phildjii.itch.io), used as an asset displayed in this game (allowed by its licence).\n"
                    "The font itself is not distributed. Original licence:\n\n")
            f.write(text)
    if os.path.exists(licence):
        with open(licence, encoding="utf-8") as f:
            text = f.read()
        with open(os.path.join(ASSETS, "font", "OFL.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write("textures/font/typewriter_fallback.png (the characters Salmon Typewriter lacks in\n"
                    "assets/craftmaster/font/typewriter.json) is a bitmap conversion of the Cairopixel font by GGBotNet\n"
                    "(a Modified Version under the SIL Open Font License 1.1, renamed \"CraftMaster Typewriter\" as the\n"
                    "licence requires). Converted by scripts/convert_typewriter_font.py. Original licence:\n\n")
            f.write(text)
    missing = [c for c in WANTED if c not in g1 and c not in g2 and c not in spaces]
    print("in the fallback:", "".join(sorted(c for c in g2 if c in WANTED)))
    print("missing wanted:", repr("".join(missing)))


if __name__ == "__main__":
    main()
