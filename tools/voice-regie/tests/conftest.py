import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

NOW = 1_800_000_000_000  # fixed "now" (epoch ms) so the inactivity rule is deterministic


def make_context(**over):
    ctx = {
        "version": 1,
        "now": NOW,
        "idleMinutes": 4,
        "activeSeason": 4,
        "activePartie": 1,
        "seasons": [
            {"id": 3, "name": "Saison 3", "archived": True, "parties": [{"id": 1, "name": "Partie 1"}]},
            {"id": 4, "name": "Saison 4", "hidden": False, "parties": [
                {"id": 1, "name": "Partie 1", "status": "en_cours", "manches": [
                    {"id": "pont", "name": "Le Pont", "aliases": ["pont", "bridge"], "order": 1},
                    {"id": "tour", "name": "La Tour", "aliases": ["tour"], "order": 2},
                    {"id": "labyrinthe", "name": "Le Labyrinthe", "aliases": [], "order": 3},
                ]}]},
        ],
        "players": [
            {"pseudo": "JustEdible", "aliases": ["Juste"], "online": True},
            {"pseudo": "Ensuri", "aliases": [], "online": True},
            {"pseudo": "Cyllu", "aliases": ["Cycy"], "online": True},
            {"pseudo": "Arreaubaz", "aliases": [], "online": False},
            {"pseudo": "LordFinn", "aliases": ["Finn"], "online": True},
        ],
        "currentManche": "pont",
        "currentMancheStartedAt": NOW - 10 * 60000,
        "lastScoreAt": NOW - 60000,
        "lastScoreManche": "pont",
        "scores": {"live": {"pont": {"JustEdible": 3, "Ensuri": 2}}, "published": {}},
        "totals": {"live": {"JustEdible": 3, "Ensuri": 2}, "published": {}},
        "curtains": ["stage"],
        "scenes": ["pont", "tour"],
        "recentActions": ["score.add pont JustEdible +3", "score.add pont Ensuri +2"],
    }
    ctx.update(over)
    return ctx


@pytest.fixture
def ctx():
    from regie.context import ShowContext
    return ShowContext(make_context())


def tool_use(name, **inp):
    return SimpleNamespace(type="tool_use", name=name, input=inp, id="toolu_x")


def text(t):
    return SimpleNamespace(type="text", text=t)


class FakeClient:
    """Stands in for anthropic.Anthropic: records the requests, answers canned messages (no network)."""

    def __init__(self, *responses, error=None):
        self.responses = list(responses)
        self.error = error
        self.calls = []
        self.beta = SimpleNamespace(messages=SimpleNamespace(create=self._create))

    def _create(self, **params):
        self.calls.append(params)
        if self.error:
            raise self.error
        content, stop = self.responses.pop(0) if self.responses else ([], "end_turn")
        return SimpleNamespace(content=content, stop_reason=stop,
                               usage=SimpleNamespace(input_tokens=100, output_tokens=20,
                                                     cache_read_input_tokens=3000, cache_creation_input_tokens=0))
