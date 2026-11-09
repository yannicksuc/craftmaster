"""The show context sent by the mod (fr.lordfinn.craftmaster.show.ShowContext): reading it, matching spoken names
against it, and rendering it for Whisper (vocabulary) and for Claude (a compact French summary).

Expected shape (every key optional, see docs/design/emission.md section 4 "Contexte"):
  now (epoch ms), idleMinutes, activeSeason, activePartie,
  seasons[{id, name, hidden, archived, parties[{id, name, status, archived, manches[{id, name, aliases[], order,
           onStage, scene, timer, archived}]}], players[...]}],
  players[{pseudo, aliases[], online, archived}]  (players of the active season),
  currentManche (id), currentMancheStartedAt (ms), lastScoreAt (ms), lastScoreManche (id),
  scores{live{manche id: {pseudo: int}}, published{...}}, totals{live{pseudo: int}, published{...}},
  curtains[names], scenes[names], recentActions[strings or {text}]
"""
from __future__ import annotations

import difflib
import re
import time
import unicodedata
from dataclasses import dataclass, field


def norm(s: str) -> str:
    """Lower case, no accents, letters and digits only: "Juste-Édible " -> "justeedible"."""
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]", "", s)


def phonetic(s: str) -> str:
    """Rough French sound key, so Whisper's spellings meet the pseudo: "silu" ~ "Cyllu", "arrobaz" ~ "Arreaubaz"."""
    s = norm(s)
    for a, b in (("eau", "o"), ("au", "o"), ("ph", "f"), ("qu", "k"), ("ck", "k"), ("h", ""), ("y", "i"),
                 ("w", "v"), ("z", "s"), ("x", "ks")):
        s = s.replace(a, b)
    s = re.sub(r"c(?=[ei])", "s", s).replace("c", "k")
    s = re.sub(r"(.)\1+", r"\1", s)       # double letters
    return re.sub(r"e$", "", s)            # silent final e


def _as_list(v) -> list:
    return v if isinstance(v, list) else []


@dataclass
class Manche:
    id: str
    name: str
    aliases: list[str] = field(default_factory=list)
    order: int = 0
    archived: bool = False
    on_stage: bool = False
    scene: str | None = None
    timer: int = 0


@dataclass
class Player:
    pseudo: str
    aliases: list[str] = field(default_factory=list)
    online: bool = False
    archived: bool = False


