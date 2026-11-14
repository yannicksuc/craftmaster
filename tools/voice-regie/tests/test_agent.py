"""The régie agent's relay (regie/agent.py, service endpoints): no Claude, no game — fakes on both sides."""
import json
import threading
import time
from types import SimpleNamespace

from regie.agent import AgentManager, Job, mask, mcp_tools
from regie.config import Settings
from regie.service import Regie


class ScriptedRunner:
    """Plays the agent: reads, changes, answers."""

    def run(self, job: Job) -> str:
        ok, out = job.call("lire", {"sujet": "ambiance", "nom": "tension"})
        assert ok and "tension" in out
        ok, out = job.call("commande", {"ligne": "ambiance modifier tension contre couleur rouge"})
        return f"Ambiance tension : {out}"


def fake_game(job: Job, stop: threading.Event, seen: list):
    """Plays the game client: takes the calls, answers them."""
    while not stop.is_set():
        for c in job.take():
            seen.append((c["tool"], c["args"]))
            job.answer(c["cid"], True, f"{c['tool']} ok ({json.dumps(c['args'], ensure_ascii=False)}) serveur 10.1.2.3")
        time.sleep(0.02)


def test_relay_round_trip():
    mgr = AgentManager(Settings(), runner=ScriptedRunner())
    job = mgr.start("rends l'ambiance tension plus rouge")
    stop, seen = threading.Event(), []
    threading.Thread(target=fake_game, args=(job, stop, seen), daemon=True).start()
    for _ in range(200):
        if job.done:
            break
        time.sleep(0.02)
    stop.set()
    assert job.done and job.error is None
    assert [t for t, _ in seen] == ["lire", "commande"]
    assert "‹adresse masquée›" in job.reply and "10.1.2.3" not in job.reply


def test_unknown_tool_and_gone_client():
    job = Job("x", "", "")
    assert job.call("tp", {}) == (False, "Outil inconnu : tp")
    job.last_poll -= 60            # the game stopped polling
    ok, out = job.call("lire", {"sujet": "resume"})
    assert not ok and "ne répond plus" in out


def test_mcp_tool_list_and_mask():
    names = [t["name"] for t in mcp_tools()]
    assert names == ["lire", "commande", "ecrire_sequence", "confirmer", "annuler"]
    assert all("inputSchema" in t for t in mcp_tools())
    assert mask("à 192.168.1.20 ou 7.5 s") == "à ‹adresse masquée› ou 7.5 s"


class FakeUnderstander:
    def understand(self, ctx, transcript):
        return SimpleNamespace(actions=[{"type": "agent.request", "demande": "Ambiance tension plus rouge"}], reply="",
                               warnings=[], error=None, seconds=0.0, usage={})


class FakeTranscriber:
    description = "fake"

    def load(self):
        pass


def test_command_routes_to_the_agent():
    started = []

    class FakeAgent:
        def start(self, text, context="", player=""):
            started.append(text)
            return SimpleNamespace(id="job42")

    regie = Regie(Settings(), transcriber=FakeTranscriber(), understander=FakeUnderstander(), agent=FakeAgent())
    out = regie.command({"text": "rends l'ambiance tension plus rouge", "context": {}})
    assert out["agentJob"] == "job42"
    assert out["actions"] == []            # nothing for the server to run directly
    assert started and started[0].startswith("rends l'ambiance tension plus rouge")
