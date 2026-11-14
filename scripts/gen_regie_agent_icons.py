"""HUD icons of the régie agent (docs/wiki/RegieIA.md), in Simple Voice Chat's style so they sit next to its
microphone: 16 x 16, white outline, dark dithered fill, darker shade at the bottom right.

    python scripts/gen_regie_agent_icons.py
-> src/main/resources/assets/craftmaster/textures/gui/sprites/icons/regie_agent_*.png
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/craftmaster/textures/gui/sprites/icons"
W, D1, D2, D3, D4 = (255, 255, 255, 255), (30, 26, 33, 255), (37, 37, 40, 255), (66, 66, 72, 255), (81, 81, 81, 255)


def star(cx, cy, r, p=0.8):
    """A four-point sparkle: |dx|^p + |dy|^p <= r^p."""
    return {(x, y) for x in range(16) for y in range(16)
            if abs(x - cx) ** p + abs(y - cy) ** p <= r ** p}


def draw(shapes, outline=W):
    inside = set().union(*shapes)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in inside:
        lower_right = (x + y) > 18
        img.putpixel((x, y), (D1 if (x + y) % 2 else D2) if lower_right else (D3 if (x + y) % 2 else D4))
    for x in range(16):
        for y in range(16):
            if (x, y) in inside:
                continue
            if any((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline)
    return img


def waves():
    """Two sound bars at the top right (listening)."""
    return {(14, 2), (14, 3), (14, 4), (14, 5), (11, 3), (11, 4)}


def show(img):
    for y in range(16):
        print("".join("W" if img.getpixel((x, y)) == W else ("o" if img.getpixel((x, y))[3] else ".") for x in range(16)))
    print()


icons = {
    "regie_agent_listening": draw([star(6, 9, 5.0), waves()]),
    "regie_agent_working": draw([star(6, 9, 5.0), star(12, 3, 2.0)]),
    "regie_agent_working2": draw([star(6, 9, 5.0)]),
}
OUT.mkdir(parents=True, exist_ok=True)
for name, img in icons.items():
    img.save(OUT / f"{name}.png")
    print(name)
    show(img)


# The button of Simple Voice Chat's menu (same format as its *_button.png: 32 x 32, left = on, right = off with its
# red slash, both drawn without the white outline)
def button():
    svc = None
    import zipfile, io as _io, glob, os
    for jar in glob.glob(os.path.expandvars(r"%APPDATA%\ModrinthApp\profiles\*\mods\voicechat-fabric-*26.2.jar")):
        with zipfile.ZipFile(jar) as z:
            svc = Image.open(_io.BytesIO(z.read("assets/voicechat/textures/icons/microphone_button.png"))).convert("RGBA")
        break
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    icon = draw([star(7, 7, 5.6)], outline=(0, 0, 0, 255))   # black outline, like Voice Chat's buttons
    img.paste(icon, (0, 0))
    img.paste(icon, (16, 0))
    if svc is not None:   # its red slash, pixel for pixel
        for x in range(16, 32):
            for y in range(16):
                p = svc.getpixel((x, y))
                if p[3] and p[0] > 150 and p[1] < 80:
                    img.putpixel((x, y), p)
    return img


button().save(OUT / "regie_agent_button.png")
