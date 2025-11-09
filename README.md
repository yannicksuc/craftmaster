# CraftMaster Plugin Documentation

## Description

CraftMaster est un plugin Minecraft Bukkit/Spigot conçu pour gérer des systèmes de classement et de tâches dans le jeu. Il permet aux administrateurs de créer des saisons de défis, d'attribuer des scores aux joueurs, et de gérer des affichages visuels représentant le classement des joueurs.

## Fonctionnalités principales

### 1. Gestion des tâches
- **Ajout de tâches** : `/craftMasterAddTask <SEASON> <TASK_NAME>`
- **Suppression de tâches** : `/craftMasterRemoveTask <SEASON> <TASK_NAME>`
- **Renommage de tâches** : `/craftMasterRenameTask <SEASON> <OLD_TASK_NAME> <NEW_TASK_NAME>`

### 2. Gestion des scores
- **Attribution de scores** : `/craftMasterSetScore <SEASON> <TASK_NAME> <PSEUDO> <SCORE>`
- **Rechargement des scores** : `/craftMasterReloadScores`

### 3. Gestion des busts (affichages visuels de classement)
- **Ajout de busts** : `/craftMasterAddBust <SEASON> <RANK>` - En pointant une tête de joueur (Player Head)
- Les busts sont des éléments visuels qui représentent le classement d'un joueur dans une saison spécifique

### 4. Intégration d'images
- **Création d'images** : `/image <URL>` - Utilise ImageFrame pour afficher des images dans le jeu

## Commandes disponibles

| Commande | Description | Permissions |
|----------|-------------|-------------|
| `/craftMasterAddTask <SEASON> <TASK_NAME>` | Ajoute une nouvelle tâche à une saison | Opérateur |
| `/craftMasterRemoveTask <SEASON> <TASK_NAME>` | Supprime une tâche d'une saison | Opérateur |
| `/craftMasterRenameTask <SEASON> <OLD_TASK_NAME> <NEW_TASK_NAME>` | Renomme une tâche existante | Opérateur |
| `/craftMasterSetScore <SEASON> <TASK_NAME> <PSEUDO> <SCORE>` | Définit le score d'un joueur pour une tâche | Opérateur |
| `/craftMasterReloadScores` | Recharge la configuration et met à jour les affichages | Opérateur |
| `/craftMasterAddBust <SEASON> <RANK>` | Enregistre une tête de joueur comme affichage de classement pour un rang | Opérateur |
| `/image <URL>` | Crée un cadre d'image à partir d'une URL | Joueur |

## Structure du plugin

### Gestionnaires principaux
- [TaskManager](file://C:\Users\User\Documents\GitHub\CraftMaster\src\main\java\fr\lordfinn\craftMaster\data\TaskManager.java#L11-L77) : Gère les tâches par saison
- [ScoreManager](file://C:\Users\User\Documents\GitHub\CraftMaster\src\main\java\fr\lordfinn\craftMaster\data\ScoreManager.java#L9-L73) : Gère les scores des joueurs
- [BustManager](file://C:\Users\User\Documents\GitHub\CraftMaster\src\main\java\fr\lordfinn\craftMaster\data\BustManager.java#L14-L121) : Gère les affichages visuels de classement (busts)

### Système de commandes
Le plugin utilise un système de commandes avec autocomplétion :
- Chaque commande a son propre `CommandExecutor`
- Les `TabCompleter` associés fournissent l'autocomplétion contextuelle

## Utilisation

### Configuration initiale
1. Configurez les saisons dans le fichier de configuration
2. Ajoutez des tâches à chaque saison avec `/craftMasterAddTask`
3. Définissez les scores des joueurs avec `/craftMasterSetScore`

### Gestion des busts
1. Placez des têtes de joueur (Player Head) dans le monde
2. Utilisez `/craftMasterAddBust` en pointant la tête pour l'enregistrer comme affichage de classement
3. Le bust représentera visuellement un rang spécifique dans une saison

### Mise à jour des données
Utilisez `/craftMasterReloadScores` pour recharger la configuration et mettre à jour tous les affichages

## Permissions

Seuls les opérateurs (ou joueurs avec permissions appropriées) peuvent utiliser les commandes d'administration du plugin. La commande `/image` est accessible à tous les joueurs.

## Dépendances

Le plugin nécessite l'API Bukkit/Spigot et utilise le plugin ImageFrame pour la fonctionnalité d'affichage d'images.