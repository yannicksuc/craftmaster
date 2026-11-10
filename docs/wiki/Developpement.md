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
  data/                   le fichier config.yml (format 2) et le modèle
    CraftMasterData       chargement / migration / sauvegarde, point unique de modification (change) et
                          journal d'annulation, API « tâches » du plugin, busts
    Season, Partie,       saison (joueurs, parties, scores publiés), partie (manches, scores en direct),
    Manche, SeasonPlayer  manche (id stable, chrono, TP, scène, tâche), joueur de la saison (surnoms)
    Task                  texte de tâche d'une manche (titre, texte, règles, durée, cire)
    Stage, Live           plateau (rideaux nommés, ancre, décors, durées de révélation), état du direct
    Migration             format 1 (plugin, mod 2.0) → 2
  show/                   l'émission
    Actions               le schéma d'actions appliqué aux données, sans serveur (testable) → lignes + effets
    ActionExecutor        point d'entrée voix et commandes : Actions, puis effets dans le monde, rafraîchissements
    ShowContext           contexte pour la régie vocale (Map prête pour JSON)
    ShowEvents            écouteurs : publication, étapes de la révélation (tableaux suspendus)
    Show                  titres, sons, TP, chrono (barre de boss, décompte), annonces de score
    Reveal                révélation mise en scène (étapes planifiées par tick)
    StageControl          rideaux nommés, décors (structures vanilla)
  command/                les commandes (Brigadier) : CraftMasterCommands (plugin, busts, rideaux, classement),
                          ShowCommands (v3), WordArgument (noms avec accents)
  bust/Busts              têtes et étiquettes flottantes
  portrait/               tableaux suspendus : PortraitEntity, Portraits (galeries, publication, illumination),
                          PortraitRanking (seule source des scores), GalleryPlanner (colonnes, plans, sans croisement),
                          PortraitGeometry (proportions bébé, centre de gravité, nœud), PortraitAnimation, Pose,
                          PoseLibrary (poses.json), PortraitNetwork, PortraitCommands (/cfpt), PortraitItem
  camera/                 réalisation TV (chantier E) : CameraSpot (une caméra : numéro, nom, rôle, objectif, zoom,
                          mouvement), CameraList (recherche « caméra 2 » / « plan large » / « trône »…, pur),
                          CameraMotion (respiration, travelling, panoramique, plan qui suit : maths pures),
                          Cameras (registre cameras.json + trépieds), CameraEntity (trépied), CameraItem
  broadcast/              réalisation TV : Director (plan à l'antenne, réalisation auto, état envoyé aux clients,
                          spectateurs gardés près du plan), BroadcastNetwork (paquets show_state, show_cue,
                          broadcast_mode, camera_request), BroadcastActions (actions camera.* / broadcast.* /
                          show.*, vérifiées sans serveur), Credits (génériques d'ouverture et de fin)
  task/                   tâches, enveloppes, notes cachées (chantier D, docs/wiki/Taches.md)
    Tasks                 règle du secret, révélation, tâche du moment envoyée à chacun, cartes, enveloppes,
                          effets des actions task.* / envelope.give ; contrat openPaper / reveal
    Notes                 cachettes (clic droit / accroupi selon le bloc, entités), trouvailles, éditeur, outil
    TaskStore             tasks.json : révélations, notes (Note : cachettes, trouvailles, morceaux)
    TaskNetwork           paquets (État, Carte, ShowCard, éditeurs, actions des écrans, sceau)
    TaskContent, -Components  objets / blocs / composants (enveloppe, sceau, note, bouteille, lampe UV,
                          EnvelopeBlock + entité de bloc, PinnedNoteBlock), WaxColor, TwitchIrc (lecture du chat)
  curtain/Curtains        animation d'ouverture / fermeture (file d'attente par tick)
  block/                  ChairBlock, CurtainBlock, Shapes4 (formes tournées dans les 4 sens)
  entity/SeatEntity       l'entité invisible sur laquelle on s'assoit
  item/CurtainRemoteItem  la télécommande
  network/Network         paquets du classement (demande client → réponse serveur, mises à jour)
  regie/                  régie vocale, côté serveur et commun : RegiePayloads (paquets), RegieServer (contrôle
                          admin, contexte, exécution), RegieJson, Wav (WAV 16 kHz), RegieService (client HTTP)
  show/                   ShowContext (contexte de la partie) et ActionExecutor (exécution du schéma d'actions)
  registry/ModContent     blocs, objets, entité, onglet créatif (block / item publics : studio/ s'y enregistre)
  studio/                 décor du studio (chantier F, Plateau.md) : Studio (enregistrements), sièges
                          (StudioSeatBlock, SofaBlock : ChairBlock + SeatEntity), LampBlock, OnAirSignBlock,
                          SpotlightBlock / BlockEntity (+ SpotlightAim, Spotlights), TimerDisplayBlock / BlockEntity
                          (+ SevenSegment, StudioTimer), ImageScreenEntity / Item (+ ImageRequest), DeskBlock,
                          PrizeTableBlock, GongBlock (+ GongSwing), DeskBellBlock, DecorBlock, StanchionBlock,
                          StudioNetwork (paquets), StudioCommands (/image, /cfsp, /cfdk, /cfgo)
  mixin/                  accès aux setters privés des text_display
src/client/java/…/client/                   client uniquement (jamais chargé par un serveur dédié)
  CraftMasterClient       touche K, rendu (vide) du siège, réception du classement
  RankingScreen           l'écran de classement
  regie/VoiceRegie        touche N, enregistrement, envoi au service puis au serveur ; Recorder (micro / WAV) ;
  regie/RegieHud          le panneau « ● Régie à l'écoute » / résultat (HudElementRegistry)
  task/                   CurrentTask (contrat : tâche du moment, carte en surimpression), PaperView (la carte :
                          papier, typographie, cire), PaperScreen (lecture + ouverture animée de l'enveloppe),
                          TaskEditorScreen, SealScreen, NoteEditorScreen, TwitchChat (!tache), TaskClient (touche J)
  studio/                 rendus du studio : SpotlightRenderer (lyre, tête, faisceau additif, tache de lumière),
                          TimerDisplayRenderer (7 segments), ImageScreenRenderer + ScreenImages (téléchargement,
                          cache mémoire et disque), ImageScreenSettings, DeskRenderer, PrizeTableRenderer,
                          GongRenderer, StanchionRenderer (cordons), StudioTimerClient, Geo (quads et boîtes)
  portrait/               PortraitRenderer, PortraitClipper (coupe par la toile, fenêtre Antichamber), PortraitSkins
                          (skin du joueur ou image téléchargée), PortraitScreen (réglages), PoseEditorScreen
  broadcast/              mode diffusion (chantier E) : Broadcast (F8, /diffusion, touches 1–9 et molette, HUD
                          vanilla masqué), ShowStateClient (état reçu, chrono lissé), DirectorCamera (vue du plan,
                          transitions), Transition (fondu enchaîné : copie de la dernière image), BroadcastOverlay
                          (habillage TV), ThemeSound, CameraRenderer (trépied, voyant rouge, numéro)
  task/CurrentTask        BOUCHON du contrat du chantier D (remplacé par la vraie classe à la fusion)
  mixin/                  CameraMixin (vue du plan, champ), GameRendererMixin (pas de balancement), HandsRendererMixin,
                          EntityRenderDispatcherMixin (trépieds, joueur caméra et spectateurs cachés), MouseHandlerMixin
                          (molette), ToastManagerMixin, GuiGraphicsExtractorAccessor — craftmaster.client.mixins.json
src/main/resources/                         fabric.mod.json, mixins, assets et data (générés)
src/test/java/                              tests unitaires JUnit 5
scripts/                                    générateur de ressources, test serveur, lanceur de client
tools/voice-regie/                          service local de la régie vocale (Python : Whisper + Claude)
```

### Épreuves (chantier H)
- `trial/` (serveur) : logique pure testée — `ResultSheet` (feuille + classement), `Scale`, `Stopwatch`, `Zone`,
  `BuzzerLock`, `PropConfig`, `PropStats`, `CanvasPixels`, `Weights` ; monde — `Trials` (branchement),
  `TrialContent` (registre), `TrialNetwork` (paquets), `Judging` (annonces, chronos en barre de boss, boutons
  « Noter pour »), `TrialProps` / `TrialPropEntity`, `BoardEntity`, `Buzzers`, `TrialZones`, `TrialLog` ;
  `trial/block/`, `trial/item/`. Commandes : `command/TrialCommands`.
- `client/trial/` : `TrialClient`, rendus (`TrialPropRenderer`, `BoardRenderer` + `BoardTextures` dynamiques,
  `PlinthRenderer`), écrans (`PropScreen`, `ResultsScreen`, `BoardTextScreen`).
- La feuille de résultats vit dans `Manche.results` (fichier de données) : annulable comme les scores.
- `task/Tasks.java` : **bouchon** de `Tasks.openPaper` (chantier D le remplace à la fusion).
- Ressources : `scripts/gen_trials.py`, appelé par `gen_assets.py`.

## Choix techniques
- **Données** : un seul objet en mémoire (le plugin en avait deux qui s'écrasaient), YAML via SnakeYAML embarqué
  dans le jar (`include`), écriture atomique, clés toujours en texte comme Bukkit. Modèle objet (Season → Partie →
  Manche) sérialisé en arbre YAML ; toute modification passe par `CraftMasterData#change` (ou `begin` / `commit` /
  `rollback`) qui garde l'arbre d'avant dans le journal (50 entrées, en mémoire) : annuler = recharger cet arbre.
  Une modification qui ne change rien n'est pas journalisée. Les objets du modèle ne se modifient que dans `change`.
- **Actions** : `Actions.apply` interprète une liste d'actions (`type` + champs, voir docs/design/emission.md §4)
  dans une seule modification : une action invalide annule toute la liste (message français clair). Ce qui touche
  le monde est rendu sous forme d'`Effect` que `ActionExecutor` joue ensuite (titres, TP, chrono, rideaux, décors,
  révélation), puis busts et écrans sont rafraîchis. Les commandes construisent les mêmes actions.
- **Publié / direct** : busts, étiquettes, écran et chat des joueurs lisent `Season#published` ; les admins
  reçoivent les scores en direct avec le total publié (`Network.Row#publishedTotal`).
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
- **Tableaux** : l'entité est le crochet sous le plafond ; tout le reste (cadre, personnage, ficelle, balancement,
  réarrangement) est dessiné par le client à partir des données synchronisées. Le personnage est le modèle joueur
  passé par `HumanoidModel.BABY_TRANSFORMER` (proportions bébé zombie), chaque partie placée par
  `PortraitGeometry` (mêmes matrices pour le dessin et pour le centre de gravité). Sans stencil en 26.3, la coupe par
  la toile et l'effet fenêtre sont faits sur la géométrie (`PortraitClipper` : découpe de polygones, et projection le
  long du regard sur le plan de la toile). Le réarrangement n'envoie qu'un état (colonnes de départ et d'arrivée,
  plan, heure de début) que chaque client anime.
