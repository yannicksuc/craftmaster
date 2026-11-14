"""
Écrit les séquences (timelines) de l'émission pour le plateau principal : les états de base de la lumière (à
capturer en ambiances) et les timelines du direct. Usage : python gen_sequences.py <dossier sequences>.

Le plateau (vu du public, qui regarde la scène vers l'ouest) :
  - scène à l'ouest : rideau « stage » (x 68), derrière lui la galerie des 5 tableaux (x 63.5) et les tables de prix ;
  - au milieu : les 5 chaises des candidats en arc, face au trône ;
  - à l'est : le trône du CraftMaster (86, -59, 0), et au fond les 3 socles des prix (x 95).
Projecteurs (numérotés de la gauche à la droite du public) :
  face1-4    portique au-dessus de l'avant-scène (x 74, en haut) : face2-3 = face du CraftMaster, face1-4 = contre-jour
             des candidats (ils tournent le dos à la scène)
  table1-2   plafond près du trône (x 89) : face des candidats (ils regardent le trône)
  contre1-2  mur du fond derrière le trône (x 93) : contre-jour du CraftMaster
  rampe1-4   rampe au sol de l'avant-scène : le rideau
  galerie1-2 au-dessus de la galerie (x 64) : les tableaux
  poursuite1-2 murs de côté (z ±13) : poursuites
Points : @point:trone, @point:table, @point:galerie, @point:rideau, @point:prix.
Principes (recherche scénographie TV) : peu d'états de base solides, effets posés par-dessus ; visages éclairés de
face + contre-jour pour détacher du fond ; blanc chaud pour la discussion, bleu froid pour la tension ; fondus 2-3 s
entre états de discussion, ~1 s vers la tension, coupure franche pour une révélation, 3-5 s pour revenir.
"""
import json, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "sequences"
os.makedirs(OUT, exist_ok=True)

ALL = ["face1", "face2", "face3", "face4", "table1", "table2", "contre1", "contre2",
       "rampe1", "rampe2", "rampe3", "rampe4", "galerie1", "galerie2", "poursuite1", "poursuite2"]


def key(t=0.0, fondu=0.0, **kw):
    k = {"t": t, "fondu": fondu}
    k.update(kw)
    return k


def light(cible, *cles):
    return {"type": "lumiere", "cible": cible, "cles": list(cles)}


def on(couleur, intensite, cone, vise, **kw):
    d = dict(allume=True, couleur=couleur, intensite=intensite, cone=cone, vise=vise, effet="aucun")
    d.update(kw)
    return d


OFF = dict(allume=False)


def state(name, description, spots, fade=0.0):
    """A base state: every spotlight set at t=0 (unnamed ones off)."""
    pistes = []
    for s in ALL:
        v = spots.get(s, OFF)
        pistes.append(light(s, key(0, fade, **v)))
    return name, {"duree": 1, "boucle": False, "description": description, "pistes": pistes}


def same(names, v):
    return {n: v for n in names}


FACES_TRONE = ["face2", "face3"]
FACES_COTE = ["face1", "face4"]
TABLE = ["table1", "table2"]
CONTRE = ["contre1", "contre2"]
RAMPE = ["rampe1", "rampe2", "rampe3", "rampe4"]
GALERIE = ["galerie1", "galerie2"]
POURSUITE = ["poursuite1", "poursuite2"]

