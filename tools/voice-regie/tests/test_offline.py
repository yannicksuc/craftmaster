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
    # TV direction (chantier E)
    ("Caméra 2.", [{"type": "camera.cut", "camera": "2"}]),
    ("Caméra deux.", [{"type": "camera.cut", "camera": "2"}]),
    ("Plan large.", [{"type": "camera.cut", "camera": "large"}]),
    ("Plan sur le trône.", [{"type": "camera.cut", "camera": "trone"}]),
    ("Le podium.", [{"type": "camera.cut", "camera": "podium"}]),
    ("Suis Ensuri.", [{"type": "camera.follow", "player": "Ensuri"}]),
    ("Suis Cycy.", [{"type": "camera.follow", "player": "Cyllu"}]),
    ("Générique.", [{"type": "show.intro"}]),
    ("Lance le générique de fin.", [{"type": "show.outro"}]),
    ("Réalisation automatique.", [{"type": "camera.auto", "enabled": True}]),
    ("Montre la tâche.", [{"type": "broadcast.task"}]),
    ("Montre les scores.", [{"type": "broadcast.scores", "mode": "show"}]),
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
