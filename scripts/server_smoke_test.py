#!/usr/bin/env python3
"""End-to-end check of the mod on a real dev server, driven over RCON.

Setup (once): run-server/server.properties with
    online-mode=false, enable-rcon=true, rcon.port=25692, rcon.password=cmtest,
    level-type=minecraft:flat, pause-when-empty-seconds=0   (a paused server would freeze the curtain animation)
and, to test the import, the plugin's file in run-server/plugins/CraftMaster/config.yml
(delete run-server/config/craftmaster/ first so it is imported again).

Then:  .\\gradlew.bat runServer   and, once it is up:   python3 scripts/server_smoke_test.py [host]
(from WSL, host = the Windows host IP: ip route | awk '/default/{print $3}').
Uses season 90 and the blocks around x=100, z=100 so it does not touch real data.
"""
import socket
import struct
import sys
import time

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
PORT, PASSWORD = 25692, "cmtest"


class Rcon:
    def __init__(self):
        # RCON may need a few seconds after "RCON running" before it accepts connections
        for attempt in range(10):
            try:
                self.s = socket.create_connection((HOST, PORT), timeout=10)
                break
            except OSError:
                if attempt == 9:
                    raise
                time.sleep(3)
        self._send(3, PASSWORD)

    def _send(self, kind, body):
        data = struct.pack("<ii", 1, kind) + body.encode("utf-8") + b"\0\0"
        self.s.sendall(struct.pack("<i", len(data)) + data)
        size = struct.unpack("<i", self._read(4))[0]
        return self._read(size)[8:-2].decode("utf-8", "replace")

    def _read(self, n):
        out = b""
        while len(out) < n:
            out += self.s.recv(n - len(out))
        return out

    def run(self, cmd):
        return self._send(2, cmd)


failures = 0


def check(name, ok, detail=""):
    global failures
    print(("OK   " if ok else "FAIL ") + name + ("" if ok else f"   -> {detail}"))
    failures += 0 if ok else 1


def wait(seconds):
    time.sleep(seconds)


r = Rcon()
S = 90
cmd = r.run

def q(name):
    """How a name is typed in a command."""
    return f'"{name}"' if " " in name else name


# Clean slate for the test season
for task in ("Défi", "Le Pont", "Pont", "Tour", "Bonus"):
    for player in ("Alice", "Bob"):
        cmd(f"craftMasterRemoveScore {S} {q(task)} {player}")
    cmd(f"craftMasterRemoveTask {S} {q(task)}")
cmd(f"craftMasterRemoveBust {S} 1")
cmd(f"craftMasterHideRanking {S} false")

# Tasks: accents without quotes, quotes for spaces, lower-case names and aliases
check("add task", "ajoutée" in cmd(f"craftMasterAddTask {S} Pont"))
check("duplicate task refused", "existe déjà" in cmd(f"craftmasteraddtask {S} Pont"))
check("accented task", "ajoutée" in cmd(f"cfat {S} Défi"))
check("add then remove task", "supprimée" in (cmd(f"cfat {S} Tour"), cmd(f"craftMasterRemoveTask {S} Tour"))[1])

# Scores
check("set score", "Score défini" in cmd(f"craftMasterSetScore {S} Pont Alice 4"))
check("set score, accented task", "Score défini" in cmd(f"cfss {S} Défi Alice 3"))
check("set score, other player", "Score défini" in cmd(f"cfss {S} Pont Bob 9"))
check("add points", "-> 12" in cmd(f"craftMasterAddScore {S} Pont Bob 3"))
check("take points", "-> 10" in cmd(f"cfas {S} Pont Bob -2"))
check("unknown task warning", "Attention" in cmd(f"cfss {S} Bonus Alice 1"))
check("remove score", "retiré" in cmd(f"craftMasterRemoveScore {S} Bonus Alice"))
check("rename moves scores", "renommée" in cmd(f"craftMasterRenameTask {S} Pont \"Le Pont\""))
out = cmd(f"craftMasterRanking chat {S}")
check("ranking order", out.find("Bob") < out.find("Alice") and "10 pts" in out and "7 pts" in out, out)

# Busts are registered by a player looking at a head: checked in game (see docs/wiki/Tests.md)
check("add bust needs a player", "réservée aux joueurs" in cmd(f"craftMasterAddBust {S} 1"))
check("reload", "rechargés" in cmd("craftMasterReloadScores"))

# Hidden ranking
check("hide ranking", "caché" in cmd(f"craftMasterHideRanking {S} true"))
out = cmd(f"craftMasterRanking chat {S}")
check("admins still see a hidden ranking", "caché aux joueurs" in out, out)
check("reveal ranking", "révélé" in cmd(f"cfhr {S} false"))

# Curtains: a 7 x 3 curtain, opened by command, closed and reopened by redstone
cmd("forceload add 96 96 111 111")
cmd("fill 95 -60 106 111 -56 107 minecraft:air")
cmd("fill 100 -60 106 106 -58 106 craftmaster:red_curtain[facing=north,open=false]")
check("curtain opens", "Ouverture" in cmd("craftMasterCurtain open 100 -60 106"))
wait(2.5)
check("whole curtain open", all("passed" in cmd(f"execute if block {x} {y} 106 craftmaster:red_curtain[open=true]")
                                for x in (100, 103, 106) for y in (-60, -58)))
check("curtain closes", "Fermeture" in cmd("cfcu close 100 -60 106"))
wait(2.5)
check("whole curtain closed", all("passed" in cmd(f"execute if block {x} -59 106 craftmaster:red_curtain[open=false]")
                                  for x in (100, 103, 106)))
cmd("setblock 103 -60 107 minecraft:redstone_block")
wait(2.5)
check("redstone opens", "passed" in cmd("execute if block 100 -58 106 craftmaster:red_curtain[open=true]"))
cmd("setblock 103 -60 107 minecraft:air")
wait(2.5)
check("losing the signal closes", "passed" in cmd("execute if block 106 -58 106 craftmaster:red_curtain[open=false]"))
check("toggle", "Ouverture" in cmd("craftMasterCurtain toggle 100 -60 106"))
check("no curtain there", "Pas de rideau" in cmd("craftMasterCurtain open 100 -60 100"))

# Chairs
cmd("setblock 102 -60 102 minecraft:air")
check("chair block", "Changed" in cmd("setblock 102 -60 102 craftmaster:oak_chair[facing=south]"))
check("seats cannot be summoned", "summon" in cmd("summon craftmaster:seat 102 -60 102").lower())

print()
print("FAILURES:", failures)
sys.exit(1 if failures else 0)
