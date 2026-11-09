# CraftMaster — wiki

Mod Fabric de l'event **CraftMaster** : saisons de tâches, scores, classement, busts, chaises et rideaux de scène.
Minecraft 26.3, à installer sur le serveur **et** chez les joueurs (avec Fabric API).

## Pour les organisateurs
- [Installation et passage depuis le plugin](Installation.md)
- [Commandes et permissions](Commandes.md)
- [Classement (écran K, saisons cachées)](Classement.md)
- [Busts](Busts.md)
- [Tableaux suspendus (galeries, poses, éditeur)](Tableaux.md)
- [Rideaux de scène](Rideaux.md)
- [Chaises](Chaises.md)
- [Fichier de données](Donnees.md)

## Pour les développeurs
- [Développement : build, structure du code, ressources](Developpement.md)
- [Tests : unitaires, serveur, en jeu](Tests.md)

## Déroulé type d'une soirée
1. Avant : `/craftMasterAddTask 4 Pont`, `/craftMasterAddTask 4 Tour`… et `/craftMasterHideRanking 4 true`
   pour garder le suspense.
2. Pendant : `/craftMasterAddScore 4 Pont LordFinn 3` à chaque point gagné. Les busts restent anonymes
   (« #1 ? »), les joueurs ne voient pas le classement, les organisateurs si (touche K).
3. La révélation : rideau fermé, `/craftMasterHideRanking 4 false`, puis ouverture du rideau à la télécommande.
   Les têtes prennent le skin des gagnants, tout le monde reçoit « Le classement de la saison 4 est révélé ! ».
