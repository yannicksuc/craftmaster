#!/usr/bin/env python3
"""End-to-end check of the mod on a real dev server, driven over RCON.

Setup (once): run-server/server.properties with
    online-mode=false, enable-rcon=true, rcon.port=25692, rcon.password=cmtest,
    level-type=minecraft:flat, pause-when-empty-seconds=0   (a paused server would freeze the curtain animation)
and, to test the import, the plugin's file in run-server/plugins/CraftMaster/config.yml
(delete run-server/config/craftmaster/ first so it is imported again).

Then:  .\\gradlew.bat runServer   and, once it is up:   python3 scripts/server_smoke_test.py [host]
(from WSL, host = the Windows host IP: ip route | awk '/default/{print $3}').
Uses season 90, a season named "Smoke" (deleted and created again at each run, the active season is put back at
the end) and the blocks around x=100..135, z=100..135 so it does not touch real data.
"""
import re
import socket
import struct
import sys
import time

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
# Optional second argument: the RCON port (another checkout's dev server)
PORT, PASSWORD = (int(sys.argv[2]) if len(sys.argv) > 2 else 25692), "cmtest"


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

    def _packet(self, rid, kind, body):
        data = struct.pack("<ii", rid, kind) + body.encode("utf-8") + b"\0\0"
        self.s.sendall(struct.pack("<i", len(data)) + data)

    def _reply(self):
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        return struct.unpack("<i", data[:4])[0], data[8:-2]

    def _send(self, kind, body):
        self._packet(1, kind, body)
        if kind == 3:
            return self._reply()[1].decode("utf-8", "replace")
        # Long replies come in several packets: a second, empty command marks the end of the first one's reply
        self._packet(2, 2, "")
        out = b""
        while True:
            rid, part = self._reply()
            if rid == 2:
                return out.decode("utf-8", "replace")
            out += part

    def _read(self, n):
        out = b""
        while len(out) < n:
            chunk = self.s.recv(n - len(out))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            out += chunk
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

# ---------------------------------------------------------------- v3: the show (seasons, rounds, live, undo, stage)
listing = cmd("craftMasterSeason list")
previous = re.search(r"(\d+)\. [^\n]*?★ active", listing)
for old in re.findall(r"(\d+)\. Smoke\b", listing):
    cmd(f"craftMasterSeason delete {old}")
check("season create", "créée" in cmd("craftMasterSeason create Smoke"))
smoke = re.search(r"(\d+)\. Smoke\b", cmd("cfse list")).group(1)
check("player with nicknames", "Ali" in cmd("craftMasterPlayer add Alice Ali, Lili"))
check("player", "ajouté" in cmd("cfpl add Bob"))
check("nickname taken refused", "désigne déjà" in cmd("cfpl add Carl Ali"))
check("round create", "n° 1" in cmd("craftMasterManche create Pont"))
check("round with timer", "chrono 0:05" in cmd("cfma create Tour 5"))
check("round inserted", "ajoutée" in cmd("cfma create Finale"))
check("round list", "Tour" in cmd("craftmastermanche list") and "Partie" not in cmd("cfma list").split("\n")[0])
check("round nicknames", "pt" in cmd("cfma aliases Pont pt, bridge"))
check("round order", "n° 1" in cmd("cfma order Finale 1"))
cmd("cfma order Finale 3")
check("register stage curtain", "enregistré" in cmd("craftMasterStage curtain set stage 100 -60 106"))

