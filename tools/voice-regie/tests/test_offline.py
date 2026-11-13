import pytest

from regie.config import Settings
from regie.offline import OfflineUnderstander
from regie.service import Regie

# Real Whisper transcripts of tests/audio (whisper_check.py, large-v3-turbo with the context prompt)
CASES = [
    ("Marque ce moment.", [{"type": "replay.flag"}]),
    ("Drapeau pour belle chute.", [{"type": "replay.flag", "text": "belle chute"}]),
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
    # Chantier H: the judge's results sheet
    ("Ensuri a fait 42 secondes.", [{"type": "result.set", "player": "Ensuri", "value": "42", "kind": "time"}]),
    ("Cycy a fait 12 blocs.", [{"type": "result.set", "player": "Cyllu", "value": "12", "kind": "count"}]),
    ("Juste a fait deux minutes.", [{"type": "result.set", "player": "JustEdible", "value": "120", "kind": "time"}]),
    ("Arreaubaz est disqualifié.", [{"type": "result.dq", "player": "Arreaubaz"}]),
    ("Applique le barème.", [{"type": "results.apply"}]),
    ("Remets les buzzers.", [{"type": "buzzer.reset"}]),
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
    ("Montre la tâche sur le stream.", [{"type": "broadcast.task"}]),
    ("Montre la tâche.", [{"type": "task.show"}]),
    ("Montre les scores.", [{"type": "broadcast.scores", "mode": "show"}]),
    ("Révèle la tâche.", [{"type": "task.reveal"}]),
    ("Révèle la suite.", [{"type": "task.reveal_more"}]),
    ("Montre la tâche à tout le monde.", [{"type": "task.show"}]),
    ("Donne les enveloppes.", [{"type": "envelope.give"}]),
    # Studio spotlights (chantier F)
    ("Projecteurs sur Ensuri.", [{"type": "spotlight.follow", "player": "Ensuri"}]),
    ("Lumière sur Cycy !", [{"type": "spotlight.follow", "player": "Cyllu"}]),
    ("Éteins les projecteurs.", [{"type": "spotlight.off"}]),
    ("Allumez les projecteurs.", [{"type": "spotlight.on"}]),
    ("Projecteurs fixes.", [{"type": "spotlight.stop"}]),
    # TV direction: lieux, zones, travellings, intros (chantier E)
    ("Caméra 2 du studio.", [{"type": "camera.cut", "camera": "caméra 2 du studio"}]),
    ("Caméra un d'Ensuri.", [{"type": "camera.cut", "camera": "caméra 1 d'ensuri"}]),
    ("Zone d'Ensuri.", [{"type": "camera.focus", "player": "Ensuri", "cut": True}]),
    ("Les caméras de l'équipe bleue.", [{"type": "camera.focus", "zone": "équipe bleue", "cut": True}]),
    ("Caméras du studio.", [{"type": "camera.focus", "lieu": "studio"}]),
    ("Les caméras du pont.", [{"type": "camera.focus", "lieu": "pont"}]),
    ("La caméra 3 est sur le pont.", [{"type": "camera.lieu", "camera": "3", "lieu": "pont"}]),
    ("Lance le travelling du pont.", [{"type": "camera.path", "path": "pont"}]),
    ("Lance l'intro du pont.", [{"type": "intro.play", "manche": "pont"}]),
    ("Lance l'intro de la tour.", [{"type": "intro.play", "manche": "tour"}]),
    ("Écran partagé.", [{"type": "camera.mosaic"}]),
    # Screen effects
    ("Passe la diffusion en noir et blanc.", [{"type": "fx.set", "target": "diffusion", "effect": "noir et blanc"}]),
    ("Mets Cycy en flou.", [{"type": "fx.set", "target": "Cyllu", "effect": "flou"}]),
    ("Mets tout le monde en sépia !", [{"type": "fx.set", "target": "tous", "effect": "sépia"}]),
    ("Enlève les effets.", [{"type": "fx.clear"}]),
    ("Retire les effets de Ensuri.", [{"type": "fx.clear", "target": "Ensuri"}]),
    ("Enlève les effets de la diffusion.", [{"type": "fx.clear", "target": "diffusion"}]),
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


def test_offline_mode_is_opt_in(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test")
    assert type(Regie(Settings(), transcriber=object()).understander).__name__ == "Understander"
    monkeypatch.delenv("ANTHROPIC_API_KEY")
    # No key: Claude Code and the Claude subscription
    assert type(Regie(Settings(), transcriber=object()).understander).__name__ == "SubscriptionUnderstander"
    assert type(Regie(Settings(understanding="offline"), transcriber=object()).understander).__name__ == "OfflineUnderstander"


# Replays: while one plays on LordFinn's game (context "replay"), the orders drive it
REPLAY_CASES = [
    ("Pause.", [{"type": "replay.control", "do": "pause"}]),
    ("Reprends la lecture.", [{"type": "replay.control", "do": "play"}]),
    ("Recule de 10 secondes.", [{"type": "replay.control", "do": "back", "seconds": 10}]),
    ("Avance de cinq secondes.", [{"type": "replay.control", "do": "forward", "seconds": 5}]),
    ("Au début.", [{"type": "replay.control", "do": "start"}]),
    ("Que les morts.", [{"type": "replay.control", "do": "filter", "kind": "morts"}]),
    ("Montre les cloches.", [{"type": "replay.control", "do": "filter", "kind": "cloches"}]),
    ("Les moments d'Ensuri.", [{"type": "replay.control", "do": "filter", "player": "Ensuri"}]),
    ("Les repères de la zone B.", [{"type": "replay.control", "do": "filter", "zone": "b"}]),
    ("Enlève le filtre.", [{"type": "replay.control", "do": "clear_filter"}]),
    ("Caméra libre depuis la caméra 2.", [{"type": "replay.control", "do": "mode", "mode": "libre", "camera": "2"}]),
    ("Vitesse 2.", [{"type": "replay.control", "do": "speed", "speed": "2"}]),
    ("Au ralenti.", [{"type": "replay.control", "do": "speed", "speed": "0.5"}]),
    ("Repère suivant.", [{"type": "replay.control", "do": "next_marker"}]),
    ("La zone.", [{"type": "replay.control", "do": "view", "view": "zone"}]),
    ("L'écran de Cycy.", [{"type": "replay.control", "do": "view", "view": "Cyllu"}]),
    ("Vue d'Ensuri.", [{"type": "camera.pov", "player": "Ensuri"}]),
    ("Les plans du direct.", [{"type": "replay.control", "do": "mode", "mode": "direct"}]),
    ("Ferme le replay.", [{"type": "replay.control", "do": "close"}]),
    ("Caméra 2.", [{"type": "camera.cut", "camera": "2"}]),
]


@pytest.mark.parametrize("transcript,expected", REPLAY_CASES)
def test_offline_replay(transcript, expected):
    from conftest import make_context
    from regie.context import ShowContext
    ctx = ShowContext(make_context(replay={"title": "Manche 1", "view": "zone", "views": ["zone", "Ensuri", "Cyllu"],
                                           "time": "0:20", "duration": "4:00", "markers": ["0:12 Ensuri +3"]}))
    r = OfflineUnderstander().understand(ctx, transcript)
    assert r.actions == expected and r.error is None
    assert "REPLAY EN COURS" in ctx.summary() and "Ensuri +3" in ctx.summary()


def test_pause_is_not_a_replay_order_without_a_replay(ctx):
    r = OfflineUnderstander().understand(ctx, "Pause.")
    assert all(a["type"] == "say" for a in r.actions)
def test_clips_and_screens(ctx):
    def a(t):
        return OfflineUnderstander().understand(ctx, t).actions
    assert a("Lance un clip.") == [{"type": "clip.start"}]
    assert a("Coupe le clip.") == [{"type": "clip.stop"}]
    assert a("Mets le clip sur la télé de gauche.") == [{"type": "tele.show", "tele": "gauche", "clip": "dernier"}]
    assert a("Mets le direct d'Ensuri sur la télé plateau.") == [{"type": "tele.show", "tele": "plateau", "live": "ensuri"}]
