from conftest import NOW, make_context

from regie.context import ShowContext
from regie.schema import ACTIONS, TOOL_TO_ACTION, claude_tools
from regie.validate import check

SPEC_ACTIONS = """score.set score.add score.remove scores.publish reveal.start undo player.add player.update
player.remove season.create season.update season.archive season.delete season.activate partie.create partie.update
partie.archive partie.delete partie.activate manche.create manche.update manche.archive manche.delete manche.start
manche.end timer.stop timer.go curtain.open curtain.close garage.open garage.close garage.toggle scene.set say agent.request
result.set result.dq result.remove results.clear results.config results.apply stopwatch.start stopwatch.stop
stopwatch.reset buzzer.reset
camera.cut camera.follow camera.auto broadcast.lowerthird broadcast.task broadcast.scores broadcast.letterbox
show.intro show.outro show.stop
task.set task.clear task.reveal task.hide task.show envelope.give task.reveal_more
spotlight.follow spotlight.stop spotlight.on spotlight.off
camera.path camera.focus camera.lieu camera.mosaic camera.split camera.pov intro.play intro.set intro.shot.add intro.shot.remove intro.auto intro.fit intro.shot.move fx.set fx.clear replay.control replay.flag tele.show clip.start clip.stop
spotlight.set look.play look.save cue.play cue.stop stage.blackout
camera.goto camera.stay camera.framing camera.avoid
spectators.set voice.mode diffusion.set""".split()


def test_lieux_and_intro_actions_are_normalized(ctx):
    r = check([
        {"type": "camera.focus", "player": "cycy", "cut": "oui"},
        {"type": "camera.path", "path": "pont"},
        {"type": "intro.play", "manche": "Le Pont"},
        {"type": "intro.shot.add", "manche": "tour", "path": "survol", "seconds": "6"},
        {"type": "camera.lieu", "camera": "3"},
    ], ctx)
    assert r.actions[0] == {"type": "camera.focus", "player": "Cyllu", "cut": True}
    assert r.actions[1] == {"type": "camera.path", "path": "pont"}
    assert r.actions[2] == {"type": "intro.play", "manche": "pont"}
    assert r.actions[3] == {"type": "intro.shot.add", "manche": "tour", "path": "survol", "seconds": 6}
    assert len(r.actions) == 4 and any("lieu" in w for w in r.warnings)


def test_context_lists_cameras_by_lieu():
    c = ShowContext(make_context(cameras=[
        {"number": 1, "name": "Plan large", "role": "large", "key": "studio:1", "lieu": "studio"},
        {"number": 1, "name": "Caméra 1", "role": "", "key": "pont:ensuri:1", "lieu": "pont", "zone": "ensuri"},
    ], cameraFocus="pont:ensuri", paths=[{"name": "survol", "lieu": "pont", "seconds": 9.5}]))
    text = c.summary()
    assert "Caméras (pont:ensuri" in text and "1 « Caméra 1 »" in text
    assert "studio:1 « Plan large »" in text
    assert "survol (pont, 9.5 s)" in text


def test_schema_matches_the_spec():
    assert sorted(ACTIONS) == sorted(SPEC_ACTIONS)


def test_tools_are_strict_and_well_formed():
    tools = claude_tools()
    assert len(tools) == len(ACTIONS)
    for t in tools:
        assert TOOL_TO_ACTION[t["name"]] in ACTIONS
        assert "." not in t["name"]
        s = t["input_schema"]
        assert t["strict"] and s["additionalProperties"] is False
        assert set(s["required"]) <= set(s["properties"])
    by_name = {t["name"]: t for t in tools}
    assert by_name["score_add"]["input_schema"]["required"] == ["player", "points"]
    assert "season" in by_name["score_add"]["input_schema"]["properties"]
    assert "season" not in by_name["say"]["input_schema"]["properties"]
    assert claude_tools() == tools  # deterministic (cached prefix)


def test_normalization(ctx):
    r = check([
        {"type": "score.add", "player": "juste", "points": "3"},
        {"type": "score.set", "manche": "Le Pont", "player": "Cycy", "value": 7.0},
        {"type": "manche.update", "manche": "2", "name": " Tour infernale "},
        {"type": "season.update", "season": "4", "hidden": "oui"},
        {"type": "player.add", "pseudo": "Notch", "aliases": "Notchy"},
        {"type": "undo", "count": 500},
    ], ctx)
    assert r.warnings == []
    assert r.actions == [
        {"type": "score.add", "player": "JustEdible", "points": 3, "manche": "current"},
        {"type": "score.set", "manche": "pont", "player": "Cyllu", "value": 7},
        {"type": "manche.update", "manche": "tour", "name": "Tour infernale"},
        {"type": "season.update", "season": 4, "hidden": True},
        {"type": "player.add", "pseudo": "Notch", "aliases": ["Notchy"]},
        {"type": "undo", "count": 50},
    ]


