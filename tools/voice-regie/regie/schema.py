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
    # Studio set (chantier F): spotlights of the studio, all at once
    "spotlight.follow": ("Braque tous les projecteurs du studio sur un joueur et les fait le suivre (« projecteurs sur Ensuri »).",
                         [f("player", PLAYER, True, PLAYER_DOC)]),
    "spotlight.stop": ("Les projecteurs arrêtent de suivre (gardent leur direction).", []),
    "spotlight.on": ("Allume tous les projecteurs du studio.", []),
    "spotlight.off": ("Éteint tous les projecteurs du studio.", []),
    "say": ("Répond à LordFinn sans rien modifier : question, doute réel, ordre impossible ou incompris. "
            "Texte court en français.", [f("text", STR, True, "Réponse courte.")]),
}

NO_SCOPE = {"say", "undo", "spotlight.follow", "spotlight.stop", "spotlight.on", "spotlight.off"}
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
