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


def _read_png(path):
    """RGBA rows of a small PNG (8-bit RGBA or RGB, no interlace), without any library."""
    import struct
    import zlib
    data = open(path, "rb").read()
    pos, chunks = 8, {}
    idat = b""
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif kind == b"PLTE":
            chunks["plte"] = body
        elif kind == b"tRNS":
            chunks["trns"] = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ctype]
    stride = w * bpp
    rows, prev = [], bytearray(stride)
    i = 0
    for _ in range(h):
        ft = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if ft == 1:
                line[x] = (line[x] + a) & 255
            elif ft == 2:
                line[x] = (line[x] + b) & 255
            elif ft == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        row = []
        for x in range(w):
            px = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                row.append(tuple(px))
            elif ctype == 2:
                row.append(tuple(px) + (255,))
            elif ctype == 3:
                k = px[0]
                pl = chunks["plte"][k * 3:k * 3 + 3]
                alpha = chunks.get("trns", b"")[k] if k < len(chunks.get("trns", b"")) else 255
                row.append(tuple(pl) + (alpha,))
            elif ctype == 4:
                row.append((px[0], px[0], px[0], px[1]))
            else:
                row.append((px[0], px[0], px[0], 255))
        rows.append(row)
    return rows


def lordfinn_envelope(path):
    """LordFinn's sealed task envelope (his server's task_enveloppe.png), split in two: the paper (layer 0) and the
    wax seal in grey (layer 1, tinted with the envelope's wax colour). Returns (paper, wax)."""
    src = _read_png(path)
    paper = [[T] * 16 for _ in range(16)]
    wax = [[T] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            r, g, b, a = src[y][x]
            if a == 0:
                continue
            if r > g + 40 and r > b + 40:      # the red seal
                v = min(255, int(r * 255 / 180))
                wax[y][x] = (v, v, v, 255)
            else:
                paper[y][x] = (r, g, b, a)
    return paper, wax


def lordfinn_texture(path):
    return _read_png(path)


# ---------------------------------------------------------------------------- organiser tools (second batch)
BRASS = [(255, 232, 140), (240, 196, 72), (206, 150, 40), (160, 106, 28), (112, 70, 20)]   # light -> dark
WOOD_L = [(176, 130, 82), (148, 104, 62), (118, 80, 46), (86, 58, 34)]
STEEL = [(232, 236, 240), (196, 202, 210), (150, 156, 166), (104, 110, 120), (66, 70, 80)]
CHAR = [(92, 90, 100), (70, 68, 78), (52, 50, 58), (36, 34, 40)]
PAPER = [(252, 248, 236), (238, 230, 210), (214, 202, 176)]


def clipboard():
    """The organiser's clipboard (was a star wand): a wooden board, a steel clip at the top, a sheet with lines."""
    O = (54, 36, 22)
    i = Icon()
    # Board x 3..12, y 2..14
    i.rect(3, 2, 12, 14, WOOD_L[1])
    i.rect(3, 2, 12, 2, WOOD_L[0])
    i.rect(3, 2, 3, 14, WOOD_L[0])
    i.rect(12, 3, 12, 14, WOOD_L[3])
    i.rect(4, 14, 12, 14, WOOD_L[3])
    # Sheet x 4..11, y 4..13
    i.rect(4, 4, 11, 13, PAPER[0])
    i.rect(11, 5, 11, 13, PAPER[2])
    i.rect(5, 13, 11, 13, PAPER[1])
    for y, (x0, x1) in ((6, (5, 10)), (8, (5, 9)), (10, (5, 10)), (12, (5, 8))):
        i.rect(x0, y, x1, y, (120, 116, 130))
    # Ticks of the checklist
    i.set(5, 6, (60, 140, 70)); i.set(5, 8, (60, 140, 70))
    # Steel clip over the top edge
    i.rect(6, 1, 9, 3, STEEL[2])
    i.rect(6, 1, 9, 1, STEEL[0])
    i.rect(9, 2, 9, 3, STEEL[3])
    i.rect(7, 2, 8, 2, STEEL[1])
    i.outline(O)
    return i.p


def uv_torch():
    """UV torch, side view (was a violet wand): black aluminium body with a grip, a wider head on the right, the
    violet lens and its cone of light."""
    O = (24, 22, 30)
    V, v, VL = (150, 90, 230), (104, 56, 170), (214, 186, 255)
    i = Icon()
    # Body x 1..8, y 7..9 (tail cap on the left), grip rings
    i.rect(1, 7, 8, 9, CHAR[1])
    i.rect(1, 7, 8, 7, CHAR[0])
    i.rect(1, 9, 8, 9, CHAR[2])
    for x in (3, 5):
        i.rect(x, 7, x, 9, CHAR[3])
    # Switch on top
    i.set(6, 6, (200, 60, 50))
    # Head x 9..11, y 5..11, wider
    i.rect(9, 5, 11, 11, CHAR[1])
    i.rect(9, 5, 11, 5, CHAR[0])
    i.rect(9, 11, 11, 11, CHAR[2])
    # Lens on the front of the head
    i.rect(12, 6, 12, 10, V)
    i.set(12, 6, VL); i.set(12, 10, v)
    i.outline(O)
    # Cone of UV light out of the lens (soft, no outline)
    for (x, y) in ((14, 7), (14, 8), (14, 9), (15, 6), (15, 8), (15, 10)):
        i.set(x, y, (186, 150, 250))
    return i.p

def brass_seal():
    """Brass seal, upright (layer 0): a turned rosewood handle with its knob, a brass collar and the brass die at the
    bottom. Next to it lies a round wax seal it made: that is the tinted layer 1 (gen_assets)."""
    O = (60, 34, 20)
    H = [(156, 98, 62), (126, 76, 46), (96, 56, 34)]
    i = Icon()
    # Knob x 3..6, y 1..3
    i.rect(3, 1, 6, 3, H[1])
    i.rect(3, 1, 6, 1, H[0]); i.set(3, 2, H[0])
    i.rect(6, 2, 6, 3, H[2])
    i.set(3, 1, T); i.set(6, 1, T)
    # Neck and handle x 4..5, y 4..8, then widening
    i.rect(4, 4, 5, 8, H[1])
    i.rect(4, 4, 4, 8, H[0])
    i.rect(3, 7, 6, 8, H[1]); i.set(3, 7, H[0]); i.set(6, 8, H[2])
    # Brass collar and die x 2..7, y 9..12
    i.rect(3, 9, 6, 9, BRASS[1])
    i.rect(2, 10, 7, 12, BRASS[2])
    i.rect(2, 10, 7, 10, BRASS[0])
    i.rect(2, 10, 2, 12, BRASS[1])
    i.rect(7, 11, 7, 12, BRASS[3])
    i.rect(3, 12, 7, 12, BRASS[3])
    i.outline(O)
    return i.p

def judge_stopwatch():
    """Judge's stopwatch: a round steel case, white dial with its marks and red hand, the crown button on top.
    12 px round case (outline included) centred in the slot, the crown inside the 16 px: 12 x 14 like vanilla's
    clock, not edge to edge."""
    import math
    O = (44, 40, 46)
    i = Icon()
    # Crown button (y 2) and its stem (y 3, in the case's outline row); the outline adds y 1
    i.rect(6, 2, 9, 2, STEEL[1]); i.rect(6, 2, 7, 2, STEEL[0])
    i.rect(7, 3, 8, 3, STEEL[3])
    cx, cy = 8.0, 9.0    # 10 px body x 3..12, y 4..13; outline x 2..13, y 3..14
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r > 5.4:
                continue
            if r > 3.9:   # the case: lit at the top left, dark at the bottom right
                i.set(x, y, STEEL[0] if dx + dy < -2.5 else STEEL[3] if dx + dy > 2.5 else STEEL[2])
            else:         # the dial, a little shaded at the bottom right
                i.set(x, y, PAPER[1] if dx + dy > 2.0 else PAPER[0])
    # Marks at 3, 6 and 9 o'clock (the minute hand points at 12)
    for (x, y) in ((11, 8), (7, 12), (4, 8)):
        i.set(x, y, (120, 116, 126))
    # Hands from the centre: black minutes to 12, red seconds to the top right
    i.set(7, 8, (40, 36, 44)); i.set(7, 7, (40, 36, 44)); i.set(7, 6, (40, 36, 44))
    i.set(8, 7, (210, 40, 36)); i.set(9, 6, (210, 40, 36))
    i.outline(O)
    return i.p

def measuring_tape():
    """Tape measure: a yellow case with a black grip band, the yellow tape pulled out with marks, its steel hook,
    all inside the 16 px (outline included)."""
    O = (60, 40, 14)
    Y = [(255, 226, 96), (240, 196, 48), (206, 156, 30), (160, 112, 20)]
    i = Icon()
    # Case x 1..8, y 4..11
    i.rect(1, 4, 8, 11, Y[1])
    i.rect(1, 4, 8, 4, Y[0]); i.rect(1, 4, 1, 11, Y[0])
    i.rect(8, 5, 8, 11, Y[3]); i.rect(2, 11, 8, 11, Y[3])
    for (x, y) in ((1, 4), (8, 4), (1, 11), (8, 11)):
        i.set(x, y, T)
    # Black grip band, steel screw
    i.rect(3, 6, 6, 9, CHAR[2]); i.rect(3, 6, 6, 6, CHAR[1])
    i.rect(4, 7, 5, 8, CHAR[3])
    i.set(4, 7, STEEL[1])
    # Tape out of the case at the bottom right, marks, hook
    i.rect(9, 9, 13, 10, Y[0])
    i.rect(9, 10, 13, 10, Y[2])
    for x in (10, 12):
        i.set(x, 9, (40, 34, 30))
    i.rect(14, 8, 14, 10, STEEL[2]); i.set(14, 8, STEEL[1])
    i.outline(O)
    return i.p

def tally_counter():
    """Hand tally counter: chrome round body, the number window, the push button on top, the ring for the finger.
    12 x 14 with the outline (y 1..14): inside the slot, not edge to edge."""
    O = (44, 46, 54)
    i = Icon()
    # Button on top
    i.rect(6, 2, 9, 2, STEEL[1]); i.rect(6, 2, 7, 2, STEEL[0])
    i.rect(7, 3, 8, 3, STEEL[3])
    # Body x 3..12, y 4..11, soft corners
    i.rect(3, 4, 12, 11, STEEL[2])
    i.rect(3, 4, 12, 4, STEEL[0]); i.rect(3, 4, 3, 11, STEEL[1])
    i.rect(12, 5, 12, 11, STEEL[3]); i.rect(4, 11, 12, 11, STEEL[3])
    for (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
        i.set(x, y, T)
    # Number window: 4 white digits on black
    i.rect(4, 6, 11, 9, CHAR[3])
    for x in (5, 7, 9):
        i.rect(x, 7, x, 8, PAPER[0])
    i.rect(10, 7, 10, 8, (240, 196, 72))
    # Finger ring under it
    i.rect(6, 12, 9, 13, STEEL[2]); i.rect(7, 12, 8, 12, T)
    i.outline(O)
    return i.p

def _wall_board(frame, surface, mark):
    """The three drawing boards share one icon: a board on the wall, 2 px frame lit from the top left, no feet."""
    O = tuple(max(0, c - 40) for c in frame[3])
    i = Icon()
    i.rect(1, 3, 14, 13, frame[1])
    i.rect(1, 3, 14, 3, frame[0]); i.rect(1, 3, 1, 13, frame[0])
    i.rect(14, 4, 14, 13, frame[3]); i.rect(2, 13, 14, 13, frame[3])
    i.rect(2, 4, 13, 4, frame[1]); i.rect(2, 4, 2, 12, frame[1])
    i.rect(13, 5, 13, 12, frame[2]); i.rect(3, 12, 13, 12, frame[2])
    i.rect(3, 5, 12, 11, surface[0])
    i.rect(3, 9, 12, 11, surface[1])
    i.rect(3, 11, 12, 11, surface[2])
    mark(i)
    i.outline(O)
    return i


def slate_icon():
    """School slate: matte dark grey-green, a chalk line, oak frame, chalk tray."""
    oak = [(196, 160, 106), (170, 134, 84), (142, 110, 66), (110, 84, 50)]
    sl = [(58, 72, 64), (52, 64, 58), (46, 58, 52)]

    def mark(i):
        for x, y in ((4, 7), (5, 6), (6, 6), (7, 7), (8, 7), (9, 6)):
            i.set(x, y, (214, 220, 212))
        i.rect(4, 9, 7, 9, (170, 178, 170))
    i = _wall_board(oak, sl, mark)
    # Chalk tray with a stick of chalk
    i.rect(3, 14, 12, 14, oak[2])
    i.rect(9, 14, 10, 14, (240, 240, 232))
    i.set(2, 14, (70, 52, 30)); i.set(13, 14, (70, 52, 30))
    return i.p


def whiteboard_icon():
    """Whiteboard on the wall: white enamel, blue and red marker lines, aluminium frame and marker tray, no feet."""
    alu = [(236, 240, 244), (206, 212, 220), (168, 174, 184), (126, 132, 144)]
    en = [(250, 251, 252), (240, 243, 246), (226, 230, 236)]

    def mark(i):
        i.rect(4, 6, 9, 6, (52, 84, 170))
        i.rect(4, 8, 11, 8, (52, 84, 170))
        i.rect(4, 10, 7, 10, (196, 50, 46))
    i = _wall_board(alu, en, mark)
    i.rect(3, 14, 12, 14, alu[2])
    i.rect(5, 14, 6, 14, (52, 84, 170))
    i.set(2, 14, (100, 104, 116)); i.set(13, 14, (100, 104, 116))
    return i.p


def canvas_icon():
    """Painter's canvas on its stretcher: cream canvas, a little landscape, light birch frame."""
    birch = [(236, 226, 196), (214, 200, 164), (186, 170, 132), (150, 134, 100)]
    cv = [(244, 236, 214), (236, 226, 200), (222, 210, 182)]

    def mark(i):
        i.rect(3, 9, 12, 11, (110, 160, 80))
        i.rect(3, 11, 12, 11, (84, 130, 60))
        i.rect(9, 6, 10, 7, (240, 190, 60))
        i.set(5, 8, (110, 160, 80)); i.set(6, 8, (110, 160, 80)); i.set(6, 7, (110, 160, 80))
    return _wall_board(birch, cv, mark).p


def glasses_base():
    """The one pair of effect glasses (layer 0): a dark frame with temples, lenses left transparent."""
    F, f, fl = (52, 48, 58), (36, 34, 42), (86, 82, 94)
    i = Icon()
    for x0 in (1, 9):
        i.rect(x0, 5, x0 + 5, 10, F)
        i.rect(x0, 5, x0 + 5, 5, fl)
        i.rect(x0 + 1, 6, x0 + 4, 9, T)
    i.rect(7, 6, 8, 6, F)
    i.set(0, 6, f); i.set(15, 6, f)
    i.rect(1, 10, 6, 10, f); i.rect(9, 10, 14, 10, f)
    return i.p


def glasses_lens():
    """The lenses (layer 1, grey, tinted with the effect's colour): one flat tone and a glint."""
    i = Icon()
    for x0 in (2, 10):
        # One flat tone (tinted with the effect's colour), one glint: like a vanilla glass pane
        for y in range(6, 10):
            for x in range(x0, x0 + 4):
                i.set(x, y, (215, 215, 215, 255))
        i.set(x0, 6, (255, 255, 255, 255))
    return i.p
