"""Offline understanding: a few fixed French patterns, no Claude. Opt-in only (REGIE_UNDERSTANDING=offline):
for tests without an API key, or as an emergency mode without internet. Deliberately narrow — anything else
answers "not understood" and does nothing.

Understood: "annule" · "ouvrez / fermez les rideaux" · "manche suivante" · "mettez à jour les scores" / "publie" ·
"la révélation" · "<N> point(s) pour|à <joueur> [pour|sur <manche>]" · "<joueur> prend <N> point(s) [sur <manche>]" ·
"révèle la tâche" · "montre la tâche" · "donne les enveloppes".
"""
from __future__ import annotations

import re
import time

from .context import ShowContext
from .understand import Understanding
from .validate import FRENCH_NUMBERS, check

NUM = r"(?P<n>\d+|" + "|".join(sorted(FRENCH_NUMBERS, key=len, reverse=True)) + r")"
MANCHE_TAIL = r"(?:\s+(?:pour|sur|dans)\s+(?:le |la |les |l'|l’)?(?P<manche>.+?))?"
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
        if re.search(r"\b(révèle|révéler|dévoile)\w* (la )?tâche\b", text):
            return [{"type": "task.reveal"}]
        if re.search(r"\b(montre|affiche)\w* (la )?tâche\b", text):
            return [{"type": "task.show"}]
        if re.search(r"\b(donne|distribue)\w* (les )?enveloppes?\b", text):
            return [{"type": "envelope.give"}]
        if re.search(r"\brévélation\b", text):
            return [{"type": "reveal.start"}]
        for pat in SCORE_PATTERNS:
            m = pat.match(text)
            if m:
                action = {"type": "score.add", "player": m.group("player"), "points": m.group("n")}
                if m.group("manche"):
                    action["manche"] = m.group("manche")
                return [action]
        return None