out = cmd("craftMasterManche start next")
check("start next round", "Manche 1 — Pont lancée" in out, out)
check("give points by nickname", "+3 pour Alice" in cmd("craftMasterGive Ali 3"))
check("give points", "+5 pour Bob" in cmd("cfg Bob 5"))
check("status", "1 — Pont" in cmd("craftMasterStatus"))
out = cmd(f"craftMasterRanking chat {smoke}")
check("organisers see unpublished points", "+5 non publié" in out, out)
check("publish", "Scores publiés" in cmd("craftMasterPublish"))
check("nothing unpublished after publish", "non publié" not in cmd(f"cfrk chat {smoke}"))
check("give then undo", "Annulé : +2 pour Bob" in (cmd("cfg Bob 2"), cmd("craftMasterUndo"))[1])
check("undo restored", "5 pts" in cmd("cfpl list"))
check("history", "Scores publiés" in cmd("craftMasterHistory"))
out = cmd("cfma next")
check("next round with timer", "Manche 2 — Tour lancée (chrono 0:05)" in out, out)
check("timer runs", "Chrono : Tour" in cmd("cfst"))
ctx = cmd("craftMasterStatus context")
check("voice context", '"currentManche"' in ctx and '"name": "Tour"' in ctx and '"aliases"' in ctx and '"remaining"' in ctx, ctx[:300])
check("timer stop", "Chrono arrêté" in cmd("craftMasterTimer stop"))
check("no timer left", "Aucun chrono" in cmd("cfti stop"))
check("round end", "Fin de la manche" in cmd("cfma end"))
check("no current round", "aucune manche en cours" in cmd("cfg Bob 1"))
check("points for a named round", "+1 pour Bob" in cmd("cfg Bob 1 Tour"))
check("unknown round", "introuvable" in cmd("cfg Bob 1 Nulle"))

# Curtains by name
check("named curtain close", "Fermeture du rideau « stage »" in cmd("craftMasterStage curtain close"))
wait(2.5)
check("stage curtain closed", "passed" in cmd("execute if block 103 -59 106 craftmaster:red_curtain[open=false]"))
check("named curtain open", "Ouverture" in cmd("cfsg curtain open stage"))
check("unknown curtain", "inconnu" in cmd("cfsg curtain open fond"))

# Sceneries: save a 3x3x3 box, place it at the anchor
cmd("fill 120 -60 120 122 -58 122 minecraft:air")
cmd("fill 130 -60 130 132 -58 132 minecraft:air")
cmd("setblock 120 -60 120 minecraft:gold_block")
cmd("setblock 122 -58 122 minecraft:diamond_block")
check("scene save", "enregistré (3 × 3 × 3)" in cmd("craftMasterStage scene save deco 120 -60 120 122 -58 122"))
check("anchor", "Ancre" in cmd("cfsg anchor 130 -60 130"))
check("scene place", "posé" in cmd("cfsg scene place deco"))
check("scene blocks placed", "passed" in cmd("execute if block 130 -60 130 minecraft:gold_block")
      and "passed" in cmd("execute if block 132 -58 132 minecraft:diamond_block"))
check("stage round with scenery", "Décor" in cmd("cfma scene Finale deco") and "scène" in cmd("cfma stage Finale true"))

# Games
check("game create", "active" in cmd("craftMasterPartie create Revanche"))
check("game list", "Revanche" in cmd("cfpa list"))
check("game status", "terminee" in cmd("cfpa status \"Partie 1\" terminée"))
check("game back", "active" in cmd("cfpa activate \"Partie 1\""))
check("game delete", "supprimée" in cmd("cfpa delete Revanche"))

# Staged reveal (short timings), then it can be interrupted
check("reveal timing", "tambour 0.5 s" in cmd("craftMasterReveal timing 0.5 0.5 0.5 0.5"))
cmd("cfg Bob 4 Pont")
check("reveal start", "lancée" in cmd("craftMasterReveal start"))
check("reveal running", "Révélation en cours" in cmd("cfst"))
wait(7)
check("reveal published", "non publié" not in cmd(f"cfrk chat {smoke}"))
check("curtain open after the reveal", "passed" in cmd("execute if block 103 -59 106 craftmaster:red_curtain[open=true]"))
check("reveal stop when none", "Aucune révélation" in cmd("cfrv stop"))
cmd("cfrv timing 3 4 3 5")

