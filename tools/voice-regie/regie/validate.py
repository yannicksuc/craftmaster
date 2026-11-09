"""Checks and normalizes the actions Claude produced before they go to the mod.

- unknown actions, missing required fields, unknown players / manches are dropped with a warning (doing nothing
  is better than doing the wrong thing on a live show);
- numbers and booleans given as text are converted; players and manches are resolved to their exact pseudo / id
  (a safety net behind Claude, which already gets the rule);
- the inactivity rule is enforced: after idleMinutes without a score in the running manche, a score for the
  current manche means the next one, so "manche.start next" is put first.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .context import ShowContext
from .schema import ACTIONS, BOOL, INT, MANCHE, PLAYER, SCOPE, STR, STR_LIST, fields_of

SCORE_ACTIONS = {"score.set", "score.add", "score.remove"}
MAX_ACTIONS = 20


@dataclass
class Checked:
    actions: list[dict] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def _to_int(v):
    if isinstance(v, bool):
        return None
    if isinstance(v, int):
        return v
    if isinstance(v, float) and v.is_integer():
        return int(v)
    if isinstance(v, str):
        s = v.strip().replace("+", "")
        if s.lstrip("-").isdigit():
            return int(s)
        return FRENCH_NUMBERS.get(s.lower())
    return None


def _to_bool(v):
    if isinstance(v, bool):
        return v
    if isinstance(v, str) and v.lower() in ("true", "oui", "vrai", "1"):
        return True
    if isinstance(v, str) and v.lower() in ("false", "non", "faux", "0"):
        return False
    return None


FRENCH_NUMBERS = {w: i for i, w in enumerate(
    "zéro un deux trois quatre cinq six sept huit neuf dix onze douze treize quatorze quinze seize".split())}
FRENCH_NUMBERS["une"] = 1


def check(raw_actions: list, ctx: ShowContext) -> Checked:
    out = Checked()
    for raw in raw_actions[:MAX_ACTIONS]:
        if not isinstance(raw, dict) or raw.get("type") not in ACTIONS:
            out.warnings.append(f"action inconnue ignorée : {raw.get('type') if isinstance(raw, dict) else raw!r}")
            continue
        action = _check_one(raw, ctx, out.warnings)
        if action is not None:
            out.actions.append(action)
    if len(raw_actions) > MAX_ACTIONS:
        out.warnings.append(f"plus de {MAX_ACTIONS} actions : la fin est ignorée")
    _apply_idle_rule(out, ctx)
    _pin_manches(out, ctx)
    return out


def _pin_manches(out: Checked, ctx: ShowContext) -> None:
    """Turns "next" into the real manche id (known now, before anything runs), and makes the actions that follow
    a manche.start in the same order target the manche just started: "manche suivante, 3 points pour Ensuri"
    gives the points to the new manche whatever the executor does with "current" / "next" afterwards."""
    started = None
    for a in out.actions:
        if a["type"] == "manche.start":
            if a["manche"] == "next":
                m = ctx.next_manche()
                if m is not None:
                    a["manche"] = m.id
            elif a["manche"] == "current" and ctx.current:
                a["manche"] = str(ctx.current)
            started = a["manche"] if a["manche"] not in ("next", "current") else None
        elif "manche" in a and a["type"] in SCORE_ACTIONS:
            if a["manche"] in ("current", "next") and started is not None:
                a["manche"] = started
            elif a["manche"] == "next":
                m = ctx.next_manche()
                if m is not None:
                    a["manche"] = m.id


def _check_one(raw: dict, ctx: ShowContext, warnings: list[str]) -> dict | None:
    kind = raw["type"]
    action = {"type": kind}
    for fl in fields_of(kind):
        v = raw.get(fl.name)
        if v is None or v == "" or v == []:
            if fl.required:
                warnings.append(f"{kind} : champ « {fl.name} » manquant, action ignorée")
                return None
            continue
        if fl.kind == INT:
            iv = _to_int(v)
            if iv is None:
                warnings.append(f"{kind} : « {fl.name} » n'est pas un nombre ({v!r}), action ignorée")
                return None
            v = iv
        elif fl.kind == BOOL:
            bv = _to_bool(v)
            if bv is None:
                warnings.append(f"{kind} : « {fl.name} » n'est pas oui/non ({v!r}), action ignorée")
                return None
            v = bv
        elif fl.kind == STR_LIST:
            v = [str(x).strip() for x in (v if isinstance(v, list) else [v]) if str(x).strip()]
        elif fl.kind == SCOPE:
            v = int(v) if str(v).strip().isdigit() else str(v).strip()
        elif fl.kind == PLAYER:
            p = ctx.player(v)
            if p is None:
                warnings.append(f"{kind} : joueur « {v} » introuvable, action ignorée")
                return None
            v = p.pseudo
        elif fl.kind == MANCHE:
            v = str(v).strip()
            if v.lower() in ("current", "en cours", "courante"):
                v = "current"
            elif v.lower() in ("next", "suivante"):
                v = "next"
            else:
                m = ctx.manche(v)
                if m is None:
                    warnings.append(f"{kind} : manche « {v} » introuvable, action ignorée")
                    return None
                v = m.id
        elif fl.kind == STR:
            v = str(v).strip()
        action[fl.name] = v
    if kind == "undo":
        action["count"] = max(1, min(int(action.get("count", 1)), 50))
    if kind in SCORE_ACTIONS and "manche" not in action:
        action["manche"] = "current"
    return action


def _apply_idle_rule(out: Checked, ctx: ShowContext) -> None:
    kinds = [a["type"] for a in out.actions]
    if "manche.start" in kinds or "manche.end" in kinds or "undo" in kinds:
        return  # Claude already handled the manches (or the order is an undo): nothing to add
    # A score explicitly for the next manche: that manche has to be running first
    if any(a["type"] in SCORE_ACTIONS and a.get("manche") == "next" for a in out.actions) and ctx.next_manche():
        out.actions.insert(0, {"type": "manche.start", "manche": "next"})
        return
    if not ctx.idle_means_next():
        return
    scoring_current = [a for a in out.actions if a["type"] in SCORE_ACTIONS and a.get("manche") == "current"
                       and "season" not in a and "partie" not in a]
    if not scoring_current:
        return
    if ctx.next_manche() is None:
        out.warnings.append("plus de manche après la manche en cours : la note reste sur la manche en cours")
        return
    out.actions.insert(0, {"type": "manche.start", "manche": "next"})
    out.warnings.append(f"pas de note depuis plus de {ctx.idle_minutes:g} min : lancement de la manche suivante d'abord")
