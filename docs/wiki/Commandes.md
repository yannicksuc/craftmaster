# Commandes

Tout passe par **une seule commande** : `/craftmaster`, ou s`/cm tv tache [s]` · `scores [show|auto|on|off]` raccourci **`/cm`**. Elle se lit comme une phrase :
`/cm <quoi> <que faire> [détails]`, tout en français, et la touche **Tab** propose à chaque étape ce qui existe
(groupes, acti`/cm tv tache [s]` · `scores [show|auto|on|off]`s, manches, joueurs, arènes, ambiances, caméras, options et leurs valeurs).

- `/cm` seul ou **touche N** : le **menu CraftMaster** ([Menus](#le-menu-craftmaster)) ; sans le mod : l'état du direct.
- `/cm aide [groupe]` : les groupes, cliquables, puis les acti`/cm tv tache [s]` · `scores [show|auto|on|off]`s d'un groupe.
- **Admin** = permissi`/cm tv tache [s]` · `scores [show|auto|on|off]` `craftmaster:admin` (LuckPerms…) ou opérateur niveau 2. Les droits des modos
  (`craftmaster:camera`, `tv`, `replay`, `image`) ouvrent seulement leurs groupes.
- Noms avec espace ou accent : entre guillemets (`"Le P`/cm tv tache [s]` · `scores [show|auto|on|off]`t"`). Une manche se désigne par son nom, son surnom ou `next`.
- Chaque commande rép`/cm tv tache [s]` · `scores [show|auto|on|off]`d en français : ce qui a changé en doré, une erreur en rouge avec ce qu'il faut faire.
- Tout ce qui touche aux sais`/cm tv tache [s]` · `scores [show|auto|on|off]`s, manches, joueurs, scores et feuilles passe par l'exécuteur de la régie vocale :
  même comportement que la voix et **annulable** (`/cm annuler`).

## Les anciennes commandes
Les anciens noms (`/cfma`, `/cfar`, `/cam`, `/tv`, `/tache`, `/ambiance`, `/craftMasterManche`…) ne s`/cm tv tache [s]` · `scores [show|auto|on|off]`t plus
proposés, mais **marchent encore** : le serveur les traduit vers la nouvelle forme (`/cfma start p`/cm tv tache [s]` · `scores [show|auto|on|off]`t` →
`/cm manche lancer p`/cm tv tache [s]` · `scores [show|auto|on|off]`t`). En jeu, une ligne grise rappelle le nouveau nom ; la console, le RCON, les conduites déjà
enregistrées, les blocs de commande et la régie IA s`/cm tv tache [s]` · `scores [show|auto|on|off]`t traduits sans rien dire (une ligne dans le journal). Les trois
commandes du plugin qui doublaient les manches (`craftMasterAddTask`, `RemoveTask`, `RenameTask`) s`/cm tv tache [s]` · `scores [show|auto|on|off]`t supprimées :
`/cm manche creer|archiver|renommer`.

Restent à part, parce qu'elles vivent dans le jeu de chacun (pas sur le serveur) : `/diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]`` (F8), `/regie` (`;`),
`/ia`, `/essaifx`, `/cmbarre`, `/cmtwitch`. Le serveur sait aussi les ouvrir : `/cm menu`, `/cm tv diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]` <joueur>`.

## L'arbre

| Groupe | Ce qu'`/cm tv tache [s]` · `scores [show|auto|on|off]` y trouve |
|---|---|
| `menu` · `aide` · `statut` · `historique` · `annuler` · `refaire` | Menu, aide, état du direct, journal et annulati`/cm tv tache [s]` · `scores [show|auto|on|off]` |
| `sais`/cm tv tache [s]` · `scores [show|auto|on|off]`` · `partie` · `manche` · `joueur` | La saison, ses parties, ses manches (lancement, chrono, TP…) et ses candidats |
| `points` · `scores` · `classement` · `revelati`/cm tv tache [s]` · `scores [show|auto|on|off]`` | Donner des points, corriger / publier les scores, classement, révélation |
| `tache` · `note` | Lettres de tâche, enveloppes, notes cachées |
| `epreuve` · `arene` | Outils d'épreuve (feuille, chr`/cm tv tache [s]` · `scores [show|auto|on|off]`os, buzzers, zones, objets…) · arènes copiées par candidat |
| `plateau` | Le décor : rideaux, ancre et décors de scène, points du plateau, portes de garage, pupitres, g`/cm tv tache [s]` · `scores [show|auto|on|off]`g, tablettes, tableaux, busts |
| `ambiance` · `c`/cm tv tache [s]` · `scores [show|auto|on|off]`duite` · `sequence` · `projecteur` | Lumières et scénographie |
| `camera` (`cam`) · `tv` · `fx` · `replay` · `media` | Réalisati`/cm tv tache [s]` · `scores [show|auto|on|off]`, habillage, effets, replays, écrans, clips et chaînes |
| `modo` · `ia` | Régie des modos · bout`/cm tv tache [s]` · `scores [show|auto|on|off]`s de la régie IA |
| `debug` · `reprise` · `worldedit` · `nommer` · `go` | Mode debug, reprise après crash, outils |

### Le direct
| Commande | Effet |
|---|---|
| `/cm statut` · `/cm statut c`/cm tv tache [s]` · `scores [show|auto|on|off]`texte` | Saison, manche en cours, dernier score, scores non publiés, chrono, révélation · le contexte JSON de la régie vocale |
| `/cm historique` | Les 10 dernières modificati`/cm tv tache [s]` · `scores [show|auto|on|off]`s |
| `/cm annuler [n]` · `/cm refaire [n]` | Annule / refait (50 gardées depuis le démarrage) |
| `/cm reprise [statut]` · `reprendre [force]` · `geler` | Après un crash : état, feu vert (chaque chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o repart avec son reste), gel à la main ([Épreuves](Epreuves.md#6-après-un-crash--le-show-gelé)) |
| `/cm go` | Le bout`/cm tv tache [s]` · `scores [show|auto|on|off]` « Commencer l'épreuve » de la lettre, pour un jeu sans le mod (cliqué dans le chat) |

### Sais`/cm tv tache [s]` · `scores [show|auto|on|off]`s, parties, joueurs (admin)
| Commande | Effet |
|---|---|
| `/cm sais`/cm tv tache [s]` · `scores [show|auto|on|off]` liste` | Les saisons (★ active) |
| `/cm sais`/cm tv tache [s]` · `scores [show|auto|on|off]` creer [nom]` · `activer <n>` · `renommer <n> <nom>` | Nouvelle saison (devient active) · change d'active · renomme |
| `/cm sais`/cm tv tache [s]` · `scores [show|auto|on|off]` archiver <n> <true\|false>` · `supprimer <n>` | Range (lecture seule) · supprime (annulable, les busts restent) |
| `/cm partie liste\|creer\|renommer\|statut\|archiver\|supprimer\|activer …` | Les parties (facultatif ; numéro ou nom) |
| `/cm joueur liste` · `ajouter <pseudo> [surnoms…]` · `surnoms <pseudo> [surnoms…]` | Candidats de la sais`/cm tv tache [s]` · `scores [show|auto|on|off]` |
| `/cm joueur renommer <pseudo> <nouveau>` · `retirer <pseudo>` | Scores suivent · archivé s'il a des points |

### Manches (admin)
| Commande | Effet |
|---|---|
| `/cm manche lancer <manche\|next>` · `suivante` · `fin` | Lance (titre, s`/cm tv tache [s]` · `scores [show|auto|on|off]`, TP, chrono, décor) · la suivante · fin de la manche |
| `/cm manche chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o go` · `chrono stop` | Lance tout de suite le chrono qui attend sa lettre ou son bouton · l'arrête (libère les figés) |
| `/cm manche chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o <manche> <secondes>` | Durée du chrono (0 = aucun) |
| `/cm manche depart <manche> <lancement\|lettre\|bout`/cm tv tache [s]` · `scores [show|auto|on|off]`>` | Quand le chrono démarre ([Tâches](Taches.md)) |
| `/cm manche liste` · `creer <nom> [s]` · `renommer` · `surnoms` · `ordre <manche> <positi`/cm tv tache [s]` · `scores [show|auto|on|off]`>` | Gestion de la liste |
| `/cm manche scene <manche> <true\|false>` · `decor <manche> <décor\|aucun>` | Se joue sur scène · décor posé au lancement |
| `/cm manche tp ajouter <manche>` · `tp effacer <manche>` | Points de TP (là où tu te tiens) |
| `/cm manche archiver <manche> <true\|false>` · `supprimer <manche>` | Range · supprime avec ses points (annulable) |
| `/cm manche camerainventaire <manche> <oui\|n`/cm tv tache [s]` · `scores [show|auto|on|off]`\|defaut>` | Caméra d'inventaire des viewers pour cette manche ([Caméra d'inventaire](#caméra-dinventaire)) |

### Points, scores, classement
| Commande | Effet |
|---|---|
| `/cm points <pseudo> <points> [manche]` | Points pour la manche en cours (ou nommée), ann`/cm tv tache [s]` · `scores [show|auto|on|off]`cés « LordFinn +3 (Le Pont) » |
| `/cm scores saisie [sais`/cm tv tache [s]` · `scores [show|auto|on|off]`]` | Écran de saisie de l'arbitre (touche **U**, [Saisie](Saisie.md)) |
| `/cm scores publier [sais`/cm tv tache [s]` · `scores [show|auto|on|off]`]` | Publie : busts, étiquettes, écrans, classement des joueurs |
| `/cm scores ajouter\|fixer <sais`/cm tv tache [s]` · `scores [show|auto|on|off]`> <manche> <pseudo> <n>` · `effacer <saison> <manche> <pseudo>` | Corrige un score d'une saison donnée |
| `/cm scores recharger` | Relit `c`/cm tv tache [s]` · `scores [show|auto|on|off]`fig/craftmaster/config.yml` (vide le journal d'annulation) |
| `/cm classement [sais`/cm tv tache [s]` · `scores [show|auto|on|off]`]` · `classement chat [saison]` (tous) | L'écran du classement (touche **K**) · dans le chat |
| `/cm classement cacher <sais`/cm tv tache [s]` · `scores [show|auto|on|off]`> <true\|false>` | Cache / révèle une saison (révéler publie) |
| `/cm revelati`/cm tv tache [s]` · `scores [show|auto|on|off]` lancer [saison]` · `stop` · `durees [rideau tambour joueur podium]` | Révélation mise en scène ([Émission](Emission.md)) |

### Tâches et notes (admin, sauf `/cm tache`)
`/cm tache` seul (**tous**) : la tâche du moment (touche **J**). Détail : [Tâches](Taches.md).
| Commande | Effet |
|---|---|
| `/cm tache editer [manche]` · `sceau` · `lire <manche>` · `apercu <manche>` · `liste` | Éditeur · sceau · lire · jouer la lettre pour soi · la liste |
| `/cm tache regler <manche> titre\|texte\|ajouter\|regles\|sec`/cm tv tache [s]` · `scores [show|auto|on|off]`des\|cire\|revelee …` | Réglages de la lettre |
| `/cm tache effacer <manche>` · `reveler [manche]` · `cacher [manche]` · `suite [manche]` | Efface · révèle · remet au secret · révèle la partie `[secret]` suivante |
| `/cm tache m`/cm tv tache [s]` · `scores [show|auto|on|off]`trer [manche] [s]` · `enveloppe <manche> [joueurs\|tous]` · `fermer [joueurs]` | Carte à l'écran de tous · enveloppes · fermer |
| `/cm tache cachertout` · `outils` | Tout redevient secret (répétiti`/cm tv tache [s]` · `scores [show|auto|on|off]`) · sceau et lampe UV |
| `/cm note liste\|info\|creer\|editer\|texte\|regler\|d`/cm tv tache [s]` · `scores [show|auto|on|off]`ner\|bouteille\|reset\|decacher\|supprimer …` | Notes cachées ([Tâches](Taches.md#notes-cachées)) |

### Épreuves (admin) — [Épreuves](Epreuves.md)
| Commande | Effet |
|---|---|
| `/cm epreuve feuille [ouvrir <manche>]` | Feuille de résultats (touche **R**) |
| `/cm epreuve feuille regler <joueur> <valeur> [mesure]` · `dq\|undq <joueur>` · `retirer <joueur>` · `effacer` | Noter, disqualifier, effacer |
| `/cm epreuve feuille mesure <type>` · `ordre <lower\|higher\|`/cm tv tache [s]` · `scores [show|auto|on|off]`>` · `bareme <5,4,3,2,1>` · `appliquer [barème]` · `montrer` | Réglages, barème, feuille dans le chat |
| `/cm epreuve chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o lancer\|stop <joueur> [noter]` · `reset [joueur]` · `liste` · `<secondes>` | Chronos des candidats · lance le chrono de la manche maintenant |
| `/cm epreuve buzzer reset\|statut` | Buzzers |
| `/cm epreuve z`/cm tv tache [s]` · `scores [show|auto|on|off]`e creer\|joueurs\|activer\|montrer\|retirer\|liste …` | Zones d'épreuve |
| `/cm epreuve journal` · `reset` · `objet` | Journal · remise à zéro · réglages de l'objet regardé |
| `/cm epreuve motdepasse …` · `vase <n>` · `marquer <but>` · `panneau <texte>` · `vocal commun\|fin` | Objets d'épreuve ([Arènes](Arenes.md)) |
| `/cm epreuve tableau taille\|effacer\|verrouiller\|texte …` | Tableau à dessin regardé |
| `/cm epreuve jouets [statut]` · `liste` · `briller [s]` · `ramasser` · `reset` · `oeuf <blocs>` | Objets à manipuler ([Objets](Objets.md)) |

### Arènes (admin) — [Arènes](Arenes.md)
| Commande | Effet |
|---|---|
| `/cm arene liste` · `creer <nom>` · `spawn <nom>` · `manche <nom> <manche>` | Les arènes, en créer, départ, manche liée |
| `/cm arene copier <nom> [joueurs\|tous]` · `placer <nom> [joueurs]` · `reset <nom>` | Une copie par candidat · chacun à s`/cm tv tache [s]` · `scores [show|auto|on|off]` départ · tout refaire |
| `/cm arene tp <nom> <joueur> [dessus]` · `kit <nom> [vide]` · `champ <nom> [encre]` | Voir une copie · kit · champ à dessiner |
| `/cm arene opti`/cm tv tache [s]` · `scores [show|auto|on|off]` <nom> <option> <valeur>` | `chronoLettre`, `panneauxSuite`, `murs`, `inventaireVide`, `solFin`, `alphabet`, `aveugle`, `cameraInventaire` (true/false), `chronoSuite` (s) — en direct, aussi dans le [menu des épreuves](#épreuves-en-direct) |
| `/cm arene panneaux <nom> [verrouiller]` · `comparer <nom> [noter]` | Panneaux des copies · comparais`/cm tv tache [s]` · `scores [show|auto|on|off]` bloc à bloc |
| `/cm arene fleurs <nom>` · `animal <nom>` | Installe les fleurs de Jeremy / le champ d'Animal devant toi |
| `/cm arene jury <nom> [tous\|<joueur>\|suivant\|precedent] [écran]` | Les dessins (Animal, à l'aveugle) sur l'écran « jury » de la salle : tous, ou un en grand ; bout`/cm tv tache [s]` · `scores [show|auto|on|off]`s dans le chat |
| `/cm arene retirer <nom> <joueur>` · `supprimer <nom>` | Oublie une copie / l'arène |

### Le plateau (admin) — [Plateau](Plateau.md), [Rideaux](Rideaux.md), [Portes de garage](PortesGarage.md), [Tableaux](Tableaux.md)
| Commande | Effet |
|---|---|
| `/cm plateau` · `info` | Rideaux, ancre, décors, points, sens du public |
| `/cm plateau rideau ouvrir\|fermer [nom]` · `regler <nom> [x y z]` · `retirer <nom>` | Rideaux nommés (défaut `stage`) |
| `/cm plateau rideau bloc ouvrir\|fermer\|basculer <x y z>` | Le rideau qui c`/cm tv tache [s]` · `scores [show|auto|on|off]`tient ce bloc |
| `/cm plateau ancre [x y z]` · `decor poser <décor>` · `decor enregistrer <nom> <de> <à>` · `decor supprimer <nom>` | Ancre et décors de scène |
| `/cm plateau devant` · `point <nom>` · `retirerpoint <nom>` | Sens du public · points visés par les projecteurs (`@point:nom`) |
| `/cm plateau garage ouvrir\|fermer\|basculer <x y z>` · `garage porte <nom> …` | Portes de garage |
| `/cm plateau pupitre nom <nom\|->` · `g`/cm tv tache [s]` · `scores [show|auto|on|off]`g` · `tablette lancer\|pause\|basculer\|reset\|relancer` | Pupitre regardé · gong · tablettes chrono |
| `/cm plateau portrait poser\|regler\|editer\|retirer\|poses\|publier\|illuminer\|hisser …` | Tableaux suspendus |
| `/cm plateau buste ajouter\|retirer <sais`/cm tv tache [s]` · `scores [show|auto|on|off]`> <rang>` | Busts (en regardant une tête de joueur) |

### Lumières (admin) — [Scénographie](Scenographie.md)
| Commande | Effet |
|---|---|
| `/cm ambiance [liste]` · `lancer <nom> [f`/cm tv tache [s]` · `scores [show|auto|on|off]`du]` · `enregistrer <nom> [parties] [fondu]` · `modifier` · `noir [fondu]` · `supprimer` | Ambiances |
| `/cm c`/cm tv tache [s]` · `scores [show|auto|on|off]`duite liste\|creer\|ajouter\|inserer\|retirer\|voir\|lancer\|stop\|statut\|supprimer\|auto\|manche …` | Conduites |
| `/cm sequence liste\|editer\|creer\|lancer\|stop\|statut\|…` | Séquences (timeline) |
| `/cm projecteur nom <nom>` · `[tout\|groupe <nom>] allume\|eteint\|couleur\|intensite\|c`/cm tv tache [s]` · `scores [show|auto|on|off]`e\|faisceau\|halo\|ici\|suivre\|stop` · `liste` | Projecteurs |

### Réalisati`/cm tv tache [s]` · `scores [show|auto|on|off]` (organisateur ou `craftmaster:camera`) — [Réalisation](Realisation.md)
`/cm camera` = `/cm cam`. `<caméra>` = numéro, nom ou rôle ; `[transiti`/cm tv tache [s]` · `scores [show|auto|on|off]`]` = `coupe`, `fondu`, `enchaine`, `miroir`, `flash`, `surimpression`.
| Commande | Effet |
|---|---|
| `/cm cam [liste [tout]]` | Les caméras, le plan, la réalisati`/cm tv tache [s]` · `scores [show|auto|on|off]` auto |
| `/cm cam ici <n> [nom]` · `ajouter [nom]` · `viser <n>` · `aller <n>` · `placer <n> <x y z> vers <x y z>` | Poser / viser / voir une caméra |
| `/cm cam nom <n> <nom>` · `role <n> <rôle\|aucun>` · `zoom <n> <15..110\|reset>` · `mouvement <n> <…>` · `retirer <n>` | Réglages |
| `/cm cam couper <caméra> [transiti`/cm tv tache [s]` · `scores [show|auto|on|off]`]` · `large` · `suivante` · `precedente` · `suivre <joueur>` · `pov <joueur>` | Change de plan |
| `/cm cam `/cm tv tache [s]` · `scores [show|auto|on|off]` <on\|off>` · `transition <t>` · `voir` · `rester` · `cadrage` · `eviter` · `lieu` · `focus` · `zone` · `mosaique` | Réalisation automatique |
| `/cm cam trajet <nom> …` · `jouer <trajet>` · `trajets` | Travellings |
| `/cm tv synthe joueur <joueur>` · `synthe texte <titre> [\| sous-titre]` | Synthés |
| `/cm tv tache [s]` · `scores [show|auto|on|off]`\|auto\|on\|off]` · `bandes <on\|off>` · `intro` · `outro` · `stop` · `vedette <joueur>` | Habillage |
| `/cm tv intro <manche> …` | Intro animée d'une manche |
| `/cm tv diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]` <joueur> <on\|off>` | Mode diffusion d'un jeu à distance |
| `/cm tv inventaire [toujours\|epreuves\|jamais]` | [Caméra d'inventaire](#caméra-dinventaire) |
| `/cm fx diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]` <effet> [s]` · `<joueurs> <effet> [s]` · `aucun` · `porter` · `retirer` · `etat` · `liste` | [Effets](Effets.md) |
| `/cm replay …` | [Replays](Replays.md) (`mode`, `lancer`, `stop`, `voir`, `drapeau`, `repere`…) |
| `/cm media image <URL> [l] [h]` · `image effacer\|retirer\|taille\|ajuster` | Image sur un écran du studio (`craftmaster:image`) |
| `/cm media tele …` · `clip …` · `chaine …` | Télés, clips, chaînes Twitch ([Clips](Clips.md)) |

### Régies
| Commande | Effet |
|---|---|
| `/cm modo [liste]` · `ajouter` · `retirer` · `pause` · `reprendre` · `journal` · `site` | [Régie des modos](RegieModos.md) |
| `/cm ia c`/cm tv tache [s]` · `scores [show|auto|on|off]`firmer\|refuser <n>` · `annuler <demande>` | Boutons de la [Régie IA](RegieIA.md) |

### Mode debug (admin)
`/cm debug [`/cm tv tache [s]` · `scores [show|auto|on|off]`|off]` (aussi dans le menu). Pour toi seul, jamais sur la diffusion ni chez les candidats :
- en particules : la z`/cm tv tache [s]` · `scores [show|auto|on|off]`e de référence de chaque arène (or), ses copies (vert, avec leur candidat), les départs
  (col`/cm tv tache [s]` · `scores [show|auto|on|off]`ne blanche), les champs à dessiner (bleu), les zones d'épreuve (rouge), l'ancre de scène et les points du plateau ;
- en haut à gauche : la manche en cours et s`/cm tv tache [s]` · `scores [show|auto|on|off]` chrono, qui attend sa lettre ou son bouton, l'arène où tu es (et la
  copie de qui), les opti`/cm tv tache [s]` · `scores [show|auto|on|off]`s de chaque arène, le plan à l'antenne, la caméra d'inventaire, l'état de la reprise.
