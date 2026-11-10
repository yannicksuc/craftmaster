"""The action schema shared with the mod (docs/design/emission.md, section 4) and the Claude tools built from it.

One table, ACTIONS, is the single source of truth: tool definitions for Claude and the validation of what comes
back are both generated from it, so adding an action is one line here (plus its execution on the server side).
Claude tool names cannot contain dots: "score.set" is the tool "score_set".
"""
from __future__ import annotations

from dataclasses import dataclass

# Field kinds: how a field is described to Claude and normalized afterwards
STR, INT, BOOL, STR_LIST, PLAYER, MANCHE, SCOPE = "str", "int", "bool", "str_list", "player", "manche", "scope"


@dataclass(frozen=True)
class Field:
    name: str
    kind: str
    required: bool
    doc: str


def f(name, kind, required=False, doc=""):
    return Field(name, kind, required, doc)


MANCHE_DOC = "Manche visée : son id exact (du contexte), ou « current » (manche en cours) ou « next » (manche suivante)."
PLAYER_DOC = "Pseudo EXACT d'un joueur du contexte (résous surnoms et fautes de transcription vers le pseudo)."

# action type -> (description for Claude, fields). "season"/"partie" are added to every action but say/undo.
ACTIONS: dict[str, tuple[str, list[Field]]] = {
    "score.set": ("Fixe le score d'un joueur dans une manche (remplace la valeur).",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC),
                   f("value", INT, True, "Nouveau score.")]),
    "score.add": ("Donne (ou retire si négatif) des points à un joueur dans une manche. C'est l'action normale quand "
                  "LordFinn annonce « 3 points pour X ».",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC),
                   f("points", INT, True, "Points à ajouter (négatif pour en enlever).")]),
    "score.remove": ("Efface le score d'un joueur dans une manche.",
                     [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC)]),
    "scores.publish": ("Publie les scores (« mettez à jour les scores ») : busts, tableaux et écrans des joueurs se mettent à jour.", []),
    "reveal.start": ("Lance la révélation mise en scène du classement.", []),
    "undo": ("Annule les dernières commandes (« annule »). count = nombre de commandes à annuler.",
             [f("count", INT, doc="Nombre de commandes à annuler, 1 par défaut.")]),
    "player.add": ("Ajoute un joueur à la saison.",
                   [f("pseudo", STR, True, "Pseudo Minecraft tel que dit (orthographe la plus probable)."),
                    f("aliases", STR_LIST, doc="Surnoms pour la voix.")]),
    "player.update": ("Modifie un joueur : renommer son pseudo ou remplacer ses surnoms.",
                      [f("pseudo", PLAYER, True, PLAYER_DOC), f("newPseudo", STR, doc="Nouveau pseudo."),
                       f("aliases", STR_LIST, doc="Nouvelle liste COMPLÈTE de surnoms (reprendre les anciens à garder).")]),
    "player.remove": ("Retire un joueur de la saison.", [f("pseudo", PLAYER, True, PLAYER_DOC)]),
    "season.create": ("Crée une saison.", [f("name", STR, True, "Nom de la saison.")]),
    "season.update": ("Renomme une saison ou la cache / montre aux joueurs.",
                      [f("name", STR, doc="Nouveau nom."), f("hidden", BOOL, doc="true = classement caché aux joueurs.")]),
    "season.archive": ("Archive (range, lecture seule) ou désarchive une saison.", [f("archived", BOOL, True, "true = archiver.")]),
    "season.delete": ("Supprime une saison pour de bon (annulable).", []),
    "season.activate": ("Rend une saison active.", []),
    "partie.create": ("Crée une partie dans la saison.", [f("name", STR, True, "Nom de la partie.")]),
    "partie.update": ("Renomme une partie ou change son statut.",
                      [f("name", STR, doc="Nouveau nom."), f("status", STR, doc="preparation | en_cours | terminee")]),
    "partie.archive": ("Archive ou désarchive une partie.", [f("archived", BOOL, True, "true = archiver.")]),
    "partie.delete": ("Supprime une partie (annulable).", []),
    "partie.activate": ("Rend une partie active.", []),
    "manche.create": ("Ajoute une manche (épreuve) à la partie.",
                      [f("name", STR, True, "Nom de la manche."), f("aliases", STR_LIST, doc="Surnoms pour la voix."),
                       f("onStage", BOOL, doc="Manche jouée sur scène."), f("scene", STR, doc="Nom du décor à poser."),
                       f("timer", INT, doc="Chrono en secondes (0 = aucun)."),
                       f("after", MANCHE, doc="Placer après cette manche (id). Absent = à la fin.")]),
    "manche.update": ("Modifie une manche : nom (« renomme l'épreuve 2 en … »), surnoms, scène, chrono, ordre.",
                      [f("manche", MANCHE, True, MANCHE_DOC), f("name", STR, doc="Nouveau nom."),
                       f("aliases", STR_LIST, doc="Nouvelle liste complète de surnoms."), f("onStage", BOOL),
                       f("scene", STR), f("timer", INT, doc="Chrono en secondes."), f("order", INT, doc="Nouvelle position (1 = première).")]),
    "manche.archive": ("Archive ou désarchive une manche.", [f("manche", MANCHE, True, MANCHE_DOC), f("archived", BOOL, True)]),
    "manche.delete": ("Supprime une manche (annulable).", [f("manche", MANCHE, True, MANCHE_DOC)]),
    "manche.start": ("Lance une manche : elle devient la manche en cours, décor posé, joueurs téléportés, titre, chrono.",
                     [f("manche", MANCHE, True, MANCHE_DOC)]),
    "manche.end": ("Termine la manche en cours.", []),
    "timer.stop": ("Arrête le chrono de la manche en cours.", []),
    "curtain.open": ("Ouvre un rideau (« ouvrez les rideaux »).", [f("name", STR, doc="Nom du rideau ; absent = rideau de scène.")]),
    "curtain.close": ("Ferme un rideau.", [f("name", STR, doc="Nom du rideau ; absent = rideau de scène.")]),
    "scene.set": ("Pose un décor enregistré à l'ancre de scène.", [f("name", STR, True, "Nom du décor (du contexte).")]),
    # ---- chantier H: the judge's results sheet, stopwatches, buzzers
    "result.set": ("Note le résultat d'un candidat sur la feuille de résultats de la manche (« Ensuri a fait 42 "
                   "secondes », « Cyllu : 12 blocs », « note de 8 pour Juste »). Ne donne pas de points : c'est "
                   "results_apply qui les calcule.",
                   [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC),
                    f("value", STR, True, "La valeur telle que dite, en chiffres : « 42 », « 1:05 » (1 min 5 s), « 12,5 »."),
                    f("kind", STR, doc="Ce qui est mesuré si c'est dit : time (secondes, minutes), count (nombre), "
                                       "distance (mètres, blocs de distance), mark (note du jury), weight (kilos).")]),
    "result.dq": ("Disqualifie un candidat de la manche (« Arreaubaz est disqualifié ») ; dq false = lève la disqualification.",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC),
                   f("dq", BOOL, doc="false pour lever la disqualification ; absent = disqualifier.")]),
    "result.remove": ("Efface le résultat d'un candidat sur la feuille de la manche.",
                      [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."), f("player", PLAYER, True, PLAYER_DOC)]),
    "results.clear": ("Vide la feuille de résultats de la manche.", [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours.")]),
    "results.config": ("Règle la feuille de résultats : ce qui est mesuré, le sens du classement, le barème "
                       "(« le plus petit gagne », « barème 3 2 1 »).",
                       [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                        f("kind", STR, doc="time | count | distance | mark | weight"),
                        f("order", STR, doc="lower (le plus petit gagne) | higher (le plus grand gagne) | auto"),
                        f("scale", STR, doc="Points par place, ex. « 5,4,3,2,1 ».")]),
    "results.apply": ("Applique le barème (« applique le barème », « donne les points de la feuille ») : classe la "
                      "feuille de résultats et fixe le score de chaque candidat dans la manche (ex-aequo = même place).",
                      [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                       f("scale", STR, doc="Barème si LordFinn en dit un, ex. « 3,2,1 » ; absent = celui de la feuille.")]),
    "stopwatch.start": ("Lance le chrono d'un candidat (essai en solo : « top chrono pour Ensuri »).",
                        [f("player", PLAYER, True, PLAYER_DOC)]),
    "stopwatch.stop": ("Arrête le chrono d'un candidat ; son temps est noté sur la feuille de résultats.",
                       [f("player", PLAYER, True, PLAYER_DOC), f("record", BOOL, doc="false = ne pas noter le temps.")]),
    "stopwatch.reset": ("Remet à zéro le chrono d'un candidat (ou tous si aucun joueur).", [f("player", PLAYER, doc=PLAYER_DOC)]),
    "buzzer.reset": ("Remet les buzzers à zéro (« remets les buzzers »).", []),
    # TV direction (chantier E): no data change, nothing to undo
    "camera.cut": ("Passe à l'antenne sur une caméra du studio (« caméra 2 », « plan large », « plan sur le trône », "
                   "« le podium »).",
                   [f("camera", STR, True, "Numéro, nom exact (du contexte) ou rôle : large, trone, podium."),
                    f("transition", STR, doc="coupe (défaut), fondu ou enchaine.")]),
    "camera.follow": ("Plan qui suit un joueur (« suis Ensuri », « caméra sur Cycy »).",
                      [f("player", PLAYER, True, PLAYER_DOC), f("transition", STR, doc="coupe, fondu ou enchaine.")]),
    "camera.auto": ("Réalisation automatique (coupe sur qui marque, qui ouvre son enveloppe, le podium à la révélation).",
                    [f("enabled", BOOL, True, "true = activer.")]),
    "broadcast.lowerthird": ("Affiche un synthé (nom en bas de l'écran) sur la diffusion : un joueur, ou un texte libre.",
                             [f("player", PLAYER, doc=PLAYER_DOC), f("text", STR, doc="Texte libre (si pas de joueur)."),
                              f("subtitle", STR, doc="Deuxième ligne.")]),
    "broadcast.task": ("Montre la carte de la tâche en cours sur la diffusion.", []),
    "broadcast.scores": ("Bandeau des scores publiés sur la diffusion : show = le montrer maintenant, on = toujours, "
                         "off = jamais, auto = après chaque publication.", [f("mode", STR, doc="show (défaut), on, off, auto.")]),
    "broadcast.letterbox": ("Bandes cinéma sur la diffusion.", [f("enabled", BOOL, True, "true = afficher.")]),
    "show.intro": ("Lance le générique d'ouverture (plans du studio, titre, présentation des candidats).", []),
    "show.outro": ("Lance le générique de fin (crédits, classement publié).", []),
    "show.stop": ("Arrête le générique en cours.", []),
    # Tasks and envelopes (chantier D, docs/design/diffusion.md)
    "task.set": ("Écrit ou modifie la tâche d'une manche (lue sur la carte de l'enveloppe) : titre, texte dicté, règles, "
                 "durée affichée, couleur de cire. Seuls les champs donnés changent.",
                 [f("manche", MANCHE, True, MANCHE_DOC), f("title", STR, doc="Titre court de la tâche."),
                  f("text", STR, doc="Texte de la tâche, mis en forme : phrases complètes, ponctuation, paragraphes "
                                    "séparés par une ligne vide (\\n\\n). Reprendre fidèlement la dictée."),
                  f("append", BOOL, doc="true = ajoute text en nouveau paragraphe au texte existant (« ajoute à la tâche »)."),
                  f("rules", STR_LIST, doc="Règles en puces, liste COMPLÈTE (remplace les anciennes)."),
                  f("seconds", INT, doc="Durée affichée sur la carte en secondes (0 = chrono de la manche)."),
                  f("wax", STR, doc="Couleur de cire : rouge, or, noir, blanc, bleu, vert, violet, rose, orange, cyan, marron, gris."),
                  f("revealOnStart", BOOL, doc="false = seule l'ouverture d'une enveloppe révèle la tâche.")]),
    "task.clear": ("Efface la tâche d'une manche.", [f("manche", MANCHE, True, MANCHE_DOC)]),
    "task.reveal": ("Révèle la tâche à tout le monde (spectateurs, diffusion), annoncé à tous.",
                    [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours.")]),
    "task.hide": ("Rend une tâche de nouveau secrète.", [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours.")]),
    "task.show": ("Affiche la carte de la tâche sur tous les écrans quelques secondes (« montre la tâche »).",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                   f("seconds", INT, doc="Durée d'affichage, 12 par défaut (1 à 120).")]),
    "envelope.give": ("Donne des enveloppes scellées de la tâche d'une manche (« donne les enveloppes »).",
                      [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                       f("players", STR_LIST, doc="Pseudos EXACTS des destinataires ; absent = tous les candidats connectés."),
                       f("wax", STR, doc="Couleur de cire ; absent = celle de la tâche.")]),
    # Studio set (chantier F): spotlights of the studio, all at once
    "spotlight.follow": ("Braque tous les projecteurs du studio sur un joueur et les fait le suivre (« projecteurs sur Ensuri »).",
                         [f("player", PLAYER, True, PLAYER_DOC)]),
    "spotlight.stop": ("Les projecteurs arrêtent de suivre (gardent leur direction).", []),
    "spotlight.on": ("Allume tous les projecteurs du studio.", []),
    "spotlight.off": ("Éteint tous les projecteurs du studio.", []),
    "say": ("Répond à LordFinn sans rien modifier : question, doute réel, ordre impossible ou incompris. "
            "Texte court en français.", [f("text", STR, True, "Réponse courte.")]),
}

NO_SCOPE = {"say", "undo", "stopwatch.start", "stopwatch.stop", "stopwatch.reset", "buzzer.reset", "camera.cut", "camera.follow", "camera.auto", "broadcast.lowerthird", "broadcast.task",
            "broadcast.scores", "broadcast.letterbox", "show.intro", "show.outro", "show.stop"}
NO_SCOPE |= {"spotlight.follow", "spotlight.stop", "spotlight.on", "spotlight.off"}
SCOPE_FIELDS = [f("season", SCOPE, doc="Saison (id ou nom). Absent = saison active."),
                f("partie", SCOPE, doc="Partie (id ou nom). Absent = partie active.")]


def fields_of(action: str) -> list[Field]:
    _, fields = ACTIONS[action]
    return fields + ([] if action in NO_SCOPE else SCOPE_FIELDS)


def tool_name(action: str) -> str:
    return action.replace(".", "_")


TOOL_TO_ACTION = {tool_name(a): a for a in ACTIONS}


def _json_type(kind: str) -> dict:
    if kind == INT:
        return {"type": "integer"}
    if kind == BOOL:
        return {"type": "boolean"}
    if kind == STR_LIST:
        return {"type": "array", "items": {"type": "string"}}
    return {"type": "string"}


def claude_tools() -> list[dict]:
    """One strict tool per action, in a fixed order (the tool list is part of the cached prompt prefix)."""
    tools = []
    for action, (doc, _) in ACTIONS.items():
        props, required = {}, []
        for fl in fields_of(action):
            props[fl.name] = {**_json_type(fl.kind), **({"description": fl.doc} if fl.doc else {})}
            if fl.required:
                required.append(fl.name)
        tools.append({
            "name": tool_name(action),
            "description": doc,
            "input_schema": {"type": "object", "properties": props, "required": required, "additionalProperties": False},
            "strict": True,
        })
    return tools
