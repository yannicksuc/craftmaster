# Commandes

**Admin** = permission `craftmaster:admin` (LuckPerms…) ou opérateur niveau 2. Chaque commande existe aussi en
minuscules et avec son alias. Noms de tâches et pseudos : accents acceptés, guillemets si le nom contient un espace
(`"Le Pont"`). La touche Tab propose saisons, tâches, joueurs et rangs.

## Tâches (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterAddTask <saison> <tâche>` | cfat | Ajoute la tâche (refusée si elle existe) |
| `/craftMasterRemoveTask <saison> <tâche>` | cfrt | Retire la tâche ; ses points restent dans les totaux |
| `/craftMasterRenameTask <saison> <ancien> <nouveau>` | cfrn | Renomme ; les points suivent (fusionnés si le nouveau nom existe) |

## Scores (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterSetScore <saison> <tâche> <pseudo> <score>` | cfss | Fixe le score |
| `/craftMasterAddScore <saison> <tâche> <pseudo> <points>` | cfas | Ajoute des points (négatif pour en retirer) |
| `/craftMasterRemoveScore <saison> <tâche> <pseudo>` | cfds | Efface ce score ; sans aucun score, le joueur sort du classement |
| `/craftMasterReloadScores` | cfrs | Relit `config/craftmaster/config.yml` (après une modification à la main) |

Un score sur une tâche qui n'est pas dans la liste de la saison est accepté, avec un avertissement.
Chaque changement met à jour les busts et les écrans de classement ouverts.

## Classement
| Commande | Alias | Qui | Effet |
|---|---|---|---|
| `/craftMasterRanking [saison]` | cfrk | tous | Ouvre l'écran (dernière saison par défaut) ; dans le chat si le jeu n'a pas le mod |
| `/craftMasterRanking chat [saison]` | | tous | Classement dans le chat |
| `/craftMasterHideRanking <saison> <true\|false>` | cfhr | admin | Cache / révèle la saison ([Classement](Classement.md)) |

## Busts (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterAddBust <saison> <rang>` | cfab | En regardant une tête de joueur (8 blocs max) : en fait le bust de ce rang |
| `/craftMasterRemoveBust <saison> <rang>` | cfdb | Oublie le bust et retire son étiquette (la tête reste) |

## Rideaux (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterCurtain <open\|close\|toggle> <x y z>` | cfcu | Ouvre / ferme le rideau qui contient ce bloc |

Pratique dans un bloc de commande ou un `/schedule` pour un spectacle minuté.

## Tableaux suspendus (admin)
`/craftMasterPortrait` (alias `cfpt`) — détail dans [Tableaux](Tableaux.md). Sans cible, agit sur le tableau regardé.
| Commande | Effet |
|---|---|
| `/cfpt place <saison> <rang> [<bloc du plafond> <north\|south\|east\|west>]` | Accroche un tableau |
| `/cfpt set [cibles] season\|rank\|player\|name\|skin\|pose\|window\|height <valeur>` | Change un réglage |
| `/cfpt edit [cibles]` / `/cfpt poseEditor [cibles]` | Ouvre l'écran de réglages / l'éditeur de poses |
| `/cfpt remove [cibles]` | Retire le tableau |
| `/cfpt poses` | Liste les poses |
| `/cfpt publish <saison>` | Réarrange les galeries de la saison selon le classement |
| `/cfpt highlight <saison> <rang>` | Illumine le tableau de ce rang (5 s) |
