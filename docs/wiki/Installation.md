# Installation

1. Serveur : Fabric Loader ≥ 0.19.5 pour Minecraft 26.3, Fabric API, et `craftmaster-<version>.jar` dans `mods/`.
2. Joueurs : le même jar et Fabric API (le mod ajoute des blocs, une entité et un écran : le jeu des joueurs doit
   les connaître).
3. Solo / LAN : marche aussi, les données sont alors celles de ce PC.

## Venir du plugin Paper
Au premier démarrage, si `plugins/CraftMaster/config.yml` existe dans le dossier du serveur et que
`config/craftmaster/config.yml` n'existe pas encore, le mod copie le fichier du plugin tel quel. Rien d'autre à faire :
tâches, scores et busts sont repris.

| Plugin | Mod |
|---|---|
| Mêmes commandes et alias | Identiques, en plus en minuscules (`/craftmasteraddtask`) |
| Permission `craftmaster.admin` | `craftmaster:admin` (LuckPerms…) ou opérateur niveau 2 |
| Monde `world`, `world_nether`, `world_the_end` | Compris ; les nouveaux busts écrivent `minecraft:overworld`… |
| Placeholders `%cfscore_…%` | Retirés (choix fait au portage) |
| `/image` (ImageFrame) | Retirée : ImageFrame n'existe pas sur Fabric |

Différences de comportement (corrections) :
- Les tâches ajoutées en jeu ne sont plus perdues au redémarrage (le plugin gardait deux copies du fichier).
- Renommer une tâche déplace ses scores vers le nouveau nom.
- Les noms avec accents marchent sans guillemets, comme avant ; un nom avec espaces se met entre guillemets.
