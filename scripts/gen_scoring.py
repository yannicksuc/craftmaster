"""Texts of the referee's score entry screen (key U) and the player frame. Called by gen_assets.py."""


def generate(m):
    texts = {
        "key.craftmaster.scores": ("Score entry (referee)", "Saisie des points (arbitre)"),
        "screen.craftmaster.scores.title": ("Score entry", "Saisie des points"),
        "screen.craftmaster.scores.season_prev": ("Previous season", "Saison précédente"),
        "screen.craftmaster.scores.season_next": ("Next season", "Saison suivante"),
        "screen.craftmaster.scores.tab.board": ("All rounds", "Toutes les épreuves"),
        "screen.craftmaster.scores.tab.round": ("Round", "Épreuve"),
        "screen.craftmaster.scores.round_prev": ("Previous round", "Épreuve précédente"),
        "screen.craftmaster.scores.round_next": ("Next round", "Épreuve suivante"),
        "screen.craftmaster.scores.round": ("Round %s — %s", "Épreuve %s — %s"),
        "screen.craftmaster.scores.current": ("  (playing)", "  (en cours)"),
        "screen.craftmaster.scores.plus.tip": ("+1 for this player only", "+1 pour ce joueur seulement"),
        "screen.craftmaster.scores.minus.tip": ("-1 for this player only", "-1 pour ce joueur seulement"),
        "screen.craftmaster.scores.update": ("Update scores", "Mettre à jour"),
        "screen.craftmaster.scores.update.tip": ("Sends every changed score at once (one undo step)",
                                                 "Envoie tous les scores modifiés d'un coup (une seule annulation)"),
        "screen.craftmaster.scores.undo": ("Undo", "Annuler"),
        "screen.craftmaster.scores.undo.tip": ("Undoes the last change (scores or anything else)",
                                               "Annule la dernière modification (scores ou autre)"),
        "screen.craftmaster.scores.redo": ("Redo", "Refaire"),
        "screen.craftmaster.scores.redo.tip": ("Redoes what was just undone", "Refait ce qui vient d'être annulé"),
        "screen.craftmaster.scores.pending": ("%s change(s) not sent", "%s modification(s) non envoyée(s)"),
        "screen.craftmaster.scores.too_many": ("8 players max: %s not shown", "8 joueurs max : %s non affiché(s)"),
        "screen.craftmaster.scores.no_players": ("No players in this season yet", "Pas encore de joueurs dans cette saison"),
        "screen.craftmaster.scores.no_rounds": ("No rounds in the active game", "Aucune épreuve dans la partie active"),
        "screen.craftmaster.scores.hint": ("Click a face, then another: swap · Shift-click: to the top · right click: put back",
                                           "Clic sur une tête puis une autre : échange · Maj-clic : en tête · clic droit : reposer"),
        "screen.craftmaster.scores.scale_hint": ("No points yet: the places give the scale's points",
                                                 "Pas encore de points : les places donnent ceux du barème"),
        "screen.craftmaster.scores.board_hint": ("Left click +1 · right click -1 · a round's number opens it",
                                                 "Clic gauche +1 · clic droit -1 · le numéro d'une épreuve l'ouvre"),
        "screen.craftmaster.scores.col.round": ("Round", "Épreuve"),
        "screen.craftmaster.scores.open_round": ("Open this round", "Ouvrir cette épreuve"),
    }
    for k, (e, f) in texts.items():
        m.en[k] = e
        m.fr[k] = f
