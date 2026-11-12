"""Item icons drawn from shapes (16 x 16, RGBA rows): the travelling marker's clapperboard, the studio camera, the
curtain remote and the portrait. Called by scripts/gen_assets.py.

Pixel-art rules (minecraft-modeling skill): limited palette per material, light from the top left, outlines in the
darkest tone of the material (never pure black), no isolated pixels, clean 1 px lines.
"""

T = (0, 0, 0, 0)


class Icon:
    def __init__(self):
        self.p = [[T] * 16 for _ in range(16)]

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = c if len(c) == 4 else tuple(c) + (255,)

    def get(self, x, y):
        return self.p[y][x] if 0 <= x < 16 and 0 <= y < 16 else T

    def rect(self, x0, y0, x1, y1, c):
        """Fills x0..x1, y0..y1 (inclusive)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, light, dark, corner=None):
        """1 px border: light on top / left, dark on bottom / right; corners left empty when corner is None (rounded)."""
        for x in range(x0, x1 + 1):
            self.set(x, y0, light)
            self.set(x, y1, dark)
        for y in range(y0, y1 + 1):
            self.set(x0, y, light)
            self.set(x1, y, dark)
        for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            self.set(x, y, corner if corner is not None else T)

    def outline(self, c):
        """Puts an outline of colour c around every opaque pixel (4-neighbours)."""
        solid = {(x, y) for y in range(16) for x in range(16) if self.p[y][x][3]}
        for (x, y) in solid:
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in solid:
                    self.set(x + dx, y + dy, c)


def clapperboard():
    """Film clapperboard, clapper stick lifted on its hinge: black and white stripes, slate with chalk lines."""
    O = (30, 28, 34)
    W, w = (238, 234, 222), (204, 198, 186)
    K, k = (58, 56, 64), (44, 42, 50)
    S, s, sl = (62, 68, 66), (52, 56, 56), (76, 84, 80)
    C = (206, 212, 204)
    H = (206, 156, 52)
    i = Icon()
    # Slate body x 2..13, y 8..13 (outline added around it)
    i.rect(2, 8, 13, 9, W)
    for x in range(2, 14):
        for y in (8, 9):
            if ((x + (9 - y)) // 2) % 2:
                i.set(x, y, K if y == 8 else k)
            elif y == 9:
                i.set(x, y, w)
    i.rect(2, 10, 13, 13, S)
    i.rect(2, 10, 13, 10, sl)
    i.rect(2, 13, 13, 13, s)
    for x in list(range(4, 8)) + list(range(9, 12)):
        i.set(x, 11, C)
    for x in range(4, 6):
        i.set(x, 12, C)
    # Clapper stick, lifted: 2 px thick, rising from the hinge (x 2, y 6) to the top right
    for x in range(2, 14):
        top = 6 - (x - 2) // 3
        stripe = ((x + 1) // 2) % 2
        i.set(x, top, W if stripe else K)
        i.set(x, top + 1, w if stripe else k)
    i.outline(O)
    # Hinge: a gold pin between the stick and the slate
    i.set(1, 7, H)
    i.set(1, 6, H)
    return i.p


def camera():
    """Studio camera on its tripod, side view: black body with a light top edge, brass lens, red tally light."""
    O = (26, 24, 30)
    B, b, bl = (56, 54, 62), (44, 42, 50), (80, 78, 88)
    G, g, gl = (206, 156, 52), (160, 110, 34), (244, 202, 72)
    L, l = (150, 196, 232), (40, 62, 104)
    R = (232, 52, 46)
    M, m = (48, 48, 56), (34, 34, 40)
    i = Icon()
    # Body x 5..13, y 3..8
    i.rect(5, 3, 13, 8, B)
    i.rect(5, 3, 13, 3, bl)
    i.rect(5, 8, 13, 8, b)
    i.rect(13, 4, 13, 8, b)
    # Side door line and the brand plate
    i.rect(8, 5, 11, 6, b)
    i.rect(8, 5, 11, 5, G)
    # Viewfinder hood on top (x 9..12, y 1..2)
    i.rect(9, 1, 12, 2, B)
    i.rect(9, 1, 12, 1, bl)
    # Lens barrel to the left (x 1..4, y 4..7): brass ring, dark glass with a highlight
    i.rect(3, 4, 4, 7, G)
    i.rect(3, 4, 4, 4, gl)
    i.rect(3, 7, 4, 7, g)
    i.rect(1, 4, 2, 7, l)
    i.set(1, 5, L)
    # Tally light
    i.set(13, 1, R)
    i.outline(O)
    # Tripod: head under the body, three legs (1 px lines, no gaps), drawn after the outline: thin and clean
    i.rect(8, 10, 10, 10, M)
    i.rect(9, 11, 9, 14, m)
    for k in range(4):
        i.set(8 - k, 11 + k, M)
        i.set(10 + k, 11 + k, m)
    return i.p


def remote():
    """Curtain remote, upright: rounded black body, red power button, gold arrows (open / close), small keys."""
    O = (30, 26, 36)
    B, b, bl = (72, 66, 84), (56, 50, 68), (104, 96, 120)
    R, r = (226, 56, 52), (170, 34, 36)
    G, g = (244, 202, 72), (182, 124, 32)
    K = (150, 142, 166)
    E = (250, 240, 200)
    i = Icon()
    # Body x 5..10, y 1..14 (rounded by the outline)
    i.rect(5, 1, 10, 14, B)
    i.rect(5, 1, 5, 14, bl)
    i.rect(10, 1, 10, 14, b)
    i.rect(5, 14, 10, 14, b)
    # Emitter at the top
    i.rect(7, 1, 8, 1, E)
    # Power button
    i.rect(7, 3, 8, 4, R)
    i.rect(8, 4, 8, 4, r)
    # Up / down arrows (open, close): solid triangles
    i.rect(7, 6, 8, 6, G)
    i.rect(6, 7, 9, 7, G)
    i.rect(6, 9, 9, 9, g)
    i.rect(7, 10, 8, 10, g)
    # Small keys
    for y in (12,):
        for x in (6, 9):
            i.set(x, y, K)
    i.outline(O)
    # Rounded corners
    for (x, y) in ((4, 0), (11, 0), (4, 15), (11, 15)):
        i.set(x, y, T)
    i.set(5, 0, O); i.set(10, 0, O)
    return i.p


def portrait():
    """A gilt frame hung on its string, a player's bust on red velvet inside."""
    S, s = (232, 220, 190), (186, 170, 136)
    N = (120, 96, 60)
    GH, G, g, gd = (255, 236, 140), (240, 196, 64), (206, 150, 40), (150, 98, 24)
    V, v = (124, 30, 42), (100, 22, 34)
    HAIR, SKIN, skin, EYE = (82, 56, 36), (226, 172, 132), (196, 140, 104), (64, 72, 150)
    SH, sh = (44, 168, 176), (30, 132, 140)
    i = Icon()
    # String from the nail to the frame's top corners
    i.set(7, 1, N); i.set(8, 1, N)
    for k in range(1, 4):
        i.set(7 - k, 1 + k, S)
        i.set(8 + k, 1 + k, s)
    # Frame x 2..13, y 4..14: 1 px gilt bevel, a darker inner fillet
    i.rect(2, 4, 13, 14, G)
    i.rect(2, 4, 13, 4, GH)
    i.rect(2, 4, 2, 14, GH)
    i.rect(13, 5, 13, 14, gd)
    i.rect(3, 14, 13, 14, gd)
    i.rect(3, 5, 12, 13, g)
    # Canvas x 4..11, y 6..12: red velvet
    i.rect(4, 6, 11, 12, V)
    # Bust: hair, face with two eyes, neck, shoulders
    i.rect(5, 6, 10, 7, HAIR)
    i.rect(6, 7, 9, 9, SKIN)
    i.set(5, 8, SKIN); i.set(10, 8, SKIN)
    i.set(6, 8, EYE); i.set(9, 8, EYE)
    i.rect(6, 9, 9, 9, skin)
    i.rect(5, 10, 10, 12, SH)
    i.rect(4, 12, 11, 12, sh)
    i.set(4, 11, SH); i.set(11, 11, SH)
    i.rect(7, 10, 8, 10, skin)
    return i.p
