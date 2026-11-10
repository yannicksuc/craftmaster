import json

from regie.config import Settings
from regie.subscription import SubscriptionUnderstander


def make(answer=None, raw=None, error=None):
    calls = []

    def runner(argv, stdin_text, timeout):
        calls.append((argv, stdin_text))
        if error:
            raise error
        return raw if raw is not None else json.dumps({"is_error": False, "structured_output": answer})
    return SubscriptionUnderstander(Settings(), runner=runner), calls


def test_json_actions_become_actions(ctx):
    u, calls = make({"actions": [{"tool": "score_add", "input": {"manche": "pont", "player": "JustEdible", "points": 3}}],
                     "reply": ""})
    r = u.understand(ctx, "trois points pour Juste pour le pont")
    assert r.error is None
    assert r.actions == [{"type": "score.add", "manche": "pont", "player": "JustEdible", "points": 3}]
    argv, stdin = calls[0]
    assert "-p" in argv and "--json-schema" in argv and "--system-prompt-file" in argv
    assert "trois points" in stdin


def test_prompt_lists_every_tool():
    u, _ = make({"actions": [], "reply": ""})
    assert all(t["name"] in u.system_prompt for t in u.tools)


def test_reply_only(ctx):
    u, _ = make({"actions": [], "reply": "Rien à faire."})
    r = u.understand(ctx, "bonjour")
    assert r.actions == [] and r.reply == "Rien à faire."


def test_claude_code_error_is_reported(ctx):
    u, _ = make(raw=json.dumps({"is_error": True, "result": "Not logged in"}))
    r = u.understand(ctx, "manche suivante")
    assert r.error == "claude_error" and r.actions == []


def test_missing_claude_code(ctx):
    u, _ = make(error=FileNotFoundError())
    r = u.understand(ctx, "manche suivante")
    assert r.error == "claude_error" and "introuvable" in r.reply