STATES = [
    # Entrée du public / avant le générique : chaud et bas, le rideau en valeur, le trône vide en silhouette
    state("etat-salle", "SALLE : accueil, chaud et bas, rideau rougeoyant", {
        **same(FACES_TRONE, on("ambre", 30, 7, "@point:trone")),
        **same(TABLE, on("ambre", 25, 12, "@point:table")),
        **same(CONTRE, on("ambre", 40, 5, "@point:trone")),
        **same(RAMPE, on("rouge", 55, 14, "@point:rideau", effet="respiration", vitesse=0.4)),
    }),
    # L'état de travail : visages en blanc chaud, contre-jours, rideau doux
    state("etat-discussion", "DISCUSSION : l'état de travail, visages en blanc chaud, contre-jours, rideau doux", {
        **same(FACES_TRONE, on("chaud", 80, 6, "@point:trone")),
        **same(FACES_COTE, on("blanc", 45, 9, "@point:table")),
        **same(TABLE, on("chaud", 85, 10, "@point:table")),
        **same(CONTRE, on("ambre", 45, 5, "@point:trone")),
        **same(RAMPE, on("ambre", 45, 13, "@point:rideau")),
        **same(GALERIE, on("chaud", 55, 10, "@point:galerie")),
    }),
    # Tension (jugement serré, dernier point, avant une annonce) : bleu froid, salle basse, contre-jours forts
    state("etat-tension", "TENSION : bleu froid, salle basse, contre-jours forts", {
        **same(FACES_TRONE, on("bleu_clair", 40, 5, "@point:trone")),
        **same(TABLE, on("bleu_clair", 45, 7, "@point:table")),
        **same(CONTRE, on("bleu", 70, 4, "@point:trone")),
        **same(FACES_COTE, on("bleu", 60, 5, "@point:table")),
        **same(RAMPE, on("violet", 25, 12, "@point:rideau")),
    }),
    # Révélation : la galerie (le classement) en blanc franc, la salle s'efface
    state("etat-revelation", "RÉVÉLATION : la galerie en blanc franc, la salle s'efface", {
        **same(GALERIE, on("blanc", 100, 12, "@point:galerie")),
        **same(RAMPE, on("blanc", 70, 14, "@point:galerie")),
        **same(FACES_TRONE, on("chaud", 30, 6, "@point:trone")),
        **same(TABLE, on("chaud", 25, 10, "@point:table")),
        **same(CONTRE, on("bleu", 35, 4, "@point:trone")),
    }),
    # Vainqueur : de l'or, les poursuites sur le premier
    state("etat-vainqueur", "VAINQUEUR : or partout, poursuites sur le premier", {
        **same(GALERIE, on("or", 100, 12, "@point:galerie")),
        **same(RAMPE, on("or", 75, 14, "@point:galerie", effet="chenillard", vitesse=1)),
        **same(FACES_TRONE, on("or", 70, 7, "@point:trone")),
        **same(TABLE, on("chaud", 60, 10, "@point:table")),
        **same(CONTRE, on("or", 70, 5, "@point:trone")),
        **same(POURSUITE, on("or", 100, 4, "@point:table", suit="@rang:1", effet="respiration", vitesse=0.6)),
    }),
    # Épreuve en direct au studio : clair et neutre, tout est lisible, contre-jours renforcés
    state("etat-studio", "ÉPREUVE AU STUDIO : clair et neutre, tout est lisible", {
        **same(FACES_TRONE, on("blanc", 75, 9, "@point:trone")),
        **same(FACES_COTE, on("blanc", 80, 14, "@point:table")),
        **same(TABLE, on("blanc", 95, 15, "@point:table")),
        **same(CONTRE, on("blanc", 70, 8, "@point:table")),
        **same(RAMPE, on("blanc", 50, 14, "@point:rideau")),
        **same(GALERIE, on("blanc", 50, 10, "@point:galerie")),
    }),
    # Pendant une épreuve ailleurs : le plateau attend, en veille
    state("etat-attente", "ATTENTE : les candidats sont ailleurs, le plateau en veille", {
        **same(CONTRE, on("bleu", 30, 6, "@point:trone", effet="respiration", vitesse=0.3)),
        **same(RAMPE, on("violet", 30, 13, "@point:rideau", effet="respiration", vitesse=0.3)),
        **same(GALERIE, on("lavande", 30, 10, "@point:galerie")),
    }),
    # Pause : doux et coloré
    state("etat-pause", "PAUSE : doux et coloré, on revient vite", {
        **same(TABLE, on("lavande", 40, 12, "@point:table")),
        **same(FACES_TRONE, on("rose", 35, 8, "@point:trone")),
        **same(RAMPE, on("magenta", 40, 14, "@point:rideau", effet="chenillard", vitesse=0.5)),
        **same(GALERIE, on("lavande", 40, 10, "@point:galerie")),
    }),
]