def test_invalid_actions_are_dropped_with_a_reason(ctx):
    r = check([
        {"type": "score.add", "player": "Machin", "points": 3},
        {"type": "score.add", "player": "Ensuri", "points": "beaucoup"},
        {"type": "score.add", "player": "Ensuri"},
        {"type": "manche.start", "manche": "piscine"},
        {"type": "teleport", "player": "Ensuri"},
        "garbage",
        {"type": "curtain.open"},
    ], ctx)
    assert r.actions == [{"type": "curtain.open"}]
    assert len(r.warnings) == 6
    assert any("Machin" in w for w in r.warnings)


def test_french_number_words(ctx):
    assert check([{"type": "score.add", "player": "Ensuri", "points": "trois"}], ctx).actions[0]["points"] == 3


def test_idle_rule_starts_the_next_manche_first():
    c = ShowContext(make_context(lastScoreAt=NOW - 5 * 60000))
    r = check([{"type": "score.add", "player": "Ensuri", "points": 2}], c)
    assert r.actions == [{"type": "manche.start", "manche": "tour"},
                         {"type": "score.add", "player": "Ensuri", "points": 2, "manche": "tour"}]
    assert r.warnings


def test_idle_rule_leaves_named_manches_and_claude_starts_alone():
    c = ShowContext(make_context(lastScoreAt=NOW - 5 * 60000))
    # Manche named explicitly: no start
    r = check([{"type": "score.add", "manche": "pont", "player": "Ensuri", "points": 2}], c)
    assert [a["type"] for a in r.actions] == ["score.add"]
    # Claude already started the next manche: not twice, and the score follows the started manche
    r = check([{"type": "manche.start", "manche": "next"}, {"type": "score.add", "player": "Ensuri", "points": 2}], c)
    assert r.actions == [{"type": "manche.start", "manche": "tour"},
                         {"type": "score.add", "player": "Ensuri", "points": 2, "manche": "tour"}]


def test_no_idle_rule_when_recent_or_last_manche():
    r = check([{"type": "score.add", "player": "Ensuri", "points": 2}], ShowContext(make_context()))
    assert r.actions == [{"type": "score.add", "player": "Ensuri", "points": 2, "manche": "current"}]
    c = ShowContext(make_context(lastScoreAt=NOW - 5 * 60000, currentManche="labyrinthe", lastScoreManche="labyrinthe"))
    r = check([{"type": "score.add", "player": "Ensuri", "points": 2}], c)
    assert [a["type"] for a in r.actions] == ["score.add"] and r.warnings


def test_score_for_next_manche_starts_it(ctx):
    r = check([{"type": "score.add", "manche": "next", "player": "Ensuri", "points": 1}], ctx)
    assert r.actions == [{"type": "manche.start", "manche": "tour"},
                         {"type": "score.add", "manche": "tour", "player": "Ensuri", "points": 1}]


def test_undo_is_never_combined_with_the_idle_rule():
    c = ShowContext(make_context(lastScoreAt=NOW - 5 * 60000))
    r = check([{"type": "undo"}], c)
    assert r.actions == [{"type": "undo", "count": 1}]


def test_task_dictation_is_kept_and_normalized(ctx):
    """Chantier D: a dictated task keeps its text, its manche is resolved, rules stay a list."""
    out = check([{"type": "task.set", "manche": "le pont", "title": "Le pont", "text": "Construisez.\n\nVite.",
                  "rules": "Pas de TNT", "seconds": "90", "append": "oui"},
                 {"type": "envelope.give", "players": ["JustEdible"]}, {"type": "task.show"}], ctx)
    assert out.warnings == []
    a = out.actions[0]
    assert a["manche"] == "pont" and a["text"] == "Construisez.\n\nVite." and a["rules"] == ["Pas de TNT"]
    assert a["seconds"] == 90 and a["append"] is True
    assert out.actions[1]["players"] == ["JustEdible"]
    assert out.actions[2] == {"type": "task.show"}


def test_screen_effect_targets(ctx):
    r = check([{"type": "fx.set", "target": "le stream", "effect": "sepia", "seconds": "30"},
               {"type": "fx.set", "target": "Juste", "effect": "flou"},
               {"type": "fx.clear"},
               {"type": "fx.set", "target": "Machin", "effect": "flou"}], ctx)
    assert r.actions == [{"type": "fx.set", "target": "diffusion", "effect": "sepia", "seconds": 30},
                         {"type": "fx.set", "target": "JustEdible", "effect": "flou"},
                         {"type": "fx.clear"}]
    assert any("Machin" in w for w in r.warnings)


def test_tele_show_chat_wall(ctx):
    """The chat wall on a named screen: chat = twitch | jeu | tout, kept as given."""
    tools = {t["name"]: t for t in claude_tools()}
    assert "chat" in tools["tele_show"]["input_schema"]["properties"]
    r = check([{"type": "tele.show", "tele": "plateau", "chat": "twitch"},
               {"type": "tele.show", "tele": "gauche", "chat": "tout"}], ctx)
    assert r.warnings == []
    assert r.actions == [{"type": "tele.show", "tele": "plateau", "chat": "twitch"},
                         {"type": "tele.show", "tele": "gauche", "chat": "tout"}]
