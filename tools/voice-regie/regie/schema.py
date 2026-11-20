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
                       f("scene", STR), f("timer", INT, doc="Chrono en secondes."),
                       f("timerStart", STR, doc="Quand le chrono démarre : « lancement » (avec la manche), « lettre » (par défaut : quand un joueur ouvre la lettre de la tâche ; au lancement si la manche n'a pas de tâche écrite) ou « bouton » (joueurs figés jusqu'à ce qu'ils cliquent « Commencer l'épreuve » dans la lettre)."),
                       f("order", INT, doc="Nouvelle position (1 = première).")]),
    "manche.archive": ("Archive ou désarchive une manche.", [f("manche", MANCHE, True, MANCHE_DOC), f("archived", BOOL, True)]),
    "manche.delete": ("Supprime une manche (annulable).", [f("manche", MANCHE, True, MANCHE_DOC)]),
    "manche.start": ("Lance une manche : elle devient la manche en cours, décor posé, joueurs téléportés, titre, chrono.",
                     [f("manche", MANCHE, True, MANCHE_DOC)]),
    "manche.end": ("Termine la manche en cours.", []),
    "timer.stop": ("Arrête le chrono de la manche en cours.", []),
    "timer.go": ("Lance tout de suite le chrono d'une manche qui attend sa lettre ou son bouton, et libère les joueurs figés (« go », « lance le chrono »).", []),
    "replay.flag": ("Pose un drapeau sur la timeline de l'enregistrement en cours (« marque ce moment », « drapeau »), "
                    "pour le retrouver au replay. Pas pendant un replay.",
                    [f("text", STR, doc="Note courte facultative (« belle chute »).")]),
    "curtain.open": ("Ouvre un rideau (« ouvrez les rideaux »).", [f("name", STR, doc="Nom du rideau ; absent = rideau de scène.")]),
    "curtain.close": ("Ferme un rideau.", [f("name", STR, doc="Nom du rideau ; absent = rideau de scène.")]),
    "garage.open": ("Ouvre une porte de garage (« ouvre la porte du garage »).",
                    [f("name", STR, doc="Nom de la porte (du contexte) ; absent = la seule porte.")]),
    "garage.close": ("Ferme une porte de garage.", [f("name", STR, doc="Nom de la porte (du contexte) ; absent = la seule porte.")]),
    "spectators.set": ("Autorise ou interdit les spectateurs : les candidats qui ont fini et les joueurs qui ne jouent pas "
                       "peuvent regarder la caméra d'un candidat ou la retransmission (« interdis les spectateurs pour la pomme »). "
                       "Interdire pendant l'épreuve renvoie les spectateurs à leur place.",
                       [f("enabled", BOOL, True, "true = autorisés, false = interdits."),
                        f("manche", MANCHE, doc="Pour cette épreuve seulement (id de manche). Absent = partout.")]),
    "voice.mode": ("Le vocal de LordFinn, en modes exclusifs : off (chacun son vocal habituel), commentaire (avec les "
                   "spectateurs, ouvert ou fermé ; les candidats qui jouent ne l'entendent jamais), tous (annonce à tout le "
                   "monde, ou aux candidats ou aux spectateurs seulement), joueur (un candidat ou une équipe seul avec lui ; "
                   "celui qu'il regarde par défaut), suivant (le mode d'après). « Parle à Ensuri », « annonce à tout le monde », "
                   "« ouvre le commentaire », « coupe mon vocal ».",
                   [f("mode", STR, True, "off, commentaire, tous, joueur ou suivant."),
                    f("target", STR, doc="commentaire : ouvert, ferme ou basculer ; tous : tous, candidats ou spectateurs ; "
                                         "joueur : le pseudo, ou equipe:<nom> pour une équipe du tableau des scores.")]),
    "garage.toggle": ("Ouvre une porte de garage fermée, ferme une porte ouverte.",
                      [f("name", STR, doc="Nom de la porte (du contexte) ; absent = la seule porte.")]),
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
    "camera.cut": ("Passe à l'antenne sur une caméra (« caméra 2 », « plan large », « plan sur le trône », "
                   "« le podium », « caméra 2 du studio », « caméra 1 d'Ensuri »).",
                   [f("camera", STR, True, "Numéro, nom exact (du contexte) ou rôle : large, trone, podium — dans le lieu "
                                           "et la zone actifs (cameraFocus). Ailleurs : clé du contexte (studio:2, "
                                           "pont:ensuri:1) ou la phrase telle quelle (« caméra 2 du studio »)."),
                    f("transition", STR, doc="coupe (défaut), fondu, enchaine, miroir (kaléidoscope), flash (flash blanc) ou surimpression."),
                    f("effect", STR, doc="Effet d'image si demandé : noir_blanc, sepia, pixel, fisheye, vignette, masque_rond, citrouille, look_intro, macro, mise_au_point, mise_au_point_inverse, stop_motion, timelapse.")]),
    "camera.follow": ("Plan qui suit un joueur (« suis Ensuri », « caméra sur Cycy ») ; pendant une manche, les touches "
                      "passent aussi aux caméras de sa zone. Ou une règle, comme les projecteurs (« suis le premier du "
                      "classement », « suis qui est sur scène », « suis le joueur du moment ») : le joueur est repris tout seul.",
                      [f("player", PLAYER, doc=PLAYER_DOC + " (ou rule)"),
                       f("rule", STR, doc="Au lieu d'un joueur : moment, candidat, scene, antenne, maitre, premier (ou rang:2…), dernier, "
                                          "hasard, public, proche, zone:<nom>."), f("transition", STR, doc="coupe, fondu, enchaine, miroir (kaléidoscope), flash (flash blanc) ou surimpression.")]),
    # TV direction, lieux / travellings / intros (chantier E): no data change except intro.*
    "camera.path": ("Lance un travelling enregistré à l'antenne (« lance le travelling du pont »).",
                    [f("path", STR, True, "Nom du trajet (contexte : paths)."), f("transition", STR, doc="coupe, fondu, enchaine, miroir (kaléidoscope), flash (flash blanc) ou surimpression."),
                     f("effect", STR, doc="Effet d'image si demandé.")]),
    "camera.focus": ("Change les caméras que visent « caméra 2 » et les touches 1–9 : une zone (« zone d'Ensuri », "
                     "« caméras de l'équipe bleue »), un lieu (« caméras du studio », « caméras du pont ») ou auto "
                     "(suivre l'émission).",
                     [f("lieu", STR, doc="studio, id de manche, ou auto."), f("zone", STR, doc="Nom de zone (équipe…)."),
                      f("player", PLAYER, doc="Candidat dont on veut la zone. " + PLAYER_DOC),
                      f("cut", BOOL, doc="true = montre aussi le plan large de cette zone (« zone d'Ensuri »).")]),
    "camera.lieu": ("Range une caméra dans un lieu (« la caméra 3 est sur le pont », « la 2 est dans la salle d'Ensuri »).",
                    [f("camera", STR, True, "La caméra (numéro dans le lieu actif, ou clé)."),
                     f("lieu", STR, True, "studio ou id de manche."), f("zone", STR, doc="Zone (nom ou candidat), si dite.")]),
    "camera.mosaic": ("Écran partagé : les zones de la manche côte à côte (« écran partagé », « mosaïque »).",
                      [f("enabled", BOOL, doc="false = retirer l'écran partagé."), f("lieu", STR, doc="studio ou id de manche.")]),
    "camera.split": ("Écran partagé de caméras CHOISIES (2 à 6), à partir de ce qui est à l'antenne (onAir) : « mets la 3 "
                     "et la 5 côte à côte », « ajoute la caméra galerie en petit », « retire la 3 », « la 2 en principal », "
                     "« coupe en deux », « repasse en un seul plan ».",
                     [f("cameras", STR_LIST, doc="Toutes les caméras de l'écran partagé, la principale d'abord (numéro, nom ou clé). "
                                                 "Absent = garder celles à l'antenne."),
                      f("add", STR, doc="Une caméra à ajouter à celles à l'antenne."),
                      f("remove", STR, doc="Une caméra à retirer de l'écran partagé."),
                      f("main", STR, doc="La caméra principale (mise en premier ; ajoutée si absente)."),
                      f("layout", STR, doc="cadres (plans encadrés sur le fond), bord (image coupée en parts, bord à bord) ou "
                                           "principal (la principale en grand, les autres en petit dessous). Vaut aussi pour les suivants."),
                      f("enabled", BOOL, doc="false = repasser en un seul plan (la principale)."),
                      f("transition", STR, doc="coupe, fondu, enchaine, miroir (kaléidoscope), flash (flash blanc) ou surimpression.")]),
    "camera.pov": ("Montre la vue d'un joueur, à travers ses yeux (« vue d'Ensuri »).", [f("player", PLAYER, True, PLAYER_DOC)]),
    # Automatic framing: the director picks the shot itself, under LordFinn's wishes
    "camera.goto": ("On va voir un joueur : la réalisation choisit elle-même comment (une caméra posée qui le voit bien, "
                    "sinon le plan qui le suit) — « on va voir Ensuri », « et maintenant allons voir comment ça se passe "
                    "chez Cycy ». Ensuite la réalisation automatique continue.",
                    [f("player", PLAYER, True, PLAYER_DOC)]),
    "camera.stay": ("La réalisation automatique reste sur un joueur (« reste sur Ensuri », « on ne lâche plus Juste ») ; "
                    "enabled false = ne plus rester sur lui (« tu peux lâcher Ensuri »).",
                    [f("player", PLAYER, doc=PLAYER_DOC), f("enabled", BOOL, doc="false = arrêter.")]),
    "camera.framing": ("Cadrage automatique plus large ou plus serré (« plus large », « plus serré », « cadrage normal »).",
                       [f("size", STR, True, "wider (plus large), tighter (plus serré) ou auto (normal).")]),
    "camera.avoid": ("La réalisation automatique n'utilise plus une caméra (« évite la caméra 3 ») ; enabled false = elle "
                     "peut la reprendre (« tu peux reprendre la caméra 3 »).",
                     [f("camera", STR, True, "La caméra (numéro dans le lieu actif, nom ou clé)."),
                      f("enabled", BOOL, doc="false = elle peut de nouveau la prendre.")]),
    "intro.play": ("Lance l'intro animée d'une manche (« lance l'intro du pont »).",
                   [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                    f("from", INT, doc="Jouer à partir de ce plan (1 = tout, avec la musique ; plus loin = essai sans musique).")]),
    "intro.set": ("Réglages de l'intro d'une manche : jouée seule au lancement, musique, titre automatique, plans calés "
                  "sur la musique, lancement enchaîné (noir, TP, titre, révélation).",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                   f("auto", BOOL, doc="true = jouée au lancement de la manche."),
                   f("music", STR, doc="maladresse (la préférée, défaut conseillé), base, reflexion, urgence, victoire, suspense, univers.futuriste, univers.grec, univers.japonais (zen), univers.steampunk, univers.medieval, univers.western (le thème CraftMaster, 13-19 s, finit par l'« appel » qui ouvre l'épreuve), intro (intro d'épreuve v2, 10 s), theme (générique), aucune, ou un id de son. Une musique du thème met aussi les coupes sur son tempo."),
                   f("titleCard", BOOL, doc="Titre automatique sur le dernier plan."),
                   f("bpm", STR, doc="Couper sur la musique : tempo en BPM, « theme », « intro » (120) ou « off »."),
                   f("beats", INT, doc="Durée d'un plan en temps de musique (absent = environ 3 s)."),
                   f("chained", BOOL, doc="true = la manche attend la fin de l'intro puis noir, TP, titre et révélation."),
                   f("look", STR, doc="Effet sur tous les plans (look_intro = look vieux reportage) ; aucun pour l'enlever."),
                   f("typedTask", BOOL, doc="true = la consigne est tapée en bas de l'image à la fin de l'intro.")]),
    "intro.auto": ("Construit l'intro d'une manche toute seule à partir de ses caméras, sur la musique d'intro (« fais l'intro "
                   "du pont toute seule »). Remplace ses plans. Les coupes tombent sur les mesures de la musique, le plan "
                   "« lieu de la tâche » sur son « appel » final.",
                   [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                    f("music", STR, doc="Musique : maladresse (la préférée, défaut conseillé), base, reflexion, urgence, victoire, suspense, univers.futuriste, univers.grec, univers.japonais (zen), univers.steampunk, univers.medieval, univers.western (le thème CraftMaster, 13-19 s, finit par l'« appel » qui ouvre l'épreuve), intro. Absent = celle de l'intro si c'est une du thème, sinon maladresse (le défaut).")]),
    "intro.fit": ("Cale les plans de l'intro sur sa musique du thème : le dernier plan sur l'« appel » final, les autres "
                  "sur ses temps, en gardant leurs proportions (« cale l'intro du pont sur la musique »).",
                  [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours.")]),
    "intro.shot.add": ("Ajoute un plan à l'intro d'une manche : une caméra, un trajet ou un joueur suivi.",
                       [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                        f("camera", STR, doc="Caméra (relative au lieu de la manche : « 2 », « large », « ensuri:1 »)."),
                        f("path", STR, doc="Nom du trajet."), f("follow", PLAYER, doc="Joueur à suivre. " + PLAYER_DOC),
                        f("task", BOOL, doc="true = le plan « lieu de la tâche » (point d'arrivée et enveloppe posée)."),
                        f("seconds", INT, doc="Durée en secondes (absent = 4 s, ou la durée du trajet)."),
                        f("transition", STR, doc="coupe, fondu, enchaine, miroir (kaléidoscope), flash (flash blanc) ou surimpression ; défaut enchaine."),
                        f("effect", STR, doc="Effet d'image."), f("card", STR, doc="titre ou synthe."),
                        f("text", STR, doc="Texte de la carte (absent = nom de la manche).")]),
    "intro.shot.move": ("Déplace un plan de l'intro d'une manche (« mets le plan 3 en premier »).",
                        [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                         f("index", INT, True, "Numéro du plan à déplacer (1 = premier)."),
                         f("to", INT, True, "Sa nouvelle place (1 = premier).")]),
    "intro.shot.remove": ("Retire un plan de l'intro d'une manche.",
                          [f("manche", MANCHE, doc=MANCHE_DOC + " Absent = manche en cours."),
                           f("index", INT, True, "Numéro du plan (1 = premier).")]),
    "camera.auto": ("Réalisation automatique (coupe sur qui marque, qui ouvre son enveloppe, le podium à la révélation). "
                    "« Repasse en auto » = enabled true : reprend aussi après « reste sur » et remet le cadrage normal.",
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
    "task.reveal_more": ("Révèle la partie cachée suivante ([secret]…[/secret]) du texte de la tâche (« révèle la suite »).",
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
    "spotlight.set": ("Règle des projecteurs comme sur un pupitre lumière : allumer / éteindre, couleur, intensité, angle du "
                      "cône, suivi d'un joueur ou suivi automatique, avec un fondu (« projos de face en rouge en 3 secondes », "
                      "« baisse la face à 40 % », « ouvre le cône », « la poursuite sur qui est sur scène »).",
                      [f("target", STR, doc="tout (défaut), le nom d'un projecteur (voir « Projecteurs nommés »), ou un groupe "
                                            "« face* » (tous ceux dont le nom commence par face)."),
                       f("on", BOOL, doc="true = allumer, false = éteindre (en fondu si fade)."),
                       f("color", STR, doc="blanc, chaud, froid, ambre, or, rouge, orange, jaune, vert, cyan, bleu, bleu_clair, "
                                           "violet, rose, magenta, lavande, turquoise, ou #RRVVBB."),
                       f("intensity", INT, doc="0 à 100 (%)."),
                       f("cone", STR, doc="Demi-angle du cône en degrés, 1 (serré) à 30 (large) ; défaut des projecteurs 3."),
                       f("follow", STR, doc="Pseudo EXACT d'un joueur, ou suivi auto : @scene (qui est sur scène), @candidat "
                                            "(candidat le plus proche), @proche (joueur le plus proche), @moment (le joueur du "
                                            "moment : enveloppe ouverte, buzzer…), @antenne (le joueur que montre la réalisation), @zone:<nom> ; chaîne vide = arrêter de suivre."),
                       f("fade", STR, doc="Durée du changement en secondes (0 à 60), ex. « 3 ».")]),
    # Stage looks and cue lists (docs/wiki/Scenographie.md)
    "look.play": ("Lance une ambiance enregistrée (projecteurs, rideaux, tableaux, écrans tels qu'enregistrés), en fondu "
                  "(« ambiance plateau », « passe en ambiance révélation en 5 secondes »).",
                  [f("name", STR, True, "Nom EXACT d'une ambiance (voir « Ambiances »)."),
                   f("fade", STR, doc="Durée du fondu en secondes ; absent = celle de l'ambiance.")]),
    "look.save": ("Enregistre la scène actuelle comme ambiance (« enregistre l'ambiance plateau »).",
                  [f("name", STR, True, "Nom de l'ambiance."),
                   f("parts", STR_LIST, doc="lumieres, rideaux, tableaux, ecrans ; absent = tout."),
                   f("fade", STR, doc="Fondu par défaut de l'ambiance, en secondes.")]),
    "cue.play": ("Lance une conduite (séquence enregistrée d'ambiances, d'attentes et de commandes) (« lance la conduite "
                 "ouverture », « top générique »).",
                 [f("name", STR, True, "Nom EXACT d'une conduite (voir « Conduites ») ou d'une ambiance.")]),
    "cue.stop": ("Arrête la conduite en cours (« stop la conduite »).", []),
    "stage.blackout": ("Noir : éteint tous les projecteurs en fondu (« noir », « noir en 3 secondes »).",
                       [f("fade", STR, doc="Durée en secondes ; défaut 1.")]),
    # Screen effects (docs/wiki/Effets.md): post-processing of the broadcast picture or of a player's view
    "fx.set": ("Met un effet d'écran sur l'image de la diffusion (par défaut) ou sur la vue d'un joueur : noir_blanc, "
               "sepia, pixel, fisheye, vignette, masque_rond, citrouille, flou, vhs, vieux_film, infrarouge, reve, ondule, "
               "jumelles, bd, negatif, look_intro (look vieux reportage de l'intro), macro (fond flou), mise_au_point "
               "(bascule de mise au point du fond vers le sujet), mise_au_point_inverse (du sujet vers le fond), stop_motion (image saccadée), timelapse (ciel accéléré) ; combinables avec + (« fisheye+masque_rond+sepia ») ; combinaisons prêtes : judas "
               "(fisheye+masque_rond), camera_cachee, souvenir, film_muet, cauchemar. Remplace l'effet précédent de la cible, "
               "sauf avec un + ou - devant (« +sepia » ajoute, « -flou » retire un seul effet).",
               [f("target", STR, doc="« diffusion » (l'image du stream, défaut), « tous » (tous les joueurs), « candidats », "
                                     "ou le pseudo EXACT d'un joueur."),
                f("effect", STR, True, "Effet(s) joints par + , ids ci-dessus."),
                f("seconds", INT, doc="Durée en secondes ; absent = jusqu'à nouvel ordre.")]),
    "fx.clear": ("Enlève les effets d'écran (« enlève les effets »).",
                 [f("target", STR, doc="Absent = tout (diffusion et joueurs) ; sinon diffusion, tous, candidats ou un pseudo EXACT.")]),
    # Replays (docs/wiki/Replays.md): only while a replay plays (context "Replay en cours"), run on LordFinn's game
    "replay.control": ("Pilote le replay en cours (lecture d'un enregistrement) : lecture, pause, avancer, reculer, "
                       "aller à un moment, vitesse, repère suivant / précédent, filtrer les repères (par type, candidat "
                       "ou zone), autre fichier (la zone ou l'écran d'un candidat, au même instant), qui réalise, fermer.",
                       [f("do", STR, True, "play | pause | toggle | back | forward | goto | start | speed | faster | slower | "
                                           "next_marker | prev_marker | filter | clear_filter | view | mode | close."),
                        f("kind", STR, doc="filter : morts | résultats | chronos | cloches | drapeaux | drapeaux des joueurs | "
                                           "points | manches | sorties de zone | projecteurs | tout."),
                        f("player", STR, doc="filter : les repères de ce candidat (pseudo EXACT), « tout » pour tous."),
                        f("zone", STR, doc="filter : les repères de cette zone (nom de la vue), « toutes » pour toutes."),
                        f("seconds", INT, doc="back / forward : combien de secondes (défaut 10) ; goto : le moment, en secondes depuis le début."),
                        f("speed", STR, doc="speed : la vitesse, ex. « 2 », « 0.5 »."),
                        f("view", STR, doc="view : « zone » ou le pseudo EXACT du candidat dont on veut l'écran."),
                        f("mode", STR, doc="mode : direct (les plans du direct), auto, manuel ou libre."),
                        f("camera", STR, doc="mode libre : la caméra (numéro ou nom) d'où part la caméra libre ; absent = l'image actuelle.")]),
    # Studio screens and in-game clips (media/, docs/wiki/Clips.md)
    "tele.show": ("Ce qu'affiche une télé nommée du studio : un clip enregistré en jeu (clip, « dernier » par défaut), "
                  "un lien (clip Twitch, vidéo), le direct Twitch d'un candidat connecté (live), une image, le mur de chat "
                  "(chat : les derniers messages du chat Twitch, du chat du jeu, ou des deux), ou la mire (off). "
                  "Un seul de clip / link / live / image / chat / off.",
                  [f("tele", STR, True, "Nom de la télé (« gauche », « plateau »… ; « tout » = toutes les télés nommées)."),
                   f("clip", STR, doc="Clip du serveur : « dernier », « avant-dernier », ou son numéro (1 = le plus ancien)."),
                   f("link", STR, doc="Adresse web complète (https://clips.twitch.tv/…, vidéo…)."),
                   f("live", STR, doc="Pseudo du candidat dont on montre le direct Twitch (sa chaîne liée), ou un nom de chaîne."),
                   f("image", STR, doc="Adresse web d'une image."),
                   f("chat", STR, doc="Mur de chat : « twitch » (chat du direct), « jeu » (chat des joueurs) ou « tout » (les deux)."),
                   f("off", BOOL, doc="true = remettre la mire.")]),
    "clip.start": ("Démarre l'enregistrement d'un clip de la vue actuelle du jeu de LordFinn (« lance un clip », « clip ! »).", []),
    "clip.stop": ("Arrête le clip en cours ; il est envoyé au serveur et devient le « dernier » clip (« coupe le clip »).", []),
    "agent.request": ("Confie à l'agent de régie une demande qui CHANGE la préparation de l'émission (pas ce qui passe "
                      "à l'antenne maintenant) : modifier / créer une ambiance (couleurs, intensité des projecteurs "
                      "enregistrés), une timeline (séquence : ajouter un plan, un effet, décaler un moment), une conduite, "
                      "une caméra (la placer, la renommer, son cadrage), un travelling, un lancement automatique ; ou "
                      "toute demande de la régie en plusieurs étapes que les autres outils ne couvrent pas. L'agent agit "
                      "et répond dans le chat.",
                      [f("demande", STR, True, "La demande, reformulée clairement en français (tout ce que LordFinn a demandé).")]),
    "say": ("Répond à LordFinn sans rien modifier : question, doute réel, ordre impossible ou incompris. "
            "Texte court en français.", [f("text", STR, True, "Réponse courte.")]),
}

NO_SCOPE = {"say", "agent.request", "undo", "stopwatch.start", "stopwatch.stop", "stopwatch.reset", "buzzer.reset", "camera.cut", "camera.follow", "camera.auto", "broadcast.lowerthird", "broadcast.task",
            "broadcast.scores", "broadcast.letterbox", "show.intro", "show.outro", "show.stop"}
NO_SCOPE |= {"spotlight.follow", "spotlight.stop", "spotlight.on", "spotlight.off", "spotlight.set",
             "look.play", "look.save", "cue.play", "cue.stop", "stage.blackout"}
NO_SCOPE |= {"camera.path", "camera.focus", "camera.lieu", "camera.mosaic", "camera.pov", "camera.split"}
NO_SCOPE |= {"camera.goto", "camera.stay", "camera.framing", "camera.avoid"}
NO_SCOPE |= {"fx.set", "fx.clear"}
NO_SCOPE |= {"replay.control", "replay.flag", "tele.show", "clip.start", "clip.stop"}
NO_SCOPE |= {"spectators.set", "voice.mode"}
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