- **Permissions** : Fabric Permission API (`craftmaster:admin`), repli sur le niveau d'opérateur 2.
  Réalisation : `Permissions.CAMERA` = admin **ou** `craftmaster:camera`.
- **Réalisation TV** (chantier E) : le serveur décide du plan (`Director`) et l'envoie à tous dans l'état de
  l'émission (`craftmaster:show_state`, renvoyé dès qu'il change, vérifié tous les 10 ticks) ; chaque jeu en mode
  diffusion calcule sa vue lui-même, image par image (`DirectorCamera` : mouvement de la caméra en temps réel, plan
  qui suit lissé, transitions). La vue remplace position et rotation de `Camera` juste après `alignWithEntity`
  (mixin) : la caméra reste « attachée » au joueur pour les chunks et le tri, n'est pas « détachée » (le corps du
  joueur n'est pas dessiné). Le **fondu enchaîné** copie l'image à l'écran (`RenderTarget#copyColorFrom`, au moment
  où le plan change, avant le rendu de la nouvelle image) et la dessine au-dessus avec une opacité décroissante
  (`BlitRenderState` ajouté au `GuiRenderState`, V inversé). L'habillage est un élément HUD Fabric dessiné sur un
  canevas de 270 unités de haut (480 de large en 16:9) mis à l'échelle de la fenêtre : même rendu quelle que soit la
  résolution et l'échelle d'interface. Les éléments vanilla sont remplacés par des enveloppes qui ne dessinent rien
  en diffusion (`HudElementRegistry.replaceElement`). Le joueur caméra en spectateur est téléporté près du plan
  (sinon le serveur ne lui envoie ni chunks ni entités) ; les trépieds suivent le registre `cameras.json` (un trépied
  inconnu ou remplacé se retire à son chargement). Les génériques sont planifiés côté serveur (même image partout)
  avec des « cues » (`craftmaster:show_cue`) que les clients animent.
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
Exception (chantier E) : `assets/craftmaster/sounds/show/theme.ogg`, le thème provisoire, est fabriqué par
`scripts/gen_theme.py` (Python Windows avec numpy, et ffmpeg dans le PATH) ; `sounds.json` vient de gen_assets.py.

### Ressources du studio (chantier F)
`scripts/gen_studio.py`, appelé par `gen_assets.py` (un seul bloc marqué) : modèles, états, textures peintes texel
par texel (palettes or, velours, noyer, métal, crème), recettes, loot, tags de minage, traductions. Les sons sont
dans le dictionnaire commun `SOUNDS` de `gen_assets.py` (écrit en `sounds.json` à la fin) : chaque partie y ajoute
ses entrées. Les éléments plus hauts qu'un bloc (trône, lampadaire) ou plus larges (enseigne) prennent leurs UV
dans leur tranche de 16 px (`el()` de gen_studio).

