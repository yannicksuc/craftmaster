import base64
import json

from regie import vision
from regie.agent import Job


PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")


def _answer(tmp_path, n, sheet=True):
    files = []
    for i in range(n):
        f = tmp_path / f"{i:03d}.png"
        f.write_bytes(PNG)
        files.append({"fichier": str(f), "legende": f"plan {i + 1}"})
    out = {"vision": True, "titre": "Intro de « La pomme »", "images": files,
           "ratees": [{"legende": "plan 9", "pourquoi": "trop loin de ton jeu"}], "notes": ["plan 2 sauté"]}
    if sheet:
        (tmp_path / "planche.png").write_bytes(PNG)
        out["planche"] = str(tmp_path / "planche.png")
    return json.dumps(out)


def test_many_pictures_go_as_the_contact_sheet(tmp_path):
    blocks = vision.blocks(_answer(tmp_path, 8), "api")
    assert blocks[0]["type"] == "text"
    assert "8 image(s)" in blocks[0]["text"] and "trop loin" in blocks[0]["text"] and "plan 2 sauté" in blocks[0]["text"]
    images = [b for b in blocks if b["type"] == "image"]
    assert len(images) == 1 and images[0]["source"]["media_type"] == "image/png"


def test_few_pictures_go_one_by_one_for_mcp(tmp_path):
    blocks = vision.blocks(_answer(tmp_path, 2, sheet=False), "mcp")
    images = [b for b in blocks if b["type"] == "image"]
    assert len(images) == 2 and images[0]["mimeType"] == "image/png" and images[0]["data"]


def test_other_answers_stay_text():
    assert vision.blocks("Caméra 4 placée.", "api") is None
    assert vision.text("{\"ok\": 1}") == "{\"ok\": 1}"


def test_wiki_pages_and_search():
    ok, pages = vision.wiki({})
    assert ok and "Effets" in pages and "RegieIA" in pages
    ok, page = vision.wiki({"page": "effets"})
    assert ok and "mise au point" in page.lower()
    ok, hits = vision.wiki({"cherche": "focus"})
    assert ok and "Effets:" in hits
    assert not vision.wiki({"page": "nimporte"})[0]


def test_wiki_answers_without_the_game():
    job = Job("lis le wiki", "", "LordFinn")
    ok, out = job.call("wiki", {"page": "RegieIA"})
    assert ok and "voir" in out and job.queue == []