Un jeu en mode diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]` (F8) ne l'affiche jamais, même pour un organisateur.

### Outils
| Commande | Effet |
|---|---|
| `/cm worldedit [perf `/cm tv tache [s]` · `scores [show|auto|on|off]`\|off]` | WorldEdit sans voisins ni mises à jour (admin) |
| `/cm nommer [nom]` (tous) | Nomme l'objet tenu (codes `&6`, `&l`…) |

## Caméra d'inventaire
Quand un candidat ouvre s`/cm tv tache [s]` · `scores [show|auto|on|off]` inventaire, les jeux de diffusion et le flux de l'émission le montrent en direct
([Arènes](Arenes.md#caméra-dinventaire)). Qui est m`/cm tv tache [s]` · `scores [show|auto|on|off]`tré :
- `/cm tv inventaire toujours` (défaut) : tous les candidats ;
- `/cm tv inventaire epreuves` : seulement pendant une manche qui la demande (`/cm manche camerainventaire <manche> oui`)
  ou dans une arène qui la demande (`/cm arene opti`/cm tv tache [s]` · `scores [show|auto|on|off]` <nom> cameraInventaire true`) ;
- `/cm tv inventaire jamais`.
Une manche ou une arène réglée à `n`/cm tv tache [s]` · `scores [show|auto|on|off]`` la coupe toujours, même en mode `toujours`.

## Le menu CraftMaster
**Touche N** ou `/cm menu` : un seul point d'entrée, dans les couleurs du kit (velours, cadres dorés). Chaque bout`/cm tv tache [s]` · `scores [show|auto|on|off]`
dit ce qu'il fait au survol ; la molette fait défiler les listes ; les réglages s'appliquent tout de suite.
- **Sais`/cm tv tache [s]` · `scores [show|auto|on|off]` et manches** : la saison active, ses manches (lancer, fin, suivante, chrono go/stop), publier, révélation,
  et les écrans de saisie (U), de classement (K), de tâche et de résultats (R).
- **Épreuves en direct** : chaque arène, sa manche, ses copies ; ses opti`/cm tv tache [s]` · `scores [show|auto|on|off]`s à cocher en direct (chrono à la lettre,
  panneaux à la suite, murs, sol = fin, alphabet, caméra d'inventaire, chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o de la suite à la molette) ; placer, remettre
  à zéro, comparer, panneaux. Les cinq épreuves (Fleurs de Jeremy, Animal, La pomme, Village, Inventaire
  alphabétique / élytres) y s`/cm tv tache [s]` · `scores [show|auto|on|off]`t réglables comme les autres.
- **Ambiances et spots** : lancer une ambiance ou une c`/cm tv tache [s]` · `scores [show|auto|on|off]`duite, noir, l'éditeur de séquences, les projecteurs.
- **Caméra et diffusi`/cm tv tache [s]` · `scores [show|auto|on|off]`** : couper sur une caméra, plan large, auto on/off, la régie des modos, la caméra d'inventaire.
- **Joueurs** : les candidats (en ligne ou n`/cm tv tache [s]` · `scores [show|auto|on|off]`), points +1 / −1, les suivre, aller les voir.
- **Debug** : le mode debug, l'état du direct, annuler / refaire.

## Stream Deck
L'API locale du jeu (`c`/cm tv tache [s]` · `scores [show|auto|on|off]`fig/craftmaster-client.json`, section `streamDeck`, `enabled: true`) répond sur `127.0.0.1`
seulement ; en plus de ses routes (`/publish`, `/reveal/start`, `/menu`, `/ranking`…) elle lance n'importe quelle
commande : `/cm?c=manche lancer next` (envoyée comme si tu la tapais : mêmes droits, rép`/cm tv tache [s]` · `scores [show|auto|on|off]`se dans le chat du jeu).

Le dossier **CraftMaster** (touche de la page principale) est généré par `StreamTools/craftmaster-deck.ps1`
(relançable, sauvegarde du profil avant) ; chaque touche lance `StreamTools/craftmaster.mjs "<commande /cm>"`, qui lit
le port dans l'instance Modrinth CraftMaster la plus récente et affiche une bulle rouge si le jeu ne rép`/cm tv tache [s]` · `scores [show|auto|on|off]`d pas.

| Page | Touches |
|---|---|
| CraftMaster | Manches ▸ · Épreuves ▸ · Lumières ▸ · Caméras ▸ · Menu · Manche suivante · Fin de manche · Chr`/cm tv tache [s]` · `scores [show|auto|on|off]`o go · Chrono stop · Annuler · Publier (2 appuis) · Révélation (2 appuis) · Debug · Serveur ▸ |
| Manches | Saisie (U) · Classement · Résultats · Menu manches · Tâche à l'écran · Révéler la tâche · Suite · Fermer les cartes · Bandeau des scores · Refaire · Joueurs · Carte tâche TV · Statut · Historique |
| Épreuves | Menu épreuves · Journal · Buzzers · Chr`/cm tv tache [s]` · `scores [show|auto|on|off]`os · Vocal commun · Vocal fin · Objets · Faire briller · Reset épreuve · Feuille · Barème · Caméra d'inventaire toujours / épreuves / jamais |
| Lumières | les 8 ambiances (salle, discussi`/cm tv tache [s]` · `scores [show|auto|on|off]`, tension, revelation, vainqueur, studio, attente, pause) · Noir · conduites ouverture / fin · Stop conduite · Spots on / off |
| Caméras | Auto `/cm tv tache [s]` · `scores [show|auto|on|off]` / off · Plan large · Menu caméra · Caméras 1 à 5 · Précédente · Suivante · Bandes on / off · Stop habillage |
| Serveur | Statut · Reprise ? · Reprendre · Geler · Debug `/cm tv tache [s]` · `scores [show|auto|on|off]` / off · Annuler · Refaire · Historique · Aide · Saisons · Joueurs · Manches · Arènes |
