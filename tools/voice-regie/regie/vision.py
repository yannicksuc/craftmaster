"""The régie agent's eyes and books, on this PC (no game needed for the books).

- ``voir``: the game takes the pictures (AgentVision in the mod) and answers with a JSON summary that names PNG files
  on this PC (``{"vision": true, "images": [{"fichier", "legende"}], "planche", "ratees", "notes", "video"}``);
  ``blocks`` turns it into a text + images tool result, so that the model really sees the pictures.
- ``wiki``: the mod's wiki pages (docs/wiki in the repository this service runs from).

Importable both as ``regie.vision`` and as ``vision`` (agent_mcp.py runs as a script from this folder).
"""
from __future__ import annotations

import base64
import json
import os
import re

MAX_IMAGES = 5          # pictures given to the model per « voir » (a contact sheet counts as one)
MAX_BYTES = 4_500_000   # per picture
WIKI = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "docs", "wiki"))


def parse(output: str) -> dict | None:
    """The vision summary in a tool's output, or None."""
    if not output or not output.lstrip().startswith("{"):
        return None
    try:
        data = json.loads(output)
    except ValueError:
        return None
    return data if isinstance(data, dict) and data.get("vision") else None


def _summary(data: dict, shown: list[str]) -> str:
    lines = [f"Vue : {data.get('titre', '')} — {len(data.get('images') or [])} image(s) prise(s) dans le rendu du flux de l'émission."]
    for i, im in enumerate(data.get("images") or [], 1):
        lines.append(f"{i}. {im.get('legende', '')}")
    for r in data.get("ratees") or []:
        lines.append(f"✗ {r.get('legende', '')} : {r.get('pourquoi', '')}")
    for n in data.get("notes") or []:
        lines.append(f"note : {n}")
    if data.get("video"):
        lines.append("Une vidéo MP4 a été faite pour LordFinn (bouton [▶ vidéo] dans son chat).")
    lines.append("LordFinn voit les mêmes images dans son chat ([aperçu]).")
    lines.append("Images jointes ci-dessous : " + ", ".join(shown) + "." if shown else "Aucune image jointe.")
    return "\n".join(lines)


def _pick(data: dict) -> list[tuple[str, str]]:
    """(label, file) of the pictures to give: the contact sheet when there are many, else each picture."""
    images = [(im.get("legende", ""), im.get("fichier", "")) for im in data.get("images") or []]
    if data.get("planche") and len(images) > MAX_IMAGES - 1:
        return [("planche contact (numérotée)", data["planche"])]
    out = []
    if data.get("planche"):
        out.append(("planche contact (numérotée)", data["planche"]))
    return out + images[: MAX_IMAGES - len(out)]


def _read(path: str) -> str | None:
    try:
        if os.path.getsize(path) > MAX_BYTES:
            return None
        with open(path, "rb") as f:
            return base64.b64encode(f.read()).decode("ascii")
    except OSError:
        return None


def blocks(output: str, fmt: str) -> list[dict] | None:
    """Tool result content with the pictures; fmt = "api" (Messages API) or "mcp". None when not a vision answer."""
    data = parse(output)
    if data is None:
        return None
    pics, shown = [], []
    for label, path in _pick(data):
        b64 = _read(path)
        if b64 is None:
            continue
        shown.append(label)
        if fmt == "mcp":
            pics.append({"type": "image", "data": b64, "mimeType": "image/png"})
        else:
            pics.append({"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": b64}})
    return [{"type": "text", "text": _summary(data, shown)}] + pics


def text(output: str) -> str:
    """The summary alone (logs, tests)."""
    data = parse(output)
    return output if data is None else _summary(data, [label for label, _ in _pick(data)])


# ---------------------------------------------------------------------------------------------- wiki


def wiki(args: dict) -> tuple[bool, str]:
    """``wiki {}`` = the pages; ``wiki {page}`` = one page; ``wiki {cherche}`` = the lines that mention a word."""
    if not os.path.isdir(WIKI):
        return False, "Wiki introuvable sur ce PC."
    pages = sorted(f[:-3] for f in os.listdir(WIKI) if f.endswith(".md"))
    page = str(args.get("page") or "").strip()
    query = str(args.get("cherche") or "").strip().lower()
    if query:
        hits = []
        for p in pages:
            with open(os.path.join(WIKI, p + ".md"), encoding="utf-8") as f:
                for n, line in enumerate(f, 1):
                    if query in line.lower():
                        hits.append(f"{p}:{n}: {line.strip()[:220]}")
        return True, "\n".join(hits[:80]) if hits else f"« {query} » n'apparaît dans aucune page."
    if not page:
        out = []
        for p in pages:
            with open(os.path.join(WIKI, p + ".md"), encoding="utf-8") as f:
                first = f.readline().strip().lstrip("# ")
            out.append(f"{p} — {first}")
        return True, "Pages du wiki :\n" + "\n".join(out)
    match = next((p for p in pages if p.lower() == page.lower().removesuffix(".md")), None)
    if match is None:
        return False, f"Page « {page} » inconnue. Pages : " + ", ".join(pages)
    with open(os.path.join(WIKI, match + ".md"), encoding="utf-8") as f:
        content = f.read()
    content = re.sub(r"\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b", "‹adresse masquée›", content)
    return True, content[:24000] + ("\n… (page coupée)" if len(content) > 24000 else "")
