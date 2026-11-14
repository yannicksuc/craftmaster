# Plateau principal : scénographie de l'émission

Mis en place le 14/11/2026 sur le plateau construit par LordFinn (salle x 64-95, z -13..13, sol -59) : la scène et
son rideau à l'ouest (galerie des 5 tableaux derrière), les 5 chaises des candidats en arc au milieu, le trône du
CraftMaster à l'est, les 3 socles des prix au fond.

Fichiers : `config/craftmaster/` (à copier dans celui du serveur, serveur arrêté), `installer-plateau.txt` (à
lancer à la console ensuite : noms des projecteurs, rideau), `gen_sequences.py` et `gen_trajets.py` (régénèrent
les timelines et les travellings si on change le plateau).

## Principes
D'après les usages de la télé (jeux à panel type Taskmaster) : peu d'états de lumière de base, solides et nommés,
avec des effets posés par-dessus ; les visages éclairés de face, un contre-jour pour les détacher du fond ; blanc
chaud pour la discussion, bleu froid pour la tension ; fondus de 2-3 s entre états, ~1 s vers la tension, coupure
franche pour une révélation, 3-5 s pour revenir. Caméras : des plans seuls de chacun (CraftMaster, candidats), deux
plans croisés des candidats par-dessus l'épaule du CraftMaster, un large, une plongée ; tenir les plans plutôt que
couper sans arrêt ; les mouvements lents pour l'ouverture, le suspense, les retours.

## Caméras (touches 1-9 du client de diffusion, `/cam cut <n>`)
| n | Plan | Usage |
|---|---|---|
| 1 | Plan large (rôle large) | du fond, en hauteur, toute la salle jusqu'à la scène |
| 2 | CraftMaster (rôle trône) | plan seul, serré |
| 3-7 | Candidat 1 à 5 | plan seul de chaque chaise, de la gauche du public (côté jardin) à sa droite (côté cour) |
| 8 | Scène et galerie (rôle podium) | la scène, le rideau, la galerie (révélation) |
| 9 | Plongée | vue du dessus de l'arc et du trône |
| 10 | Candidats côté cour | par-dessus l'épaule du CraftMaster |
| 11 | Candidats côté jardin | par-dessus l'autre épaule |
| 12 | Profil de la table | de côté, panoramique |
| 13 | Galerie serrée | les tableaux, de près |
| 14 | Les prix | les 3 socles du fond |

Pas de trépied dans le monde : ce sont des points de vue (rien ne gêne les joueurs). Pour en avoir un : `/cam here
<n>` depuis l'endroit voulu, ou l'objet Caméra de studio.

## Travellings (`/cam jouer <nom>`)
`survol` (grue du fond vers la scène, caméra 1) · `galerie` (latéral devant les tableaux, 8) · `tour-de-table`
(arc autour des candidats, 12) · `approche-trone` (avancée sur le CraftMaster, 2) · `recul` (de la scène vers le
fond, 1).

## Ambiances (états de base, `/ambiance lancer <nom> [fondu]`)
`salle` (accueil, chaud et bas) · `discussion` (l'état de travail) · `tension` (bleu froid) · `revelation` (la
galerie en blanc) · `vainqueur` (or, poursuites sur le premier) · `studio` (épreuve en direct, clair) · `attente`
(les candidats sont ailleurs) · `pause`. Plus `/ambiance noir [fondu]`.

## Timelines (`/sequence lancer <nom>`)
| Nom | Quand |
|---|---|
| `ouverture` | début : noir, le trône en silhouette, les candidats s'allument, le rideau s'ouvre sur la galerie, plongée → grue → plan large, puis discussion |
| `annonce` | avant une épreuve : tension, poursuite sur le CraftMaster, plan du trône |
| `jugement` | juger : discussion, poursuite douce sur le candidat du moment |
| `point` | un point donné : pulsation dorée sur la table |
| `prix` | l'épreuve des prix : lumière et plan sur les socles |
| `epreuve-studio` | épreuve sur le plateau : clair, plongée puis large |
| `depart-epreuve` · `retour-plateau` | lancées toutes seules au début / à la fin d'une manche |
| `revelation` · `podium` | lancées toutes seules par la révélation des scores |
| `fin` | sous le générique de fin : salle, rideau qui se ferme, noir |
| `rideau-chenillard` | couche en boucle sur le rideau (`/sequence stop rideau-chenillard`) |

## Conduites
`/conduite lancer ouverture` (la timeline d'ouverture puis le générique), `/conduite lancer fin` (générique de fin
et lumières de fin). Lancements automatiques : `/conduite auto`. Pour une manche jouée sur le plateau :
`/conduite manche <manche> epreuve-studio`.

## Projecteurs (`/cfsp groupe <nom> …`)
`face1-4` portique de l'avant-scène (2-3 : face du CraftMaster ; 1 et 4 : contre-jour des candidats) · `table1-2`
plafond près du trône (face des candidats) · `contre1-2` mur du fond (contre-jour du trône) · `rampe1-4` au sol
devant le rideau · `galerie1-2` au-dessus des tableaux · `poursuite1-2` murs de côté. Points visables :
`@point:trone`, `@point:table`, `@point:galerie`, `@point:rideau`, `@point:prix`.