def cmd(t, c):
    return key(t, 0, commande=c)


def cam(t, plan, transition=None):
    k = key(t, 0, plan=plan)
    if transition: k["transition"] = transition
    return k


TIMELINES = {
    # Le début de l'émission, avant / sous le générique : du noir à l'état de discussion, rideau qui s'ouvre
    "ouverture": {
        "duree": 16, "boucle": False,
        "description": "Ouverture : noir, le trône en silhouette, les candidats s'allument en vague, le rideau s'ouvre sur la galerie, puis DISCUSSION",
        "pistes": [
            light("tout", key(0, 0.5, allume=False)),
            light("contre", key(1, 1.5, **on("bleu", 70, 4, "@point:trone"))),
            light("rampe", key(2.5, 1, **on("ambre", 55, 13, "@point:rideau", effet="chenillard", vitesse=1.5))),
            light("table", key(4.5, 1, vague=1.2, **on("chaud", 85, 10, "@point:table"))),
            light("face", key(6.5, 1.5, **on("chaud", 70, 7, "@point:trone"))),
            {"type": "rideau", "cible": "stage", "cles": [key(0, 0, ouvert=False), key(8.5, 0, ouvert=True)]},
            light("galerie", key(9, 2, **on("chaud", 70, 10, "@point:galerie"))),
            {"type": "camera", "cible": "", "cles": [cam(0, "camera:studio:9", "fondu"), cam(4, "travelling:survol", "enchaine"),
                                                    cam(12.5, "camera:studio:1", "enchaine")]},
            {"type": "commande", "cles": [cmd(13, "ambiance discussion 3")]},
        ],
    },
    # Annoncer une épreuve (avant /cfma next) : tension courte, la poursuite sur le CraftMaster
    "annonce": {
        "duree": 8, "boucle": False,
        "description": "Annonce d'une épreuve : TENSION, poursuite sur le CraftMaster, plan du trône",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance tension 1")]},
            light("poursuite1", key(0.5, 0.8, **on("blanc", 100, 4, "@point:trone", suit="@maitre"))),
            {"type": "camera", "cible": "", "cles": [cam(0.3, "camera:studio:2", "coupe")]},
        ],
    },
    # Juger une épreuve : DISCUSSION, la poursuite douce sur le candidat du moment
    "jugement": {
        "duree": 5, "boucle": False,
        "description": "Jugement : DISCUSSION, poursuite douce sur le candidat du moment",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance discussion 2")]},
            light("poursuite2", key(0.5, 1, **on("chaud", 70, 5, "@point:table", suit="@moment", effet="respiration", vitesse=0.5))),
        ],
    },
    # Un point donné : une petite pulsation dorée sur les visages, puis retour
    "point": {
        "duree": 3, "boucle": False,
        "description": "Un point : pulsation dorée courte sur la table",
        "pistes": [
            light("table", key(0, 0.2, couleur="or", intensite=100, effet="pulse", vitesse=2),
                  key(1.6, 0.8, couleur="chaud", intensite=85, effet="aucun")),
        ],
    },
    # Révélation du classement (lancée toute seule au début de /craftMasterReveal) : ≈ 26 s
    "revelation": {
        "duree": 26, "boucle": False,
        "description": "Révélation : TENSION, rideau qui frissonne pendant le roulement, puis la galerie en blanc franc et une poursuite sur chaque nom",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance tension 1")]},
            light("rampe", key(0.5, 0.5, **on("bleu", 45, 13, "@point:rideau", effet="chenillard", vitesse=2)),
                  key(6.8, 0, allume=False)),
            light("contre", key(3, 0.3, effet="pulse", vitesse=1.5), key(6.8, 0, effet="aucun")),
            light("galerie", key(7, 0, **on("blanc", 100, 12, "@point:galerie"))),
            light("poursuite1", key(7.2, 0.3, **on("blanc", 100, 4, "@point:galerie", suit="@moment"))),
            {"type": "camera", "cible": "", "cles": [cam(0, "camera:studio:1", "fondu"), cam(3, "camera:studio:11", "coupe"),
                                                    cam(5, "camera:studio:10", "coupe"), cam(7, "camera:studio:13", "flash")]},
        ],
    },
    # Le vainqueur (lancé tout seul quand il est révélé)
    "podium": {
        "duree": 12, "boucle": False,
        "description": "Podium : coupure franche en or, poursuites sur le premier, travelling sur la galerie, puis VAINQUEUR",
        "pistes": [
            light("tout", key(0, 0, couleur="or", intensite=100)),
            light("face", key(0, 0, effet="strobe", vitesse=1.5), key(2, 0.5, effet="aucun")),
            light("poursuite", key(0.2, 0.3, **on("or", 100, 4, "@point:table", suit="@rang:1", effet="respiration", vitesse=0.6))),
            light("galerie", key(0, 0, **on("or", 100, 12, "@point:galerie"))),
            {"type": "camera", "cible": "", "cles": [cam(0, "camera:studio:8", "flash"), cam(3, "travelling:galerie", "enchaine")]},
            {"type": "commande", "cles": [cmd(2.5, "ambiance vainqueur 2")]},
        ],
    },
    # Une épreuve commence ailleurs : le plateau passe en veille
    "depart-epreuve": {
        "duree": 4, "boucle": False,
        "description": "Départ en épreuve : le plateau passe en ATTENTE",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance attente 4")]},
            light("poursuite", key(0, 1, allume=False)),
        ],
    },
    # Retour au plateau après une épreuve
    "retour-plateau": {
        "duree": 6, "boucle": False,
        "description": "Retour au plateau : DISCUSSION en 4 s, plan large en fondu, tour de table",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance discussion 4")]},
            light("poursuite", key(0, 1, allume=False)),
            {"type": "camera", "cible": "", "cles": [cam(0, "camera:studio:1", "fondu"), cam(2.5, "travelling:tour-de-table", "enchaine")]},
        ],
    },
    # Épreuve en direct sur le plateau
    "epreuve-studio": {
        "duree": 4, "boucle": False,
        "description": "Épreuve au studio : tout clair, plongée puis plan large",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance studio 2")]},
            light("poursuite", key(0, 1, allume=False)),
            {"type": "camera", "cible": "", "cles": [cam(0, "camera:studio:9", "enchaine"), cam(3, "camera:studio:1", "enchaine")]},
        ],
    },
    # Les prix : la lumière sur les socles du fond
    "prix": {
        "duree": 5, "boucle": False,
        "description": "Les prix : poursuites sur les socles du fond, plan des prix",
        "pistes": [
            light("poursuite", key(0, 1, **on("or", 90, 6, "@point:prix", effet="respiration", vitesse=0.5))),
            light("contre", key(0, 1, couleur="or", intensite=70)),
            {"type": "camera", "cible": "", "cles": [cam(0.5, "camera:studio:14", "enchaine")]},
        ],
    },
    # La fin : retour à la salle, le rideau se ferme, noir
    "fin": {
        "duree": 18, "boucle": False,
        "description": "Fin (sous le générique de fin, qui mène les caméras) : SALLE en 5 s, le rideau se ferme, noir",
        "pistes": [
            {"type": "commande", "cles": [cmd(0, "ambiance salle 5"), cmd(16, "noir 2")]},
            light("poursuite", key(0, 2, allume=False)),
            {"type": "rideau", "cible": "stage", "cles": [key(9, 0, ouvert=False)]},
        ],
    },
    # Un chenillard en boucle sur le rideau, à poser par-dessus n'importe quel état (attente, générique)
    "rideau-chenillard": {
        "duree": 8, "boucle": True,
        "description": "Couche : chenillard ambre en boucle sur le rideau",
        "pistes": [light("rampe", key(0, 1, **on("ambre", 55, 13, "@point:rideau", effet="chenillard", vitesse=1.2)))],
    },
}

for name, seq in STATES:
    json.dump(seq, open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
for name, seq in TIMELINES.items():
    json.dump(seq, open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(len(STATES), "états,", len(TIMELINES), "timelines")
