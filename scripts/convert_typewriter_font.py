"""Converts the Cairopixel pixel font (GGBotNet, SIL OFL 1.1) into the Minecraft bitmap font craftmaster:typewriter.

Cairopixel is drawn on a pixel grid: every outline point is a multiple of 64 font units (1024 units per em = 16 px),
so each glyph is rasterised exactly, one font pixel = one texture pixel, no anti-aliasing (sampled at pixel centres,
non-zero winding). Output:

  src/main/resources/assets/craftmaster/textures/font/typewriter.png   glyph sheet (white, tinted by the text colour)
  src/main/resources/assets/craftmaster/font/typewriter.json           bitmap provider + space + fallback to the default
  src/main/resources/assets/craftmaster/font/OFL.txt                   the licence (a converted font is a Modified Version)

The converted font is called "CraftMaster Typewriter" (the OFL reserves the name "Cairopixel" to the original).
Minecraft's bitmap provider gives each glyph the advance (rightmost lit column + 1) + 1, so a glyph whose own advance
is wider gets one nearly transparent pixel (alpha 1, discarded by the text shader) at its last column.

Run with a Python that has fontTools + Pillow (the Windows one):
  python scripts/convert_typewriter_font.py <path to Cairopixel.ttf> [<path to License.txt>]
"""
import json
import os
import shutil
import sys

from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "craftmaster")
UNIT = 64          # font units per pixel
COLUMNS = 32       # glyphs per row of the sheet
# Characters the letters need (French): reported if the font lacks one
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


def main():
    ttf = sys.argv[1]
    licence = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(ttf), "License.txt")
    font = TTFont(ttf)
    cmap = font.getBestCmap()
    gs = font.getGlyphSet()
    hmtx = font["hmtx"]
    glyphs = {}   # char -> (pixels set of (px, py) with py up from the baseline, advance px)
    spaces = {}
    skipped = []
    for cp, name in sorted(cmap.items()):
        ch = chr(cp)
        if cp < 32 or cp == 0xFFFD or cp > 0xFFFF:
            continue
        adv = hmtx[name][0] / UNIT
        polys = contours(gs, name)
        if not polys:
            if adv > 0:
                spaces[ch] = int(round(adv))
            continue
        xs = [p[0] for c in polys for p in c]
        ys = [p[1] for c in polys for p in c]
        if any(v % UNIT for v in xs + ys):
            skipped.append((ch, "off-grid"))
            continue
        pix = set()
        for py in range(int(min(ys)) // UNIT, int(max(ys)) // UNIT):
            for px in range(int(min(xs)) // UNIT, int(max(xs)) // UNIT):
                if winding(polys, px * UNIT + UNIT / 2, py * UNIT + UNIT / 2) != 0:
                    pix.add((px, py))
        if not pix:
            continue
        if min(p[0] for p in pix) < 0:
            skipped.append((ch, "negative bearing"))
            continue
        glyphs[ch] = (pix, int(round(adv)))
    top = max(py for pix, _ in glyphs.values() for _, py in pix) + 1      # rows above the baseline
    bottom = min(py for pix, _ in glyphs.values() for _, py in pix)       # lowest row (negative)
    cell_h = top - bottom
    cell_w = max(max(max(px for px, _ in pix) + 1, adv - 1) for pix, adv in glyphs.values())
    chars = list(glyphs)
    rows = (len(chars) + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * cell_w, rows * cell_h), (0, 0, 0, 0))
    px_ = sheet.load()
    narrower = []
    for i, ch in enumerate(chars):
        pix, adv = glyphs[ch]
        ox, oy = (i % COLUMNS) * cell_w, (i // COLUMNS) * cell_h
        for x, y in pix:
            px_[ox + x, oy + top - 1 - y] = (255, 255, 255, 255)
        ink = max(x for x, _ in pix) + 1
        if adv - 1 > ink:
            # Pad to the font's own advance: one invisible pixel in the last column (top row)
            px_[ox + adv - 2, oy] = (255, 255, 255, 1)
        elif adv - 1 < ink:
            narrower.append(ch)
    tex = os.path.join(ASSETS, "textures", "font", "typewriter.png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    sheet.save(tex)
    rows_s = []
    for r in range(rows):
        row = "".join(chars[r * COLUMNS:(r + 1) * COLUMNS])
        rows_s.append(row + "\u0000" * (COLUMNS - len(row)))
    spaces.setdefault(" ", 4)
    providers = [
        {"type": "space", "advances": spaces},
        {"type": "bitmap", "file": "craftmaster:font/typewriter.png", "height": cell_h, "ascent": top, "chars": rows_s},
        {"type": "reference", "id": "minecraft:default"},
    ]
    out = os.path.join(ASSETS, "font", "typewriter.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
        f.write("\n")
    if os.path.exists(licence):
        with open(licence, encoding="utf-8") as f:
            text = f.read()
        with open(os.path.join(ASSETS, "font", "OFL.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write("CraftMaster Typewriter (assets/craftmaster/font/typewriter.json, textures/font/typewriter.png)\n"
                    "is a bitmap conversion of the Cairopixel font by GGBotNet (a Modified Version under the\n"
                    "SIL Open Font License 1.1, renamed as the licence requires). Converted by\n"
                    "scripts/convert_typewriter_font.py. Original licence:\n\n")
            f.write(text)
    missing = [c for c in WANTED if c not in glyphs and c not in spaces]
    print(f"{len(glyphs)} glyphs, {len(spaces)} spaces, cell {cell_w}x{cell_h}, ascent {top}, sheet {sheet.size}")
    print("missing wanted:", repr("".join(missing)))
    print("skipped:", skipped[:20], len(skipped))
    print("ink wider than advance:", "".join(narrower))


if __name__ == "__main__":
    main()
