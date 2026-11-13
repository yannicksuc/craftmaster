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

## 4. Tableaux suspendus et télés
- Monter / descendre les tableaux : `/cfpt hoist <saison> <blocs> [secondes]` ([Tableaux](Tableaux.md)) ; une
  ambiance retient leur hauteur.
- Télés nommées (`/cmtele nommer <nom>`) : une ambiance retient ce qu'elles montrent ([Clips](Clips.md)).

## 5. À la voix, au Stream Deck, à la régie des modos
- Voix : « noir », « noir en 3 secondes », « ambiance plateau », « passe en ambiance révélation en 5 secondes »,
  « lance la conduite ouverture », « stop la conduite », « la face en rouge en 3 secondes », « baisse le contre à
  40 % », « ouvre le cône de la face », « la poursuite sur qui est sur scène ».
- Stream Deck : `/stage/look?name=plateau&fade=3`, `/stage/cue?name=ouverture`, `/stage/stop`, `/stage/noir?fade=2`,
  `/spot?target=face&color=rouge&intensity=60&cone=8&follow=@scene&fade=2&on=true` (n'importe quels champs).
- Régie des modos (touche `;`), onglet **Scène** : un bouton par ambiance et par conduite, Stop, Noir, une rangée de
  couleurs pour tous les projecteurs et la poursuite (sur scène, le moment, candidat, proche, fixes). Il faut le droit
  « camera ».

Fichier : `config/craftmaster/scenographie.json` (ambiances, conduites, lancements automatiques), sauvegardé à chaque
changement.
