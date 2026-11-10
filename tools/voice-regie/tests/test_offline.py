import pytest

from regie.config import Settings
from regie.offline import OfflineUnderstander
from regie.service import Regie

# Real Whisper transcripts of tests/audio (whisper_check.py, large-v3-turbo with the context prompt)
CASES = [
    ("Trois points pour Juste pour le Pont.", [{"type": "score.add", "player": "JustEdible", "points": 3, "manche": "pont"}]),
    ("Deux points pour JustEdible.", [{"type": "score.add", "player": "JustEdible", "points": 2, "manche": "current"}]),
    ("Ensuri prend 4 points sur la Tour.", [{"type": "score.add", "player": "Ensuri", "points": 4, "manche": "tour"}]),
    ("Un point pour Cycy.", [{"type": "score.add", "player": "Cyllu", "points": 1, "manche": "current"}]),
    ("Trois points à arrobaz.", [{"type": "score.add", "player": "Arreaubaz", "points": 3, "manche": "current"}]),
    ("Quatre points pour l'or fine sur le labyrinthe.",
     [{"type": "score.add", "player": "LordFinn", "points": 4, "manche": "labyrinthe"}]),
    ("Ouvrez les rideaux.", [{"type": "curtain.open"}]),
    ("Manches suivantes.", [{"type": "manche.start", "manche": "tour"}]),
    ("Mettez à jour les scores.", [{"type": "scores.publish"}]),
    ("Annule.", [{"type": "undo", "count": 1}]),
    ("Lance la révélation.", [{"type": "reveal.start"}]),
    # Studio spotlights (chantier F)
    ("Projecteurs sur Ensuri.", [{"type": "spotlight.follow", "player": "Ensuri"}]),
    ("Lumière sur Cycy !", [{"type": "spotlight.follow", "player": "Cyllu"}]),
    ("Éteins les projecteurs.", [{"type": "spotlight.off"}]),
    ("Allumez les projecteurs.", [{"type": "spotlight.on"}]),
    ("Projecteurs fixes.", [{"type": "spotlight.stop"}]),
]


@pytest.mark.parametrize("transcript,expected", CASES)
def test_offline_patterns(ctx, transcript, expected):
    r = OfflineUnderstander().understand(ctx, transcript)
    assert r.actions == expected and r.error is None


def test_offline_does_nothing_when_unsure(ctx):
    for t in ("Renomme l'épreuve 2 en Tour infernale.", "cinq points pour Machin", "bonjour à tous"):
        r = OfflineUnderstander().understand(ctx, t)
        assert all(a["type"] == "say" for a in r.actions), t
        assert r.reply


def test_offline_mode_is_opt_in():
    assert type(Regie(Settings(), transcriber=object()).understander).__name__ == "Understander"
    assert type(Regie(Settings(understanding="offline"), transcriber=object()).understander).__name__ == "OfflineUnderstander"
