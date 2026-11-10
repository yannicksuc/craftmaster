"""Pixel-art textures of the task letter (chantier D, the folded letter sealed with wax).

Uses the procedural texture tools of the SteveParty workshop (proctex: Perlin noise quantised to a small palette,
blended by short horizontal strokes, lone pixels cleaned), see docs/techniques-textures.md over there. Writes:

  textures/gui/task/letter_paper.png    256 x 256 cream paper (front of the sheet), tiles vertically
  textures/gui/task/letter_back.png     256 x 256 outside of the folded sheet (a little warmer and darker)
  textures/gui/task/letter_seal.png     48 x 48 round wax seal with the CM monogram (grey: tinted with its wax)
  textures/gui/task/letter_residue.png  48 x 48 what is left of the wax once torn off (grey, tinted)

The screens draw the sheet at an integer number of screen pixels per texture pixel, nearest filtering, like the
vanilla book. Run with the Windows Python (numpy + Pillow):
  python scripts/gen_letter_art.py [<workshop dir>]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKSHOP = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), "SteveParty-Workshop")
if not os.path.isdir(WORKSHOP):
    WORKSHOP = r"C:\Users\yanni\code\SteveParty-Workshop"
sys.path.insert(0, os.path.join(WORKSHOP, "art", "textures", "lib"))
import proctex as pt  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "craftmaster", "textures", "gui", "task")
S = 256


def save(img, name):
    img.save(os.path.join(OUT, name))
    print("wrote", name, img.size)


def tile_noise(noise, w, h, scale, z):
    """Noise tiling vertically (sampled on a cylinder) and horizontally (on a second circle)."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    ay, ax = y / h * 2 * math.pi, x / w * 2 * math.pi
    r = scale / (2 * math.pi)
    return noise.fbm(np.cos(ax) * r * w / h, np.sin(ax) * r * w / h + np.cos(ay) * r, np.sin(ay) * r + z, octaves=3)


def paper(colors, seed, stains=True):
    """Cream paper: large soft stains (quantised noise), fibres as short strokes, no speckle."""
    pal = pt.hexes(*colors)  # dark -> light
    n = pt.Perlin3(seed)
    st = pt.Strokes(seed + 1)
    v = tile_noise(n, S, S, 5.0, 0.0)
    v = (v - v.min()) / (v.max() - v.min())
    # Mostly the base tone: squash the values toward the middle-light ids
    v = 0.35 + 0.5 * v
    if stains:
        s = tile_noise(pt.Perlin3(seed + 7), S, S, 3.0, 4.0)
        v = np.where(s > 0.42, v - 0.28 * np.clip((s - 0.42) * 6, 0, 1), v)
    y, x = np.mgrid[0:S, 0:S].astype(float)
    off = st(x * 1.0, y * 1.0, np.zeros_like(x))
    idx = pt.quantise(np.clip(v, 0, 1), 0, len(pal) - 1, off, amp=0.5)
    idx = pt.cleanup(idx)
    img = np.zeros((S, S, 4), np.uint8)
    img[..., :3] = pal[idx]
    img[..., 3] = 255
    return Image.fromarray(img, "RGBA")


# ---------------------------------------------------------------- paper (palette of the brief)
# front: stain #E4DDC8, mid #EAE5D4, cream #F1EDE0, light #F5F2E8
save(paper(("#E4DDC8", "#EAE5D4", "#F1EDE0", "#F4F1E6"), 31), "letter_paper.png")
# outside of the folded sheet: a touch warmer / darker, like the second sheet #E2D9BF
save(paper(("#DDD3B8", "#E2D9BF", "#E8E0CA", "#ECE5D1"), 57), "letter_back.png")

# ---------------------------------------------------------------- the wax seal (grey ramp, tinted in game)
RAMP = np.array([62, 92, 122, 150, 176, 200, 222, 242], np.uint8)   # id 0..7
SEAL = 48
C_ = [".#####.",
      "##...##",
      "##.....",
      "##.....",
      "##.....",
      "##.....",
      "##.....",
      "##...##",
      ".#####."]
M_ = ["##.....##",
      "###...###",
      "##.#.#.##",
      "##..#..##",
      "##.....##",
      "##.....##",
      "##.....##",
      "##.....##",
      "##.....##"]
MONO = [c + "." + m for c, m in zip(C_, M_)]


