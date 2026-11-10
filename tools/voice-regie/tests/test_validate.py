from conftest import NOW, make_context

from regie.context import ShowContext
from regie.schema import ACTIONS, TOOL_TO_ACTION, claude_tools
from regie.validate import check

SPEC_ACTIONS = """score.set score.add score.remove scores.publish reveal.start undo player.add player.update
player.remove season.create season.update season.archive season.delete season.activate partie.create partie.update
partie.archive partie.delete partie.activate manche.create manche.update manche.archive manche.delete manche.start
manche.end timer.stop curtain.open curtain.close scene.set say
task.set task.clear task.reveal task.hide task.show envelope.give""".split()


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
