from conftest import NOW, make_context

from regie.context import ShowContext, norm


def test_norm():
    assert norm("Juste-Édible ") == "justeedible"


def test_player_exact_alias_and_approximate(ctx):
    assert ctx.player("justedible").pseudo == "JustEdible"
    assert ctx.player("Juste").pseudo == "JustEdible"          # nickname
    assert ctx.player("cycy").pseudo == "Cyllu"                # nickname, case
    assert ctx.player("Juste Édible").pseudo == "JustEdible"   # split + accent
    assert ctx.player("Cylu").pseudo == "Cyllu"                # mistranscribed
    assert ctx.player("Ensury").pseudo == "Ensuri"
    assert ctx.player("Lord Fin").pseudo == "LordFinn"
    assert ctx.player("Arreaubase").pseudo == "Arreaubaz"
    assert ctx.player("arrobaz").pseudo == "Arreaubaz"           # real Whisper outputs (whisper_check.py)
    assert ctx.player("l'or fine").pseudo == "LordFinn"
    assert ctx.player("silu").pseudo == "Cyllu"
    assert ctx.player("Machin") is None
    assert ctx.player("") is None


def test_ambiguous_player_is_not_guessed():
    c = ShowContext(make_context(players=[{"pseudo": "Alex1"}, {"pseudo": "Alex2"}]))
    assert c.player("Alex") is None
    assert c.player("alex2").pseudo == "Alex2"


def test_manche_resolution(ctx):
    assert ctx.manche("pont").id == "pont"
    assert ctx.manche("Le Pont").id == "pont"
    assert ctx.manche("bridge").id == "pont"
    assert ctx.manche("2").id == "tour"              # "l'épreuve 2"
    assert ctx.manche("labyrinte").id == "labyrinthe"  # approximate
    assert ctx.manche("current").id == "pont"
    assert ctx.manche("next").id == "tour"
    assert ctx.manche("piscine") is None


def test_next_manche_without_current_is_first_unscored():
    c = ShowContext(make_context(currentManche=None))
    assert c.next_manche().id == "tour"


def test_next_manche_at_the_end_is_none():
    assert ShowContext(make_context(currentManche="labyrinthe")).next_manche() is None


def test_idle_rule():
    # A score one minute ago: not idle
    assert not ShowContext(make_context()).idle_means_next()
    # Five minutes without a score in the running manche: next
    assert ShowContext(make_context(lastScoreAt=NOW - 5 * 60000)).idle_means_next()
    # Threshold from the context
    assert not ShowContext(make_context(lastScoreAt=NOW - 5 * 60000, idleMinutes=10)).idle_means_next()
    # The running manche was started after the last score (no score in it yet): stays on it
    assert not ShowContext(make_context(lastScoreAt=NOW - 9 * 60000, lastScoreManche="pont",
                                        currentManche="tour")).idle_means_next()
    # Never any score: nothing to compare with
    assert not ShowContext(make_context(lastScoreAt=None)).idle_means_next()


def test_missing_or_garbage_context_does_not_crash():
    for raw in (None, {}, {"seasons": "x", "players": 3, "scores": []}):
        c = ShowContext(raw)
        assert c.player("x") is None
        assert c.manche("current") is None
        assert "Joueurs" in c.summary()
        assert c.whisper_prompt().startswith("Régie CraftMaster")


def test_players_can_come_from_the_season():
    raw = make_context()
    raw["seasons"][1]["players"] = raw.pop("players")
    assert ShowContext(raw).player("Juste").pseudo == "JustEdible"


def test_whisper_prompt_and_hotwords(ctx):
    p = ctx.whisper_prompt()
    for word in ("JustEdible", "Ensuri", "Cyllu", "Arreaubaz", "LordFinn", "Cycy", "Le Pont", "La Tour"):
        assert word in p
    assert len(p) <= 800
    assert ctx.hotwords().split()[:2] == ["JustEdible", "Ensuri"]


def test_summary_for_claude(ctx):
    s = ctx.summary()
    assert "id=pont « Le Pont »" in s and "EN COURS" in s
    assert "manche suivante : tour" in s
    assert "non atteint" in s
    assert "JustEdible [Juste]" in s
    assert "score.add pont Ensuri +2" in s
    idle = ShowContext(make_context(lastScoreAt=NOW - 6 * 60000)).summary()
    assert "DÉPASSÉ" in idle
