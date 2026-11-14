# CraftMaster — wiki

Mod Fabric de l'event **CraftMaster** : saisons, parties et manches, scores en direct et publiés, déroulé du direct
(titres, chrono, révélation), classement, busts, chaises et rideaux de scène.
Minecraft 26.3, à installer sur le serveur **et** chez les joueurs (avec Fabric API).

## Pour les organisateurs
- [Installation et passage depuis le plugin](Installation.md)
- [Émission : déroulé du direct, plateau, révélation](Emission.md)
- [Commandes et permissions](Commandes.md)
- [Classement (écran K, saisons cachées)](Classement.md)
- [Saisie des points de l'arbitre (touche U), API StreamDeck, cadre joueur](Saisie.md)
- [Busts](Busts.md)
- [Tableaux suspendus (galeries, poses, éditeur)](Tableaux.md)
- [Écran d'accueil du jeu (accueil.json : fond, LordFinn sur son trône, tableaux, bouton Jouer)](Accueil.md)
- [Épreuves : objets d'épreuve, buzzers, feuille de résultats, chrono, tableaux à dessiner](Epreuves.md)
- [Garde-robe : changer de tenue en un clic (vêtements Selfexpression, toute armure)](GardeRobe.md)
- [Objets à manipuler : canards en plastique, balles, ballons, œuf fragile, golf, cibles qui comptent, verre gradué](Objets.md)
- [Tâches, enveloppes scellées et notes cachées](Taches.md)
- [Rideaux de scène](Rideaux.md)
- [Scénographie : projecteurs (couleur, intensité, cône, poursuite auto), ambiances et conduites automatiques](Scenographie.md)
- [Portes de garage animées, WorldEdit sans mises à jour](PortesGarage.md)
- [Finitions v4 : rideau fluide, éditeur de poses, skins HD](Finitions.md)
- [Chaises](Chaises.md)
- [Plateau : trône, éclairage, projecteurs, chrono géant, écrans /image, pupitres, gong…](Plateau.md)
- [Régie vocale (piloter à la voix)](RegieVocale.md)
- [Régie IA (changer la préparation de l'émission à la voix, pendant le direct)](RegieIA.md)
- [Régie des modos : dans le jeu (touche ;) et sur lordfinn.fr](RegieModos.md)
- [Réalisation TV : caméras, mode diffusion, habillage, générique](Realisation.md)
- [Replays des manches (Server Replay) : un fichier par candidat et pour la zone de l'épreuve](Replays.md)
- [Clips et télés : clip de la vue (F9 / F10), clips et directs Twitch sur les écrans du studio](Clips.md)
- [Effets d'écran : noir et blanc, fish-eye, judas, VHS… et lunettes à porter](Effets.md)
- [Fichier de données](Donnees.md)

## Pour les développeurs
- [Développement : build, structure du code, ressources](Developpement.md)
- [Tests : unitaires, serveur, en jeu](Tests.md)

## Déroulé type d'une soirée
1. Avant : `/craftMasterSeason create Saison 4`, les joueurs avec leurs surnoms
   (`/craftMasterPlayer add Ensuri Ensu`), les manches (`/craftMasterManche create "Le Pont" 180`), leurs points de
   TP (`/craftMasterManche tp add "Le Pont"` à chaque emplacement), le rideau de scène
   (`/craftMasterStage curtain set stage`) et, au besoin, `/craftMasterHideRanking 4 true` pour le suspense.
2. Pendant : `/craftMasterManche next` lance chaque manche (titre, TP, chrono) ; `/cfg LordFinn 3` donne les points,
   annoncés « LordFinn +3 (Le Pont) ». De temps en temps `/craftMasterPublish` met à jour busts et classement des
   joueurs. Une erreur ? `/craftMasterUndo`.
3. La révélation : `/craftMasterReveal start` — rideau fermé, roulement de tambour, révélation du dernier au premier,
   feux d'artifice sur le podium, rideau ouvert.

Pour Twitch : un jeu « caméra » en mode diffusion (F8) montre les plans des caméras du studio et l'habillage TV,
`/tv intro` lance le générique ([Réalisation](Realisation.md)).

Le tout est aussi faisable à la voix (régie vocale). Le détail : [Émission](Emission.md).
