# Développement

## Outils
- Java 25 et Gradle 9.7.1 (wrapper) ; Fabric Loom 1.17 en mode « non obfusqué » (`net.fabricmc.fabric-loom`) :
  Minecraft 26.x est livré avec les noms Mojang, il n'y a pas de mappings. Même stack que CrazyPhone 26.3-fabric.
- Depuis WSL : `cmd.exe /c ".\gradlew.bat build"` (Java et Gradle sont ceux de Windows).

| Tâche | Effet |
|---|---|
| `.\gradlew.bat build` | Compile, lance les tests unitaires, produit `build/libs/craftmaster-<version>.jar` |
| `.\gradlew.bat test` | Tests unitaires seuls |
| `.\gradlew.bat runServer` | Serveur de dev (`run-server/`) |
| `.\gradlew.bat runClient` / `runClient2` | Client LordFinn (`run/`, admin) / Joueur2 (`run2/`, joueur normal) |
| `powershell -File scripts\dev-client.ps1 [-Second]` | Client en arrière-plan, sans son, connecté au serveur de dev |
| `python3 scripts/gen_assets.py` | Régénère modèles, états de blocs, textures, recettes, loot tables, traductions |
| `python3 scripts/server_smoke_test.py <ip>` | Test de bout en bout sur le serveur de dev ([Tests](Tests.md)) |
| `.\gradlew.bat runClient -PregieTrigger` | Client dont la régie vocale joue les WAV déposés dans `run/regie-trigger/` |
| `powershell -File tools\voice-regie\start.ps1` | Service local de la régie vocale ([RegieVocale](RegieVocale.md)) |

## Structure du code
```
src/main/java/fr/lordfinn/craftmaster/      commun (serveur + client)
  CraftMaster.java        point d'entrée : enregistrements, événements serveur
  Permissions.java        qui est organisateur (craftmaster:admin ou op 2)
  data/CraftMasterData    le fichier config.yml : tâches, scores, classement, busts, saisons cachées
  command/                les commandes (Brigadier) et WordArgument (noms avec accents)
  bust/Busts              têtes et étiquettes flottantes
  curtain/Curtains        animation d'ouverture / fermeture (file d'attente par tick)
  block/                  ChairBlock, CurtainBlock, Shapes4 (formes tournées dans les 4 sens)
  entity/SeatEntity       l'entité invisible sur laquelle on s'assoit
  item/CurtainRemoteItem  la télécommande
  network/Network         paquets du classement (demande client → réponse serveur, mises à jour)
  regie/                  régie vocale, côté serveur et commun : RegiePayloads (paquets), RegieServer (contrôle
                          admin, contexte, exécution), RegieJson, Wav (WAV 16 kHz), RegieService (client HTTP)
  show/                   ShowContext (contexte de la partie) et ActionExecutor (exécution du schéma d'actions)
  registry/ModContent     blocs, objets, entité, onglet créatif
  mixin/                  accès aux setters privés des text_display
src/client/java/…/client/                   client uniquement (jamais chargé par un serveur dédié)
  CraftMasterClient       touche K, rendu (vide) du siège, réception du classement
  RankingScreen           l'écran de classement
  regie/VoiceRegie        touche N, enregistrement, envoi au service puis au serveur ; Recorder (micro / WAV) ;
  regie/RegieHud          le panneau « ● Régie à l'écoute » / résultat (HudElementRegistry)
src/main/resources/                         fabric.mod.json, mixins, assets et data (générés)
src/test/java/                              tests unitaires JUnit 5
scripts/                                    générateur de ressources, test serveur, lanceur de client
tools/voice-regie/                          service local de la régie vocale (Python : Whisper + Claude)
```

## Choix techniques
- **Données** : un seul objet en mémoire (le plugin en avait deux qui s'écrasaient), YAML via SnakeYAML embarqué
  dans le jar (`include`), écriture atomique, clés toujours en texte comme Bukkit.
- **Commandes** : chaque commande est enregistrée sous son nom, en minuscules et sous son alias (Brigadier est
  sensible à la casse, Bukkit ne l'était pas). `WordArgument` lit un mot jusqu'à l'espace, accents compris ; il est
  synchronisé avec le client (`ArgumentTypeRegistry`), d'où le mod obligatoire côté joueur.
- **Busts** : le skin passe par un `ResolvableProfile` « non résolu » (le pseudo) que le jeu du joueur résout ;
  les étiquettes sont des `text_display` vanilla taggés, créés ou mis à jour seulement quand les entités du chunk
  sont chargées, et rafraîchis à leur chargement (`ServerEntityEvents.ENTITY_LOAD`).
- **Rideaux** : le rideau est retrouvé par parcours en largeur des blocs voisins de même sens. Les changements
  sont planifiés tick par tick avec le drapeau `UPDATE_CLIENTS` seul, pour que l'animation ne déclenche pas la
  redstone du rideau.
- **Chaises** : l'entité siège doit être sauvegardable (le serveur refuse de faire monter sur une entité qui ne
  l'est pas) ; elle se supprime dès qu'elle est vide ou que la chaise disparaît.
- **Permissions** : Fabric Permission API (`craftmaster:admin`), repli sur le niveau d'opérateur 2.
- **Régie vocale** : la compréhension tourne sur le PC de celui qui parle (service local `tools/voice-regie`),
  jamais sur le serveur. Le client demande le contexte au serveur dès l'appui (il arrive pendant qu'on parle),
  envoie WAV + contexte au service au relâchement (fil à part), puis les actions au serveur qui revérifie le
  droit admin à chaque paquet. Le JSON voyage en tableaux d'octets UTF-8 (pas de limite de 32 767 caractères).
  Les actions `say` ne vont pas au serveur (le client affiche la réponse du service). Claude : un outil strict
  par action, prompt et outils fixes mis en cache, contexte dans le message ; voir tools/voice-regie/README.md.
  Propriétés JVM de dev : `craftmaster.regie.url`, `craftmaster.regie.wav` (WAV à la place du micro),
  `craftmaster.regie.triggerDir` (dossier déclencheur, posé par `-PregieTrigger`).

## Ressources
Tout ce qui est dans `src/main/resources/assets` et `data` vient de `scripts/gen_assets.py` (Python sans
dépendance, les PNG sont écrits à la main). Modifier le script puis le relancer, ne pas éditer les JSON générés.
Formats 26.x à respecter : définitions d'objet dans `assets/<mod>/items/`, loot tables avec `"condition"` unique,
catégories de recette `building | redstone | equipment | misc`.
