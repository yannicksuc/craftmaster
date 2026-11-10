# CraftMaster — wiki

Mod Fabric de l'event **CraftMaster** : saisons, parties et manches, scores en direct et publiés, déroulé du direct
(titres, chrono, révélation), classement, busts, chaises et rideaux de scène.
Minecraft 26.3, à installer sur le serveur **et** chez les joueurs (avec Fabric API).

## Pour les organisateurs
- [Installation et passage depuis le plugin](Installation.md)
- [Émission : déroulé du direct, plateau, révélation](Emission.md)
- [Commandes et permissions](Commandes.md)
- [Classement (écran K, saisons cachées)](Classement.md)
- [Busts](Busts.md)
- [Tableaux suspendus (galeries, poses, éditeur)](Tableaux.md)
- [Rideaux de scène](Rideaux.md)
- [Chaises](Chaises.md)
- [Plateau : trône, éclairage, projecteurs, chrono géant, écrans /image, pupitres, gong…](Plateau.md)
- [Régie vocale (piloter à la voix)](RegieVocale.md)
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

Le tout est aussi faisable à la voix (régie vocale). Le détail : [Émission](Emission.md).
