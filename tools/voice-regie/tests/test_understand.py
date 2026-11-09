from conftest import FakeClient, make_context, text, tool_use

from regie.config import Settings
from regie.context import ShowContext
from regie.prompt import SYSTEM_PROMPT
from regie.understand import Understander


def understander(*responses, error=None, **settings):
    client = FakeClient(*responses, error=error)
    return Understander(Settings(**settings), client=client), client


def test_tool_calls_become_actions(ctx):
    u, client = understander(([tool_use("score_add", manche="pont", player="JustEdible", points=3)], "tool_use"))
    r = u.understand(ctx, "trois points pour Juste pour le pont")
    assert r.error is None
    assert r.actions == [{"type": "score.add", "manche": "pont", "player": "JustEdible", "points": 3}]
    assert r.usage["cache_read_input_tokens"] == 3000


def test_several_actions_keep_their_order(ctx):
    u, _ = understander(([tool_use("manche_start", manche="next"), tool_use("curtain_open"),
                          tool_use("say", text="C'est parti !")], "tool_use"))
    r = u.understand(ctx, "manche suivante et ouvrez les rideaux")
    assert [a["type"] for a in r.actions] == ["manche.start", "curtain.open"]   # say next to actions -> reply
    assert r.actions[0]["manche"] == "tour"
    assert r.reply == "C'est parti !"


def test_doubt_gives_a_say_and_nothing_else(ctx):
    u, _ = understander(([tool_use("say", text="Je ne trouve pas de joueur « Machin ».")], "tool_use"))
    r = u.understand(ctx, "cinq points pour Machin")
    assert r.actions == [{"type": "say", "text": "Je ne trouve pas de joueur « Machin »."}]
    assert "Machin" in r.reply


def test_plain_text_answer_is_the_reply(ctx):
    u, _ = understander(([text("Rien à faire.")], "end_turn"))
    r = u.understand(ctx, "bonjour")
    assert r.actions == [] and r.reply == "Rien à faire."


def test_dropped_actions_are_explained(ctx):
    u, _ = understander(([tool_use("score_add", player="Machin", points=5)], "tool_use"))
    r = u.understand(ctx, "cinq points pour Machin")
    assert r.actions == [] and "Machin" in r.reply


def test_request_shape_and_cache_prefix(ctx):
    u, client = understander(([], "end_turn"))
    u.understand(ctx, "manche suivante")
    other = ShowContext(make_context(currentManche="tour", players=[{"pseudo": "Notch"}]))
    u.understand(other, "annule")
    a, b = client.calls
    assert a["model"] == "claude-opus-5-5"
    assert a["output_config"] == {"effort": "low"}
    assert "thinking" not in a and "temperature" not in a
    assert a["tool_choice"] == {"type": "auto"}
    assert a["system"][0]["cache_control"] == {"type": "ephemeral"}
    assert a["system"][0]["text"] == SYSTEM_PROMPT
    # The cached prefix (tools + system) is byte-identical whatever the context
    assert a["tools"] == b["tools"] and a["system"] == b["system"]
    assert all(t["strict"] for t in a["tools"])
    # The context and the order travel in the user message
    msg = a["messages"][0]["content"]
    assert "<contexte>" in msg and "<ordre>manche suivante</ordre>" in msg and "JustEdible" in msg
    assert a["fallbacks"] == "default" and a["betas"] == ["server-side-fallback-2026-07-01"]


def test_fast_mode_and_no_fallbacks(ctx):
    u, client = understander(([], "end_turn"), fast_mode=True, fallbacks=False)
    u.understand(ctx, "annule")
    p = client.calls[0]
    assert p["speed"] == "fast" and p["betas"] == ["fast-mode-2026-02-01"] and "fallbacks" not in p


def test_api_error_is_reported_not_raised(ctx):
    u, _ = understander(error=ConnectionError("boom"))
    r = u.understand(ctx, "annule")
    assert r.error == "claude_error" and r.actions == [] and "ConnectionError" in r.reply


def test_refusal(ctx):
    u, _ = understander(([], "refusal"))
    r = u.understand(ctx, "annule")
    assert r.error == "refused" and r.actions == []


def test_empty_transcript_does_not_call_claude(ctx):
    u, client = understander()
    r = u.understand(ctx, "   ")
    assert r.error == "empty_transcript" and client.calls == []


def test_no_api_key(ctx, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    u = Understander(Settings())  # real client never created
    r = u.understand(ctx, "annule")
    assert r.error == "no_api_key" and r.actions == []
