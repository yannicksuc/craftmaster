# Scénographie : projecteurs, ambiances et conduites

Une régie lumière comme au théâtre : chaque projecteur se règle (couleur, intensité, angle du cône, direction ou
poursuite), on **enregistre des ambiances** (l'état de toute la scène), on les **enchaîne dans des conduites**
(séquences avec attentes, noirs, commandes), et le show les lance **tout seul** au bon moment.
Tout passe aussi par la régie vocale, le Stream Deck et l'onglet **Scène** de la régie des modos (touche `;`).

## 1. Les projecteurs
Pose les projecteurs (onglet CraftMaster), au sol ou suspendus. Puis **nomme-les** en les regardant :
`/cfsp nom face1`, `/cfsp nom face2`, `/cfsp nom contre`, `/cfsp nom poursuite`…
Un nom sans son numéro forme un **groupe** : `face` = face1, face2…

| Commande | Effet |
|---|---|
| `/cfsp <ordre>` | le projecteur que tu regardes |
| `/cfsp tout <ordre>` | tous les projecteurs chargés |
| `/cfsp groupe <nom> <ordre>` | ceux qui portent ce nom, ou dont le nom commence par lui |

**Le menu d'un projecteur** : Maj + clic droit dessus (organisateurs), ou `/cfsp menu` en le regardant. Il s'ouvre sur
le côté de l'écran, le faisceau reste visible : nom, allumé, couleur, intensité, cône, direction (◀ ▶ tourner de 15°,
▲ ▼ incliner de 10°, ⇄ dans l'autre sens, « Où je regarde » : pointe le bloc sous ton viseur, « Vers moi »), qui il
suit, effet et vitesse. Molette sur une valeur pour l'ajuster ; chaque changement se voit tout de suite.

Ordres (le dernier nombre, facultatif, est la **durée du fondu** en secondes) :

| Ordre | Effet |
|---|---|
| `allume [s]` · `eteint [s]` | allume / éteint, en fondu |
| `couleur <couleur> [s]` | blanc, chaud, froid, ambre, or, rouge, orange, jaune, vert, cyan, bleu, bleu_clair, violet, rose, magenta, lavande, turquoise, ou `#RRVVBB` |
| `intensite <0-100> [s]` | la force du faisceau et de la lumière au sol |
| `cone <1-30> [s]` | demi-angle du cône en degrés : 3 = faisceau serré (défaut), 15 = large, 30 = douche |
| `ici [s]` | pointe vers toi (place-toi là où la lumière doit tomber) |
| `suivre <joueur ou règle>` · `stop` | poursuite (voir plus bas) / arrête de suivre en gardant la direction |
| `liste` | les projecteurs, leur nom, leur état, qui ils suivent |

Exemple : `/cfsp groupe face couleur ambre 3` puis `/cfsp groupe face intensite 40 3` : la face passe en ambre et
baisse à 40 % en 3 secondes.

### Poursuite automatique
Au lieu d'un pseudo, un projecteur peut suivre une **règle** ; le serveur choisit le joueur, et la lumière reste sur
lui tant qu'il correspond (elle ne saute pas d'un joueur à l'autre) :

| Règle | Suit |
|---|---|
| `@scene` | qui est sur scène : un joueur dans la zone d'épreuve nommée `scene` (`/craftMasterZone create scene` avec le mètre ruban) |
| `@zone:<nom>` | un joueur dans cette zone |
| `@candidat` | le candidat le plus proche (saison active) |
| `@proche` | le joueur le plus proche (pas les spectateurs) |
| `@moment` | le joueur du moment : celui qui ouvre son enveloppe, buzze, est révélé… pendant une minute |

## 2. Les ambiances
Une ambiance est une **mémoire** de la scène : projecteurs (allumés, couleur, intensité, cône, direction ou
poursuite), rideaux nommés (ouverts / fermés), tableaux suspendus (longueur de ficelle) et télés nommées (ce qu'elles
montrent). Règle la scène comme tu la veux, puis :

| Commande | Effet |
|---|---|
| `/ambiance enregistrer <nom> [parties] [fondu]` | enregistre la scène actuelle ; parties : `tout` (défaut), ou `lumieres`, `rideaux`, `tableaux`, `ecrans` séparées par des virgules (`lumieres,rideaux`) |
| `/ambiance lancer <nom> [fondu]` | rejoue l'ambiance : les projecteurs tournent et changent en fondu, les tableaux montent ou descendent, les rideaux s'ouvrent ou se ferment, les télés changent |
| `/ambiance` | la liste, cliquable (clic : la lancer ; « mettre à jour » : la remplacer par la scène actuelle) |
| `/ambiance noir [fondu]` | tous les projecteurs s'éteignent |
| `/ambiance supprimer <nom>` | |

Une ambiance « lumières seulement » ne touche ni aux rideaux ni aux tableaux. Un nom avec des espaces se met entre
guillemets : `/ambiance lancer "plateau ouvert"`.

## 3. Les conduites
Une conduite enchaîne des lignes, jouées l'une après l'autre :

| Ligne | Effet |
|---|---|
| `attendre <s>` | attend |
| `ambiance <nom> [s]` | lance une ambiance (avec ce fondu) |
| `noir [s]` | tous les projecteurs s'éteignent |
| `conduite <nom>` | continue avec une autre conduite |
| n'importe quelle commande, sans `/` | `cfsp groupe face couleur rouge 2`, `cfpt hoist 1 6 4`, `cmtele tout clip dernier`, `tv intro`, `cfma next`… |

| Commande | Effet |
|---|---|
| `/conduite ajouter <nom> <ligne>` | ajoute une ligne (crée la conduite au besoin) |
| `/conduite voir <nom>` | ses lignes, cliquables : ✕ pour retirer, clic sur une ligne pour la recopier et l'insérer modifiée |
| `/conduite inserer <nom> <n> <ligne>` · `retirer <nom> <n>` | |
| `/conduite lancer <nom>` · `stop` · `statut` | une seule conduite joue à la fois |
| `/conduite` | la liste, cliquable |

### Lancement automatique
| Commande | Effet |
|---|---|
| `/conduite auto <moment> <conduite ou ambiance>` | moments : `lancement` (d'une manche), `fin` (de la manche), `chrono` (le chrono arrive à zéro), `revelation` (début de la révélation), `podium` (le vainqueur est révélé) ; `aucune` pour retirer |
| `/conduite manche <manche> <conduite ou ambiance>` | cette manche joue la sienne à son lancement, à la place de celle de `lancement` |
| `/conduite auto` | ce qui se lance tout seul, cliquable |

Exemple, une ouverture de manche :
```
/conduite ajouter ouverture noir 1
/conduite ajouter ouverture attendre 2
/conduite ajouter ouverture ambiance plateau 3
/conduite ajouter ouverture cfsp groupe poursuite suivre @scene
/conduite auto lancement ouverture
```

## 4. Les séquences : la timeline de scène
Une séquence est une **timeline** : des **pistes** (une par chose à piloter) et des **keypoints** dans le temps. Un
keypoint ne dit que ce qui change, avec un **fondu** (le temps que prend le changement). On voit la scène en direct
pendant qu'on règle.

### L'éditeur en jeu
`/sequence editer <nom>` (la crée au besoin) ouvre l'éditeur **en bas de l'écran** : la scène reste visible au-dessus.

- **À gauche, les pistes** : *Lumière* (un groupe de projecteurs : `tout`, `face`, `contre gauche`…), *Rideau* (un
  rideau nommé ou `tout`), *Tableaux* (la galerie d'une saison, ou `tout`), *Écran* (une télé nommée), *Commande*.
  Clic ou molette sur la cible : passer à la suivante ; clic droit : la taper. **M** : muette, **✕** : retirer.
  Les boutons du bas ajoutent une piste.
- **Au milieu, la timeline** : la règle des secondes, la **tête de lecture** (cliquer ou glisser sur la règle : la
  scène se met dans l'état de cet instant), les keypoints (losanges à la couleur de la lumière), la barre de fondu
  derrière chacun, l'éventail d'une vague ou d'une cascade.
- **À droite, les réglages** du keypoint choisi : temps, fondu, et selon la piste allumé, couleur, intensité, cône,
  suit, vise, effet, vitesse, vague / ouvert / ficelle, cascade / ce que montre l'écran / la commande. Molette ou
  glisser sur une valeur pour la changer (Maj : plus vite), clic droit : « — » = inchangé.

| Geste | Effet |
|---|---|
| Double-clic sur une ligne | un keypoint là, qui reprend l'état de la piste à cet instant (on ne change que ce qui doit changer) |
| Clic / Maj+clic | choisir un keypoint / en ajouter à la sélection |
| Glisser un keypoint | le déplacer (au quart de seconde ; Ctrl : libre) |
| Espace | lire depuis la tête de lecture / arrêter |
| Suppr, clic droit sur un keypoint | le retirer |
| Ctrl+C / Ctrl+V / Ctrl+D / Ctrl+A | copier / coller à la tête de lecture / dupliquer juste après / toute la piste |
| Ctrl+Z / Ctrl+Y | défaire / refaire |
| ← → | déplacer la sélection (ou la tête de lecture) d'un quart de seconde, Maj : une seconde |
| ↑ ↓ | piste précédente / suivante |
| Molette / Ctrl+molette | faire défiler / zoomer |

Boutons : **● Capturer** (la scène telle que tu l'as réglée à la main, `/cfsp…`, dans le keypoint choisi ou un
nouveau), **Miroir** (la piste pour l'autre côté : `gauche ↔ droite`, `cour ↔ jardin`, `g ↔ d` dans le nom ; sinon
ses angles en miroir), **Inverser** (la sélection, ou toute la séquence, jouée à l'envers), **Dupliquer**,
**Boucle**, la durée (molette ou clic), ◀ ▶ pour passer d'une séquence à l'autre, **+** pour en créer une, le nom
(clic) pour la renommer. Tout s'enregistre tout seul.

### Les cibles de poursuite (« suit »)
| Cible | Suit |
|---|---|
| `@maitre` | le CraftMaster (`/plateau maitre <pseudo>`, LordFinn par défaut) |
| `@rang:1`, `@rang:2`… | le joueur à ce rang du classement de la saison active ; `@dernier` : le dernier |
| `@hasard` | un candidat au hasard (tiré une fois, gardé tant qu'il est là) |
| `@public` | quelqu'un du public au hasard (zone `public`, sinon un joueur qui n'est ni candidat ni le maître) |
| `@scene`, `@moment`, `@candidat`, `@proche`, `@zone:<nom>` | comme plus haut ; ou un pseudo |

« Vise » pointe un endroit fixe : `@centre` (le centre de la scène), `@point:<nom>`, ou `x y z`.

### Effets de lumière
`respiration` (lente), `pulse` (battement), `strobe` (flashs), `scintille` (flamme), `chenillard` (les projecteurs
du groupe s'allument tour à tour, de gauche à droite). `vitesse` : 1 = normal. Les effets suivent l'heure du monde :
tous les joueurs et la diffusion voient la même chose.

### Vagues et cascades
`vague` (lumière) : les projecteurs du groupe prennent le changement **l'un après l'autre**, de la gauche à la
droite du public, étalé sur ce nombre de secondes. `cascade` (tableaux) : un tableau après l'autre, ce nombre de
secondes d'écart.

### Le plateau
| Commande | Effet |
|---|---|
| `/plateau` | où en est le plateau |
| `/plateau devant` | dans le public, en regardant la scène : donne la gauche et la droite (miroirs, vagues) |
| `/plateau point <nom>` | un point visable là où tu es (`centre` : le centre de la scène ; sans lui, le centre de la zone `scene`) |
| `/plateau maitre <pseudo>` · `scene <zone>` · `public <zone>` | le CraftMaster, la zone de la scène, celle du public |

### Les commandes
| Commande | Effet |
|---|---|
| `/sequence` | la liste, cliquable (▶ : lancer ; nom : éditer) |
| `/sequence lancer <nom> [depuis]` · `stop [nom]` · `statut` | plusieurs séquences peuvent jouer en même temps (un chenillard en boucle sous une révélation) |
| `/sequence creer <nom> [secondes]` · `dupliquer` · `renommer` · `supprimer` | |
| `/sequence miroir <nom>` · `inverser <nom>` · `etirer <nom> <facteur>` · `boucle <nom> <true\|false>` | les outils sur toute la séquence |
| `/sequence importer [nom]` · `exporter <nom>` | fichiers `config/craftmaster/sequences/<nom>.json` |

Une séquence se lance aussi comme une conduite : `/conduite auto revelation <séquence>`, `/conduite manche …`, une
ligne `sequence <nom>` dans une conduite, le bouton de la régie des modos, la voix (« lance la conduite … »), le
Stream Deck (`/stage/cue?name=…`). « Stop » arrête conduites et séquences.

### Écrire une séquence à la main (ou par une IA)
Un fichier `config/craftmaster/sequences/<nom>.json`, puis `/sequence importer <nom>`. Clés en français ; un champ
absent = inchangé. Une IA peut en écrire une à partir d'une phrase comme « révélation dramatique : noir, roulement,
poursuite or sur le premier, vague de faces en ambre ».

```json
{
  "duree": 14,
  "boucle": false,
  "description": "Révélation du vainqueur",
  "pistes": [
    {"type": "lumiere", "cible": "tout", "cles": [
      {"t": 0, "allume": false, "fondu": 1.5}
    ]},
    {"type": "lumiere", "cible": "face", "cles": [
      {"t": 2, "allume": true, "couleur": "bleu", "intensite": 30, "cone": 12, "vise": "@centre", "effet": "chenillard", "vitesse": 1.5, "fondu": 1},
      {"t": 9, "couleur": "ambre", "intensite": 100, "effet": "aucun", "vague": 1.2, "fondu": 0.5}
    ]},
    {"type": "lumiere", "cible": "poursuite", "cles": [
      {"t": 8, "allume": true, "suit": "@rang:1", "couleur": "or", "intensite": 100, "cone": 4, "effet": "respiration", "fondu": 0.3}
    ]},
    {"type": "rideau", "cible": "fond", "cles": [{"t": 8, "ouvert": true}]},
    {"type": "tableaux", "cible": "1", "cles": [{"t": 9, "hauteur": 3, "fondu": 4, "cascade": 0.3}]},
    {"type": "ecran", "cible": "geant", "cles": [{"t": 9, "montre": "clip dernier"}]},
    {"type": "commande", "cles": [
      {"t": 7.5, "commande": "playsound minecraft:block.note_block.snare master @a ~ ~ ~ 1 0.8"},
      {"t": 8, "commande": "title @a title {\"text\":\"Vainqueur !\",\"color\":\"gold\"}"}
    ]}
  ]
}
```

| Piste | Champs d'un keypoint (en plus de `t` et `fondu`) |
|---|---|
| `lumiere` | `allume`, `couleur` (nom ou `#RRVVBB`), `intensite` 0-100, `cone` 1-30, `suit`, `vise`, `horizontal` + `vertical`, `effet`, `vitesse`, `vague` |
| `rideau` | `ouvert` |
| `tableaux` | `hauteur` (ficelle, blocs), `cascade` |
| `ecran` | `montre` : ce que prend `/cmtele <écran>` (`clip dernier`, `lien <url>`, `chat`, `off`) |
| `commande` | `commande` : une ligne de conduite (commande sans `/`, `ambiance x`, `noir 2`…) |

## 5. Tableaux suspendus et télés
- Monter / descendre les tableaux : `/cfpt hoist <saison> <blocs> [secondes]` ([Tableaux](Tableaux.md)) ; une
  ambiance retient leur hauteur.
- Télés nommées (`/cmtele nommer <nom>`) : une ambiance retient ce qu'elles montrent ([Clips](Clips.md)).

## 6. À la voix, au Stream Deck, à la régie des modos
- Voix : « noir », « noir en 3 secondes », « ambiance plateau », « passe en ambiance révélation en 5 secondes »,
  « lance la conduite ouverture », « stop la conduite », « la face en rouge en 3 secondes », « baisse le contre à
  40 % », « ouvre le cône de la face », « la poursuite sur qui est sur scène ».
- Stream Deck : `/stage/look?name=plateau&fade=3`, `/stage/cue?name=ouverture`, `/stage/stop`, `/stage/noir?fade=2`,
  `/spot?target=face&color=rouge&intensity=60&cone=8&follow=@scene&fade=2&on=true` (n'importe quels champs).
- Régie des modos (touche `;`), onglet **Scène** : un bouton par ambiance et par conduite, Stop, Noir, une rangée de
  couleurs pour tous les projecteurs et la poursuite (sur scène, le moment, candidat, proche, fixes). Il faut le droit
  « camera ».

Fichier : `config/craftmaster/scenographie.json` (ambiances, conduites, séquences, plateau, lancements automatiques), sauvegardé à chaque
changement.
