# CraftMaster (mod Fabric)

Mod de l'event CraftMaster : saisons de tâches, scores, classement, busts, chaises et rideaux de scène.
Fabric, Minecraft 26.3, à installer **sur le serveur et chez les joueurs** (avec Fabric API).

Anciennement un plugin Paper : mêmes commandes, mêmes données.

## Données

`config/craftmaster/config.yml`, même format que le `config.yml` du plugin :

```yaml
seasons: { '3': { tasks: [Pont, Défi] } }
scores:  { '3': { LordFinn: { Pont: 4, Défi: 3 } } }
busts:   { '3': { '1': { world: minecraft:overworld, x: 0, y: -60, z: 5 } } }
```

Au premier démarrage, s'il trouve `plugins/CraftMaster/config.yml` dans le dossier du serveur, le mod l'importe tel quel.
Les anciens noms de monde Bukkit (`world`, `world_nether`, `world_the_end`) sont compris ; les nouveaux busts
enregistrent l'identifiant de la dimension (`minecraft:overworld`).

## Commandes

Admin = permission `craftmaster:admin` (LuckPerms…) ou opérateur niveau 2. Chaque commande marche aussi en
minuscules (`/craftmasteraddtask`) et avec son alias. Les noms avec accents passent sans guillemets ; un nom avec
espaces se met entre guillemets (`"Le Pont"`).

| Commande | Alias | Rôle |
|---|---|---|
| `/craftMasterAddTask <saison> <tâche>` | cfat | Ajoute une tâche |
| `/craftMasterRemoveTask <saison> <tâche>` | cfrt | Retire une tâche (ses scores restent comptés) |
| `/craftMasterRenameTask <saison> <ancien> <nouveau>` | cfrn | Renomme une tâche, les scores suivent |
| `/craftMasterSetScore <saison> <tâche> <pseudo> <score>` | cfss | Donne un score, met à jour busts et écrans |
| `/craftMasterReloadScores` | cfrs | Relit le fichier et met tout à jour |
| `/craftMasterAddBust <saison> <rang>` | cfab | Enregistre la tête de joueur visée (8 blocs) |
| `/craftMasterRemoveBust <saison> <rang>` | cfdb | Oublie un bust (la tête reste) |
| `/craftMasterRanking [saison]` | cfrk | Ouvre l'écran de classement (tout le monde) |
| `/craftMasterRanking chat <saison>` | | Classement dans le chat |
| `/craftMasterCurtain <open\|close\|toggle> <x y z>` | cfcu | Ouvre / ferme un rideau |

`/image` (plugin ImageFrame) n'existe plus : ImageFrame n'existe pas sur Fabric.
Les placeholders PlaceholderAPI (`%cfscore_…%`) ne sont pas repris.

## En jeu

- **Classement** : touche **K** (réglable). Podium or/argent/bronze, tête de chaque joueur, barre de points,
  détail par tâche au survol, ◀ ▶ pour changer de saison. L'écran se met à jour en direct quand un score change.
- **Busts** : la tête prend le skin du joueur classé au rang, avec une étiquette flottante « #1 Pseudo / 13 pts ».
- **Chaises** (12 bois) : clic droit pour s'asseoir, accroupi pour se lever.
- **Rideaux** (16 couleurs) : les blocs de rideau qui se touchent et regardent dans le même sens forment un seul
  rideau. Il s'ouvre du milieu vers les côtés et se ferme des côtés vers le milieu. Un rideau ouvert est invisible
  (on le revoit en tenant un rideau ou la télécommande, pour le casser ou le lier).
  Pour l'ouvrir / le fermer : un signal redstone sur n'importe quel bloc du rideau, la **télécommande de rideaux**
  (clic droit sur un rideau pour la lier, puis clic droit n'importe où), un clic droit en créatif, ou la commande.
- Onglet créatif « CraftMaster » avec tous les objets (pas de recettes de craft).

## Développement

- Build : `.\gradlew.bat build` (Java 25) → `build/libs/craftmaster-<version>.jar`.
- Client de dev : `.\gradlew.bat runClient` ; serveur : `.\gradlew.bat runServer` (dossier `run-server/`).
- Modèles, textures, traductions et loot tables sont générés par `python3 scripts/gen_assets.py`.
