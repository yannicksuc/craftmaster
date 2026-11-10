"""Offline understanding: a few fixed French patterns, no Claude. Opt-in only (REGIE_UNDERSTANDING=offline):
for tests without an API key, or as an emergency mode without internet. Deliberately narrow — anything else
answers "not understood" and does nothing.

Understood: "caméra <N>" · "plan large" · "plan sur le trône" · "le podium" · "suis <joueur>" · "générique" /
"générique de fin" · "réalisation automatique" · "montre la tâche" · "montre les scores" ·
"annule" · "ouvrez / fermez les rideaux" · "manche suivante" · "mettez à jour les scores" / "publie" ·
"la révélation" · "<N> point(s) pour|à <joueur> [pour|sur <manche>]" · "<joueur> prend <N> point(s) [sur <manche>]" ·
"<joueur> a fait <N> secondes|minutes|blocs|mètres" · "<joueur> est disqualifié" · "applique le barème" · "remets les buzzers".
"""
from __future__ import annotations

import re
import time

from .context import ShowContext
from .understand import Understanding
from .validate import FRENCH_NUMBERS, check

NUM = r"(?P<n>\d+|" + "|".join(sorted(FRENCH_NUMBERS, key=len, reverse=True)) + r")"
MANCHE_TAIL = r"(?:\s+(?:pour|sur|dans)\s+(?:le |la |les |l'|l’)?(?P<manche>.+?))?"
UNITS = {"seconde": "time", "secondes": "time", "s": "time", "minute": "time", "minutes": "time",
         "bloc": "count", "blocs": "count", "point": None, "mètre": "distance", "mètres": "distance", "metres": "distance",
         "kilo": "weight", "kilos": "weight", "kg": "weight"}
RESULT_PATTERN = re.compile(rf"^(?P<player>.+?)\s+(?:a fait|fait|a mis|a)\s+(?P<v>\d+(?:[,.]\d+)?|"
                            + "|".join(sorted(FRENCH_NUMBERS, key=len, reverse=True)) + r")\s+(?P<unit>"
                            + "|".join(sorted((u for u in UNITS if u != "point"), key=len, reverse=True)) + r")$")
SCORE_PATTERNS = [
    re.compile(rf"^(?:et )?{NUM}\s+points?\s+(?:pour|à|a)\s+(?P<player>.+?){MANCHE_TAIL}$"),
    re.compile(rf"^(?P<player>.+?)\s+(?:prend|gagne|a)\s+{NUM}\s+points?{MANCHE_TAIL}$"),
]


def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[.!?,;:«»\"]", " ", text.lower())).strip()


class OfflineUnderstander:
    def understand(self, ctx: ShowContext, transcript: str) -> Understanding:
        t = time.perf_counter()
        text = _clean(transcript)
        if not text:
            return Understanding(reply="Je n'ai rien entendu.", error="empty_transcript")
        raw = self._parse(text, ctx)
        if raw is None:
            return Understanding(actions=[{"type": "say", "text": "Pas compris (mode hors ligne)."}],
                                 reply="Pas compris (mode hors ligne) : rien n'a été fait.", seconds=time.perf_counter() - t)
        checked = check(raw, ctx)
        reply = "" if checked.actions else "Rien n'a été fait (" + " ; ".join(checked.warnings) + ")"
        return Understanding(actions=checked.actions, reply=reply, warnings=checked.warnings, seconds=time.perf_counter() - t)

    @staticmethod
    def _parse(text: str, ctx: ShowContext) -> list[dict] | None:
        if re.fullmatch(r"(annule|annuler|annule ça|annule ca)", text):
            return [{"type": "undo", "count": 1}]
        if re.search(r"\bouvr\w* (les |le )?rideaux?\b", text):
            return [{"type": "curtain.open"}]
        if re.search(r"\bferm\w* (les |le )?rideaux?\b", text):
            return [{"type": "curtain.close"}]
        if re.fullmatch(r"(on passe à la )?manches? suivantes?", text):
            return [{"type": "manche.start", "manche": "next"}]
        if re.search(r"(mett?\w* à jour (les scores|le classement)|^publie)", text):
            return [{"type": "scores.publish"}]
        if re.search(r"\brévélation\b", text):
            return [{"type": "reveal.start"}]
        if re.search(r"\bappliqu\w* (le |les )?barèmes?\b", text):
            return [{"type": "results.apply"}]
        if re.search(r"\b(remets?|réinitialise|reset)\w* (les )?buzzers?\b", text):
            return [{"type": "buzzer.reset"}]
        m = re.match(r"^(?P<player>.+?)\s+est\s+disqualifiée?$", text)
        if m:
            return [{"type": "result.dq", "player": m.group("player")}]
        m = RESULT_PATTERN.match(text)
        if m:
            v = m.group("v")
            v = str(FRENCH_NUMBERS[v]) if v in FRENCH_NUMBERS else v
            unit = m.group("unit")
            if unit.startswith("minute"):
                v = str(float(v.replace(",", ".")) * 60).rstrip("0").rstrip(".")
            action = {"type": "result.set", "player": m.group("player"), "value": v}
            if UNITS.get(unit):
                action["kind"] = UNITS[unit]
            return [action]
        # TV direction (chantier E)
        m = re.fullmatch(r"(?:la |sur la )?cam[ée]ra " + NUM, text)
        if m:
            n = m.group("n")
            return [{"type": "camera.cut", "camera": str(FRENCH_NUMBERS.get(n, n))}]
        if re.fullmatch(r"(?:un |le )?plan (?:large|général|general)", text):
            return [{"type": "camera.cut", "camera": "large"}]
        if re.fullmatch(r"(?:un |le )?plan (?:sur le |du )?tr[ôo]ne", text):
            return [{"type": "camera.cut", "camera": "trone"}]
        if re.fullmatch(r"(?:plan (?:sur le |du )?|caméra (?:sur le )?|le )?podium", text):
            return [{"type": "camera.cut", "camera": "podium"}]
        m = re.fullmatch(r"(?:suis|suivez|on suit|caméra sur) (?P<player>.+)", text)
        if m:
            return [{"type": "camera.follow", "player": m.group("player")}]
        if re.fullmatch(r"(?:lance )?(?:le )?générique de fin", text):
            return [{"type": "show.outro"}]
        if re.fullmatch(r"(?:lance )?(?:le )?générique(?: d'ouverture| de début)?", text):
            return [{"type": "show.intro"}]
        if re.fullmatch(r"réalisation auto(?:matique)?", text):
            return [{"type": "camera.auto", "enabled": True}]
        if re.fullmatch(r"(?:montre|affiche) la tâche", text):
            return [{"type": "broadcast.task"}]
        if re.fullmatch(r"(?:montre|affiche) (?:les scores|le classement)", text):
            return [{"type": "broadcast.scores", "mode": "show"}]
        for pat in SCORE_PATTERNS:
            m = pat.match(text)
            if m:
                action = {"type": "score.add", "player": m.group("player"), "points": m.group("n")}
                if m.group("manche"):
                    action["manche"] = m.group("manche")
                return [action]
        return None