class ShowContext:
    def __init__(self, raw: dict | None, idle_minutes: float = 4.0, now_ms: int | None = None):
        self.raw = raw if isinstance(raw, dict) else {}
        r = self.raw
        self.now_ms = int(r.get("now") or now_ms or time.time() * 1000)
        self.idle_minutes = float(r.get("idleMinutes") or idle_minutes)
        self.seasons = _as_list(r.get("seasons"))
        self.active_season = r.get("activeSeason")
        self.active_partie = r.get("activePartie")
        season = self._find(self.seasons, self.active_season)
        partie = self._find(_as_list(season.get("parties")) if season else [], self.active_partie)
        self.season, self.partie = season or {}, partie or {}
        self.manches = sorted((self._manche(m) for m in _as_list(self.partie.get("manches")) if isinstance(m, dict)),
                              key=lambda m: m.order)
        players = r.get("players") if isinstance(r.get("players"), list) else self.season.get("players")
        self.players = [self._player(p) for p in _as_list(players) if isinstance(p, dict) and p.get("pseudo")]
        self.current = r.get("currentManche")
        self.current_started_ms = r.get("currentMancheStartedAt")
        self.last_score_ms = r.get("lastScoreAt")
        self.last_score_manche = r.get("lastScoreManche")
        scores = r.get("scores") if isinstance(r.get("scores"), dict) else {}
        self.live = scores.get("live") if isinstance(scores.get("live"), dict) else {}
        self.published = scores.get("published") if isinstance(scores.get("published"), dict) else {}
        totals = r.get("totals") if isinstance(r.get("totals"), dict) else {}
        self.totals_live = totals.get("live") if isinstance(totals.get("live"), dict) else {}
        self.totals_published = totals.get("published") if isinstance(totals.get("published"), dict) else {}
        self.curtains = [str(c) for c in _as_list(r.get("curtains"))]
        self.scenes = [str(s) for s in _as_list(r.get("scenes"))]
        self.recent = [a if isinstance(a, str) else str(a.get("text", a)) for a in _as_list(r.get("recentActions"))][-10:]

    # ------------------------------------------------------------------ parsing helpers

    @staticmethod
    def _find(items: list, key) -> dict | None:
        items = [i for i in items if isinstance(i, dict)]
        for i in items:
            if key is not None and str(i.get("id")) == str(key):
                return i
        # No active one given: the last non-archived one
        live = [i for i in items if not i.get("archived")]
        return (live or items or [None])[-1]

    @staticmethod
    def _manche(m: dict) -> Manche:
        return Manche(id=str(m.get("id") or m.get("name")), name=str(m.get("name") or m.get("id")),
                      aliases=[str(a) for a in _as_list(m.get("aliases"))], order=int(m.get("order") or 0),
                      archived=bool(m.get("archived")), on_stage=bool(m.get("onStage")), scene=m.get("scene"),
                      timer=int(m.get("timer") or 0))

    @staticmethod
    def _player(p: dict) -> Player:
        return Player(pseudo=str(p["pseudo"]), aliases=[str(a) for a in _as_list(p.get("aliases"))],
                      online=bool(p.get("online")), archived=bool(p.get("archived")))

    # ------------------------------------------------------------------ show state

    def manche(self, ref) -> Manche | None:
        """A manche by id, name, alias (exact, then approximate); "current"/"next" resolve to the real one."""
        if ref is None:
            return None
        if ref == "current":
            return self.manche(self.current) if self.current else None
        if ref == "next":
            return self.next_manche()
        live = [m for m in self.manches if not m.archived] or self.manches
        key = norm(ref)
        for m in self.manches:
            if str(m.id) == str(ref):
                return m
        for m in live:
            if key and (norm(m.name) == key or key in (norm(a) for a in m.aliases)):
                return m
        if str(ref).isdigit():  # "l'épreuve 2" -> second manche in the order
            i = int(ref) - 1
            if 0 <= i < len(live):
                return live[i]
        return best_match(key, {m.id: [m.name, *m.aliases] for m in live}, lambda mid: self.manche(mid))

    def next_manche(self) -> Manche | None:
        live = [m for m in self.manches if not m.archived]
        if not live:
            return None
        cur = self.manche(self.current) if self.current else None
        if cur is None:
            # Nothing running: the first manche without any score yet, else the first one
            for m in live:
                if not self.live.get(m.id):
                    return m
            return live[0]
        after = [m for m in live if m.order > cur.order]
        return after[0] if after else None

    def player(self, spoken) -> Player | None:
        if not spoken:
            return None
        key = norm(spoken)
        for p in self.players:
            if norm(p.pseudo) == key or key in (norm(a) for a in p.aliases):
                return p
        return best_match(key, {p.pseudo: [p.pseudo, *p.aliases] for p in self.players},
                          lambda pseudo: next(p for p in self.players if p.pseudo == pseudo))

    def minutes_since_last_score(self) -> float | None:
        if not self.last_score_ms:
            return None
        return max(0.0, (self.now_ms - int(self.last_score_ms)) / 60000)

    def idle_means_next(self) -> bool:
        """Rule of the show: scores were given in the running manche, then nothing for idleMinutes: a new score
        without a manche is for the NEXT manche (launch it first). A manche started but not scored yet stays."""
        minutes = self.minutes_since_last_score()
        if minutes is None or minutes < self.idle_minutes:
            return False
        if self.current is None:
            return True
        if self.last_score_manche is not None:
            return str(self.last_score_manche) == str(self.current)
        return bool(self.live.get(str(self.current)))

    # ------------------------------------------------------------------ renderings

    def whisper_prompt(self) -> str:
        """A short French sentence carrying the vocabulary (Whisper reads at most ~224 tokens of prompt)."""
        names = [p.pseudo for p in self.players]
        nicks = [a for p in self.players for a in p.aliases]
        manches = [m.name for m in self.manches if not m.archived]
        parts = ["Régie CraftMaster."]
        if names:
            parts.append("Joueurs : " + ", ".join(names) + ".")
        if nicks:
            parts.append("Surnoms : " + ", ".join(nicks) + ".")
        if manches:
            parts.append("Manches : " + ", ".join(manches) + ".")
        parts.append("Trois points pour Ensuri, manche suivante, ouvrez les rideaux, mettez à jour les scores.")
        return " ".join(parts)[:800]

    def hotwords(self) -> str:
        words = [p.pseudo for p in self.players] + [a for p in self.players for a in p.aliases]
        return " ".join(dict.fromkeys(words))

    def summary(self) -> str:
        """What Claude sees of the show (French, compact)."""
        out = []
        lt = time.localtime(self.now_ms / 1000)
        out.append(f"Heure : {lt.tm_hour:02d}:{lt.tm_min:02d}")
        out.append(f"Saison active : {self.season.get('id', '?')} « {self.season.get('name', '')} »"
                   + (" (cachée aux joueurs)" if self.season.get("hidden") else ""))
        if self.partie:
            out.append(f"Partie active : {self.partie.get('id', '?')} « {self.partie.get('name', '')} »"
                       f" statut {self.partie.get('status', '?')}")
        cur = self.manche(self.current) if self.current else None
        out.append("Manches de la partie active (ordre, id, nom, surnoms) :")
        if not self.manches:
            out.append("  (aucune)")
        for m in self.manches:
            flags = []
            if cur and m.id == cur.id:
                flags.append("EN COURS")
            if m.archived:
                flags.append("archivée")
            if m.on_stage:
                flags.append("sur scène" + (f", décor {m.scene}" if m.scene else ""))
            if m.timer:
                flags.append(f"chrono {m.timer}s")
            given = self.live.get(m.id) or {}
            if given:
                flags.append("notes : " + ", ".join(f"{k} {v}" for k, v in given.items()))
            out.append(f"  {m.order}. id={m.id} « {m.name} »" + (f" surnoms [{', '.join(m.aliases)}]" if m.aliases else "")
                       + (" — " + " ; ".join(flags) if flags else ""))
        nxt = self.next_manche()
        out.append(f"Manche en cours : {cur.id if cur else 'aucune'} · manche suivante : {nxt.id if nxt else 'aucune'}")
        minutes = self.minutes_since_last_score()
        if minutes is None:
            out.append("Dernière note : jamais")
        else:
            out.append(f"Dernière note : il y a {minutes:.1f} min (manche {self.last_score_manche or '?'})")
        out.append(f"Seuil d'inactivité : {self.idle_minutes:g} min → "
                   + ("DÉPASSÉ : une note sans manche précisée va à la manche SUIVANTE (la lancer d'abord avec manche_start next)."
                      if self.idle_means_next() else "non atteint : une note sans manche va à la manche en cours."))
        out.append("Joueurs de la saison (pseudo exact, surnoms) :")
        for p in self.players:
            out.append(f"  {p.pseudo}" + (f" [{', '.join(p.aliases)}]" if p.aliases else "")
                       + (" en ligne" if p.online else "") + (" (archivé)" if p.archived else ""))
        if not self.players:
            out.append("  (aucun)")
        if self.totals_live:
            out.append("Totaux en direct : " + ", ".join(f"{k} {v}" for k, v in self.totals_live.items())
                       + (" · publiés : " + ", ".join(f"{k} {v}" for k, v in self.totals_published.items())
                          if self.totals_published else ""))
        others = []
        for s in self.seasons:
            if not isinstance(s, dict):
                continue
            ps = ", ".join(f"{p.get('id')} « {p.get('name', '')} »" for p in _as_list(s.get("parties")) if isinstance(p, dict))
            others.append(f"  saison {s.get('id')} « {s.get('name', '')} »" + (" archivée" if s.get("archived") else "")
                          + (f" — parties : {ps}" if ps else ""))
        if others:
            out.append("Saisons :")
            out.extend(others)
        out.append("Rideaux : " + (", ".join(self.curtains) if self.curtains else "stage (défaut)"))
        out.append("Décors : " + (", ".join(self.scenes) if self.scenes else "aucun"))
        if self.recent:
            out.append("Dernières actions :")
            out.extend("  " + a for a in self.recent)
        return "\n".join(out)


def best_match(key: str, candidates: dict, resolve, cutoff: float = 0.72):
    """Closest candidate (by its names, normalized) to a mistranscribed word, or None if nothing is close enough.
    A spoken word that starts a longer name ("juste" for "justedible") also counts."""
    if not key:
        return None
    scores: dict = {}
    for cid, names in candidates.items():
        for n in names:
            nn = norm(n)
            if not nn:
                continue
            score = max(difflib.SequenceMatcher(None, key, nn).ratio(),
                        difflib.SequenceMatcher(None, phonetic(key), phonetic(nn)).ratio())
            if len(key) >= 4 and (nn.startswith(key) or key.startswith(nn)):
                score = max(score, 0.8)
            scores[cid] = max(scores.get(cid, 0.0), score)
    ranked = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)
    if not ranked or ranked[0][1] < cutoff:
        return None
    # Two candidates almost as close: ambiguous, better do nothing than pick the wrong one
    if len(ranked) > 1 and ranked[0][1] - ranked[1][1] < 0.05:
        return None
    return resolve(ranked[0][0])
