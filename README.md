# CraftMaster (mod Fabric)

Mod de l'event CraftMaster de LordFinn : saisons de tâches, scores, classement, busts, chaises et rideaux de scène.
Fabric, Minecraft 26.3, à installer **sur le serveur et chez les joueurs** (avec Fabric API).
Anciennement un plugin Paper : mêmes commandes, mêmes données (importées automatiquement).

**Documentation complète : [docs/wiki](docs/wiki/Home.md).**

## En bref
- **Tâches et scores** : `/craftMasterAddTask`, `RemoveTask`, `RenameTask`, `SetScore`, `AddScore`, `RemoveScore`,
  `ReloadScores` (+ alias `cfat`, `cfss`, `cfas`…). Accents acceptés sans guillemets.
- **Classement** : touche **K** ou `/craftMasterRanking` — écran avec podium, têtes, détail par tâche, mis à jour
  en direct. `/craftMasterHideRanking <saison> true` garde le suspense jusqu'à la révélation.
- **Saisie des points (arbitre)** : touche **U** ou `/craftMasterScores` — joueurs à réordonner, +1/-1, têtes de
  chiffres, tableau épreuves × joueurs ; `/craftMasterRedo` refait ; API StreamDeck locale et cadre joueur dans le
  HUD ([Saisie](docs/wiki/Saisie.md)).
- **Busts** : `/craftMasterAddBust <saison> <rang>` en regardant une tête de joueur ; elle prend le skin du joueur
  à ce rang, avec une étiquette « #1 Pseudo / 16 pts ».
- **Rideaux de scène** (16 couleurs) : s'ouvrent du milieu vers les côtés ; redstone, télécommande, clic en créatif
  ou `/craftMasterCurtain`.
- **Chaises** (12 bois) : clic droit pour s'asseoir.
- Données : `config/craftmaster/config.yml`, même format que le plugin.

| Page | Contenu |
|---|---|
| [Installation](docs/wiki/Installation.md) | Installer, venir du plugin, différences |
| [Commandes](docs/wiki/Commandes.md) | Toutes les commandes et permissions |
| [Classement](docs/wiki/Classement.md) · [Busts](docs/wiki/Busts.md) · [Rideaux](docs/wiki/Rideaux.md) · [Chaises](docs/wiki/Chaises.md) | Les fonctions en jeu |
| [Données](docs/wiki/Donnees.md) | Le fichier `config.yml` |
| [Développement](docs/wiki/Developpement.md) · [Tests](docs/wiki/Tests.md) | Build, structure du code, tests |

## Build
`.\gradlew.bat build` (Java 25) → `build/libs/craftmaster-<version>+26.3.jar` et `build/libs/craftmaster-<version>+26.2.jar`
(un jar par version de Minecraft, même code, tests unitaires lancés sur les deux ; voir [Développement](docs/wiki/Developpement.md)).

## Crédits
- Police machine à écrire des lettres de tâche (`craftmaster:typewriter`) : conversion bitmap de **Cairopixel** de
  GGBotNet, sous licence SIL Open Font License 1.1 (version modifiée renommée « CraftMaster Typewriter », licence
  dans `src/main/resources/assets/craftmaster/font/OFL.txt`).
