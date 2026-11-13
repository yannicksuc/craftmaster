"""Offline understanding: a few fixed French patterns, no Claude. Opt-in only (REGIE_UNDERSTANDING=offline):
for tests without an API key, or as an emergency mode without internet. Deliberately narrow — anything else
answers "not understood" and does nothing.

Understood: "caméra <N>" · "plan large" · "plan sur le trône" · "le podium" · "suis <joueur>" · "générique" /
"générique de fin" · "réalisation automatique" · "montre la tâche sur le stream" · "montre les scores" ·
"annule" · "ouvrez / fermez les rideaux" · "manche suivante" · "mettez à jour les scores" / "publie" ·
"la révélation" · "<N> point(s) pour|à <joueur> [pour|sur <manche>]" · "<joueur> prend <N> point(s) [sur <manche>]" ·
"<joueur> a fait <N> secondes|minutes|blocs|mètres" · "<joueur> est disqualifié" · "applique le barème" · "remets les buzzers" ·
"révèle la tâche" · "révèle la suite" · "montre la tâche" · "donne les enveloppes" ·
"projecteurs sur <joueur>" · "éteins | allume les projecteurs" · "projecteurs fixes" ·
"caméra <N> du studio | d'<joueur>" · "zone d'<joueur>" · "caméras de l'équipe bleue | du studio | du <manche>" ·
"la caméra <N> est sur <lieu>" · "lance le travelling <nom>" · "lance l'intro [du <manche>]" · "écran partagé".
"passe | mets la diffusion | <joueur> en <effet>" · "enlève | retire les effets [de <joueur> | de la diffusion]".
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
        # Garage doors: « ouvre / ferme la porte (du garage) »
        if re.search(r"\bouvr\w* (la |les )?portes? (du |de |de la )?garage\b", text):
            return [{"type": "garage.open"}]
        if re.search(r"\bferm\w* (la |les )?portes? (du |de |de la )?garage\b", text):
            return [{"type": "garage.close"}]
        # Studio spotlights (chantier F)
        m = re.fullmatch(r"(?:les )?(?:projecteurs?|lumières?|lumiere) sur (?P<player>.+)", text)
        if m:
            return [{"type": "spotlight.follow", "player": m.group("player")}]
        if re.search(r"\b(éteins|eteins|éteignez|coupez?) (les |le )?projecteurs?\b", text):
            return [{"type": "spotlight.off"}]
        if re.search(r"\ballum\w* (les |le )?projecteurs?\b", text):
            return [{"type": "spotlight.on"}]
        if re.fullmatch(r"(?:les )?projecteurs? fixes?", text):
            return [{"type": "spotlight.stop"}]
        # Screen effects: "passe la diffusion en noir et blanc", "mets Ensuri en flou", "enlève les effets"
        m = re.fullmatch(r"(?:enl[èe]v\w*|retir\w*|coupe\w*|plus d.|supprim\w*) (?:les |l.)?effets?"
                         r"(?: (?:de |du |sur |pour )(?P<who>.+))?", text)
        if m:
            who = m.group("who")
            if not who:
                return [{"type": "fx.clear"}]
            who = re.sub(r"^(la |le |l.)", "", who)
            return [{"type": "fx.clear", "target": "diffusion" if who in ("diffusion", "stream", "live") else who}]
        m = re.fullmatch(r"(?:passe|mets|met|mettez|passez) (?P<who>la diffusion|le stream|le live|tout le monde|tous|"
                         r"les candidats|.+?) en (?:mode |effet )?(?P<fx>.+)", text)
        if m:
            who = m.group("who")
            target = {"la diffusion": "diffusion", "le stream": "diffusion", "le live": "diffusion", "tout le monde": "tous",
                      "tous": "tous", "les candidats": "candidats"}.get(who, who)
            return [{"type": "fx.set", "target": target, "effect": m.group("fx").replace(" plus ", "+").replace(" avec ", "+")}]
        if re.fullmatch(r"(on passe à la )?manches? suivantes?", text):
            return [{"type": "manche.start", "manche": "next"}]
        if re.search(r"(mett?\w* à jour (les scores|le classement)|^publie)", text):
            return [{"type": "scores.publish"}]
        if re.search(r"\b(révèle|révéler|dévoile)\w* (la )?suite\b", text):
            return [{"type": "task.reveal_more"}]
        if re.search(r"\b(révèle|révéler|dévoile)\w* (la )?tâche\b", text):
            return [{"type": "task.reveal"}]
        if re.search(r"\b(montre|affiche)\w* (la )?tâche\b(?! sur le stream| sur la diffusion| à l.antenne)", text):
            return [{"type": "task.show"}]
        if re.search(r"\b(donne|distribue)\w* (les )?enveloppes?\b", text):
            return [{"type": "envelope.give"}]
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
        # TV direction, lieux / travellings / intros (chantier E)
        m = re.fullmatch(r"(?:lance |joue |envoie )?(?:le |un )?(?:travelling|trajet)s? (?:du |de la |de l'|des |d'|de )?(?P<path>.+)", text)
        if m:
            return [{"type": "camera.path", "path": m.group("path").strip()}]
        m = re.fullmatch(r"(?:lance |joue )?l'intro(?:duction)? (?:du |de la |de l'|des |d'|de )(?:manche )?(?P<manche>.+)", text)
        if m:
            return [{"type": "intro.play", "manche": m.group("manche").strip()}]
        if re.fullmatch(r"(?:lance |joue )?l'intro(?:duction)?(?: de la manche)?", text):
            return [{"type": "intro.play"}]
        m = re.fullmatch(r"(?:la )?cam[ée]ra " + NUM + r" est (?:sur|dans|à|au) (?:le |la |l'|les )?(?P<lieu>.+)", text)
        if m:
            n = m.group("n")
            return [{"type": "camera.lieu", "camera": str(FRENCH_NUMBERS.get(n, n)), "lieu": m.group("lieu").strip()}]
        if re.fullmatch(r"(?:les )?cam[ée]ras du (?:studio|plateau)", text):
            return [{"type": "camera.focus", "lieu": "studio"}]
        m = re.fullmatch(r"(?:la )?(?:zone|salle|chambre) (?:de |du |d')(?P<who>.+)", text) \
            or re.fullmatch(r"(?:les )?cam[ée]ras (?:de |du |d')(?P<who>.+)", text)
        if m:
            who = m.group("who").strip()
            if ctx.manche(who) is not None:
                return [{"type": "camera.focus", "lieu": ctx.manche(who).id}]
            if ctx.player(who) is not None:
                return [{"type": "camera.focus", "player": who, "cut": True}]
            return [{"type": "camera.focus", "zone": re.sub(r"^(?:l'|la |le )", "", who), "cut": True}]
        if re.fullmatch(r"(?:un |l')?(?:écran partagé|mosaïque|split screen)", text):
            return [{"type": "camera.mosaic"}]
        m = re.fullmatch(r"(?:la |sur la )?cam[ée]ra " + NUM + r" (?P<of>du studio|du plateau|d'.+|de .+|du .+)", text)
        if m:
            n = m.group("n")
            return [{"type": "camera.cut", "camera": f"caméra {FRENCH_NUMBERS.get(n, n)} {m.group('of')}"}]
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
        if re.fullmatch(r"(?:montre|affiche) la tâche (?:sur le stream|sur la diffusion|à l.antenne)", text):
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