# ---------------------------------------------------------------- TV direction: lieux, zones, travellings, intros
# (chantier E). Placing cameras and recording steps uses a player's eyes: only with LordFinn connected.
check("keys address the studio", "le studio" in cmd("cam focus"))
check("unknown lieu", "inconnu" in cmd("cam focus nullepart"))
if "LordFinn" in cmd("list"):
    P = "execute as LordFinn at @s run "
    cmd("tp LordFinn 110 -58 110 0 0")
    check("camera in a round's zone", "posée" in cmd(P + "cam here pont:smoke-zone:1 Salle test"))
    check("camera in the studio", "posée" in cmd(P + "cam here studio:17 Test"))
    out = cmd("cam lieu studio:17 Pont")
    check("camera moved to the round", "pont:17" in out, out)
    cmd("tp LordFinn 112 -58 110 90 10")
    check("path created", "créé" in cmd(P + "cam trajet smoke point"))
    cmd("tp LordFinn 116 -57 114 45 5")
    check("path second step", "étape 2" in cmd(P + "cam trajet smoke point 2"))
    check("path hold", "pause de 1 s" in cmd("cam trajet smoke pause 1 1"))
    check("path listed", "smoke" in cmd("cam trajets"))
    check("path on air", "trajet « smoke »" in cmd("cam jouer smoke"))
    check("intro shot (path)", "plan 1" in cmd("tv intro Pont ajouter trajet smoke"))
    check("intro shot (zone camera)", "plan 2" in cmd("tv intro Pont ajouter camera smoke-zone:1 2 enchaine sepia"))
    check("intro plays at start", "lancement" in cmd("tv intro Pont auto on"))
    cmd("cfma start Pont")
    out = cmd("cam list")
    check("intro running", "intro en cours" in out, out)
    out = cmd("cam focus")
    check("keys follow the round", "Pont" in out, out)
    out = cmd("cam cut studio:1")
    check("explicit studio camera", "studio" in out or "Aucune" in out, out)
    check("zone focus", "smoke-zone" in cmd("cam zone smoke-zone"))
    check("show stop", "arrêté" in cmd("tv stop"))
    cmd("cfma end")
    check("keys back to the studio", "le studio" in cmd("cam focus"))
    check("undo intro change", "Annulé" in (cmd("tv intro Pont auto off"), cmd("craftMasterUndo"))[1])
    cmd("cam remove pont:smoke-zone:1")
    cmd("cam remove pont:17")
    check("path deleted", "effacé" in cmd("cam trajet smoke effacer"))
else:
    print("(TV direction: connect LordFinn to also check cameras in lieux, travellings and intros)")
# Screen effects (docs/wiki/Effets.md): the broadcast picture, unknown effects, wearables, status
check("fx broadcast", "Diffusion : fish-eye + sépia + masque rond" in cmd("craftMasterFx diffusion masque_rond+sepia+fisheye 5"))
check("fx preset", "fish-eye + masque rond" in cmd("fx diffusion judas"))
check("fx status", "Diffusion : fish-eye + masque rond" in cmd("fx etat"))
check("fx unknown effect refused", "inconnu" in cmd("fx diffusion arc-en-ciel"))
check("fx player offline refused", "pas connecté" in cmd("fx Personne sepia"))
check("fx everything off", "retirés" in cmd("fx aucun"))
check("fx list", "masque_rond" in cmd("fx liste"))
for item in ("blurry_glasses", "peephole_glasses", "pumpkin_mask", "vhs_headset", "binocular_goggles"):
    out = cmd(f"give @a craftmaster:{item}")
    check(f"wearable {item}", "Gave" in out or "No player" in out, out)
out = cmd("give @a minecraft:carved_pumpkin[craftmaster:screen_fx=\"sepia+vignette\"]")
check("screen_fx component on any item", "Gave" in out or "No player" in out, out)

# Cleanup: the test season goes, the previous active season comes back
cmd("cfsg curtain remove stage")
check("season delete", "supprimée" in cmd(f"craftMasterSeason delete {smoke}"))
if previous:
    cmd(f"cfse activate {previous.group(1)}")

print()
print("FAILURES:", failures)
sys.exit(1 if failures else 0)
