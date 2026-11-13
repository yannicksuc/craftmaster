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
During a replay: "pause" · "lecture" · "recule | avance [de <N> secondes]" · "au début" · "vitesse <x>" ·
"au ralenti" · "vitesse normale" · "plus | moins vite" · "repère suivant | précédent" · "la zone" ·
"l'écran de <joueur>" · "vue de <joueur>" · "les plans du direct" · "caméra libre" · "ferme le replay".
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


def _replay(text: str, ctx: ShowContext) -> list[dict] | None:
    """Orders while a replay plays (context "replay"): playback, files, who directs."""
    def ctl(what, **kw):
        return [{"type": "replay.control", "do": what, **kw}]

    if re.fullmatch(r"(?:mets? (?:le replay )?en )?pause|stop", text):
        return ctl("pause")
    if re.fullmatch(r"(?:lecture|play|reprends?|relance|continue)(?: le replay| la lecture)?", text):
        return ctl("play")
    m = re.fullmatch(r"(?:recule|reviens|retour(?: en arrière)?|en arrière)(?: de)? " + NUM + r" secondes?", text)
    if m:
        n = m.group("n")
        return ctl("back", seconds=int(FRENCH_NUMBERS.get(n, n)))
    m = re.fullmatch(r"(?:avance|en avant)(?: de)? " + NUM + r" secondes?", text)
    if m:
        n = m.group("n")
        return ctl("forward", seconds=int(FRENCH_NUMBERS.get(n, n)))
    if re.fullmatch(r"(?:recule|reviens en arrière|en arrière)", text):
        return ctl("back")
    if re.fullmatch(r"(?:avance|en avant)", text):
        return ctl("forward")
    if re.fullmatch(r"(?:retour |reviens |va )?au début|recommence|depuis le début", text):
        return ctl("start")
    m = re.fullmatch(r"vitesse (?:x|fois )?(?P<x>\d+(?:[,.]\d+)?)", text)
    if m:
        return ctl("speed", speed=m.group("x").replace(",", "."))
    if re.fullmatch(r"vitesse normale", text):
        return ctl("speed", speed="1")
    if re.fullmatch(r"(?:au |en )?ralenti", text):
        return ctl("speed", speed="0.5")
    if re.fullmatch(r"(?:en )?accéléré|plus vite|accélère", text):
        return ctl("faster")
    if re.fullmatch(r"moins vite|ralentis", text):
        return ctl("slower")
    if re.fullmatch(r"(?:le )?(?:repère|événement|moment) suivant|(?:le )?prochain (?:repère|événement|moment)", text):
        return ctl("next_marker")
    if re.fullmatch(r"(?:le )?(?:repère|événement|moment) précédent", text):
        return ctl("prev_marker")
    m = re.fullmatch(r"(?:montre |affiche )?(?:que |juste |seulement )?les (?P<k>morts|résultats|réussites|chronos|cloches"
                     r"|drapeaux(?: des joueurs)?|points|manches|sorties de zone|projecteurs)", text)
    if m:
        return ctl("filter", kind=m.group("k"))
    m = re.fullmatch(r"(?:les )?(?:moments|repères) (?:de |d')(?:la )?zone (?P<z>.+)", text)
    if m:
        return ctl("filter", zone=m.group("z"))
    m = re.fullmatch(r"(?:les )?(?:moments|repères) (?:de |d')(?P<who>.+)", text)
    if m:
        p = ctx.player(m.group("who"))
        return ctl("filter", player=p.pseudo if p else m.group("who"))
    if re.fullmatch(r"(?:tous les (?:repères|moments)|enlève le filtre|plus de filtre|sans filtre)", text):
        return ctl("clear_filter")
    if re.fullmatch(r"(?:la |sur la |vue de la |fichier de la )?zone(?: de l'épreuve)?", text):
        return ctl("view", view="zone")
    m = re.fullmatch(r"(?:l'écran|le fichier|l'interface|le jeu) (?:de |d')(?P<who>.+)", text)
    if m:
        p = ctx.player(m.group("who"))
        return ctl("view", view=p.pseudo if p else m.group("who"))
    m = re.fullmatch(r"(?:la )?vue (?:de |d')(?P<who>.+)|par les yeux (?:de |d')(?P<who2>.+)", text)
    if m:
        return [{"type": "camera.pov", "player": m.group("who") or m.group("who2")}]
    if re.fullmatch(r"(?:la réalisation du direct|les plans du direct|réalisation direct)", text):
        return ctl("mode", mode="direct")
    m = re.fullmatch(r"(?:caméra|réalisation) libre (?:depuis|à partir de|sur) (?:la )?(?:caméra )?(?P<cam>.+)", text)
    if m:
        n = m.group("cam")
        return ctl("mode", mode="libre", camera=str(FRENCH_NUMBERS.get(n, n)))
    if re.fullmatch(r"(?:caméra|réalisation) libre", text):
        return ctl("mode", mode="libre")
    if re.fullmatch(r"réalisation manuelle", text):
        return ctl("mode", mode="manuel")
    if re.fullmatch(r"(?:ferme|quitte|arrête|coupe) le replay", text):
        return ctl("close")
    return None


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
        if ctx.replay:
            replay = _replay(text, ctx)
            if replay is not None:
                return replay
        # Live: a flag on the recording's timeline
        m = re.fullmatch(r"(?:marque ce moment|marque le moment|(?:pose |mets )?(?:un )?drapeau)(?: (?:pour |sur )?(?P<note>.+))?", text)
        if m and not ctx.replay:
            return [{"type": "replay.flag", **({"text": m.group("note")} if m.group("note") else {})}]
        if re.fullmatch(r"(annule|annuler|annule ça|annule ca)", text):
            return [{"type": "undo", "count": 1}]
        if re.search(r"\bouvr\w* (les |le )?rideaux?\b", text):
            return [{"type": "curtain.open"}]
        if re.search(r"\bferm\w* (les |le )?rideaux?\b", text):
            return [{"type": "curtain.close"}]
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
        # In-game clips and studio screens (media/)
        if re.fullmatch(r"(?:lance|démarre|demarre|commence) (?:un |le )?clip|clip", text):
            return [{"type": "clip.start"}]
        if re.fullmatch(r"(?:coupe|arrête|arrete|stoppe|termine) (?:le )?clip", text):
            return [{"type": "clip.stop"}]
        m = re.fullmatch(r"(?:mets|montre|passe) (?:le )?(?:dernier )?clip sur (?:la )?(?:télé|tele|télévision) (?:de |du )?(?P<tele>[\w-]+)", text)
        if m:
            return [{"type": "tele.show", "tele": m.group("tele"), "clip": "dernier"}]
        m = re.fullmatch(r"(?:mets|montre|passe) (?:le )?direct (?:de |d.)(?P<who>[\w-]+) sur (?:la )?(?:télé|tele) (?:de |du )?(?P<tele>[\w-]+)", text)
        if m:
            return [{"type": "tele.show", "tele": m.group("tele"), "live": m.group("who")}]
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