def seal_mask_and_height(seed):
    """Blobby rim radius per angle, then a height field: rolled rim, pressed disc, raised monogram."""
    n = pt.Perlin3(seed)
    y, x = np.mgrid[0:SEAL, 0:SEAL].astype(float)
    c = SEAL / 2 - 0.5
    dx, dy = x - c, y - c
    d = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    wob = n.fbm(np.cos(ang) * 1.6, np.sin(ang) * 1.6, np.zeros_like(ang) + 2.0, octaves=2)
    R = 20.0 + 2.6 * wob                      # irregular, poured wax
    inside = d <= R
    disc = 13.0                               # the part pressed by the stamp
    h = np.zeros_like(d)
    # Rolled rim: a ridge between the disc and the outside, lit from the top left
    t = np.clip((d - disc) / np.maximum(R - disc, 1), 0, 1)
    h = np.where(d > disc, np.sin(t * math.pi) * 1.0 + 0.15, 0.0)
    # Pressed disc: flat, a dark groove around it
    h = np.where((d > disc - 1.2) & (d <= disc + 0.6), -0.55, h)
    mono = np.zeros((SEAL, SEAL), bool)
    mh, mw = len(MONO), len(MONO[0])
    ox, oy = int(round(c - mw / 2 + 0.5)), int(round(c - mh / 2 + 0.5))
    for j, row in enumerate(MONO):
        for i, ch in enumerate(row):
            if ch == "#":
                mono[oy + j, ox + i] = True
    h = np.where(mono, 0.6, h)
    return inside, h, mono, d, R, disc


def shade(inside, h, mono, d, R):
    """Palette ids by part: lit rim (convex, bright top left), groove (concave, dark top left), flat disc, raised
    monogram with a lit edge and a cast shadow, dark outline of the blob."""
    y, x = np.mgrid[0:SEAL, 0:SEAL].astype(float)
    c = SEAL / 2 - 0.5
    dx, dy = x - c, y - c
    dd = np.maximum(d, 0.01)
    light = -(dx + dy) / (dd * math.sqrt(2))          # 1 toward the top left, -1 bottom right
    st = pt.Strokes(5)
    off = st(x, y, np.zeros_like(x))
    disc = 13.0
    rim = d > disc + 0.6
    groove = (d > disc - 1.2) & ~rim
    v = np.full(d.shape, 0.55)                          # flat pressed disc
    t = np.clip((d - disc) / np.maximum(R - disc, 1), 0, 1)
    v = np.where(rim, 0.55 + 0.32 * light * np.sin(np.clip(t, 0.15, 1) * math.pi * 0.9) - 0.1 * t, v)
    v = np.where(groove, 0.4 - 0.22 * light, v)
    v = v - 0.05 * (dx + dy) / R                         # roundness
    sh = (pt.shift(mono, 1, 1) | pt.shift(mono, 1, 0)) & ~mono
    lit = mono & ~pt.shift(mono, -1, -1)
    v = np.where(sh, 0.22, v)
    v = np.where(mono, 0.74, v)
    v = np.where(lit, 0.95, v)
    edge = inside & ~(pt.shift(inside, 1, 0) & pt.shift(inside, -1, 0) & pt.shift(inside, 0, 1) & pt.shift(inside, 0, -1))
    v = np.where(edge, np.where(light > 0.3, 0.3, 0.05), v)
    idx = pt.quantise(np.clip(v, 0, 1), 0, len(RAMP) - 1, np.where(mono | sh | edge, 0, off), amp=0.45)
    keep = mono | sh | edge | ~inside
    idx = np.where(inside, pt.cleanup(np.where(inside, idx, 0), keep=keep), -1)
    return idx


def to_img(idx, alpha_mask=None):
    img = np.zeros(idx.shape + (4,), np.uint8)
    m = idx >= 0 if alpha_mask is None else alpha_mask
    g = RAMP[np.clip(idx, 0, len(RAMP) - 1)]
    img[..., 0] = img[..., 1] = img[..., 2] = g
    img[..., 3] = np.where(m, 255, 0)
    return Image.fromarray(img, "RGBA")


inside, h, mono, d, R, disc = seal_mask_and_height(12)
seal_idx = shade(inside, h, mono, d, R)
save(to_img(seal_idx), "letter_seal.png")

# ---------------------------------------------------------------- residue: a matte stain, a few torn bits of the rim
n = pt.Perlin3(77)
y, x = np.mgrid[0:SEAL, 0:SEAL].astype(float)
c = SEAL / 2 - 0.5
ang = np.arctan2(y - c, x - c)
stain = inside & (d <= R - 2)
keep_ang = n.fbm(np.cos(ang) * 1.4, np.sin(ang) * 1.4, np.zeros_like(ang) + 9.0, octaves=2) > -0.35
ring = inside & (d > R - 4.5) & keep_ang
ring = ring & pt.cleanup(ring.astype(int)).astype(bool)
v = np.where(ring, 0.5, 0.45)
lip = ring & ~pt.shift(ring, 1, 1)
v = np.where(lip, 0.72, v)
out_edge = ring & ~(pt.shift(ring, 1, 0) & pt.shift(ring, -1, 0) & pt.shift(ring, 0, 1) & pt.shift(ring, 0, -1))
v = np.where(out_edge & ~lip, 0.2, v)
idx = pt.quantise(v, 0, len(RAMP) - 1, np.zeros_like(v))
img = np.zeros((SEAL, SEAL, 4), np.uint8)
g = RAMP[idx]
img[..., 0] = img[..., 1] = img[..., 2] = g
# The stain is thin wax left on the paper: see-through; the torn bits are solid
img[..., 3] = np.where(ring, 255, np.where(stain, 45, 0))
save(Image.fromarray(img, "RGBA"), "letter_residue.png")
