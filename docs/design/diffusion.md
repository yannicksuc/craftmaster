# Conception — CraftMaster v4 : l'émission en direct sur Twitch

Objectif : un rendu aussi proche que possible d'une émission de télé, mais dans Minecraft et diffusé en direct sur
Twitch. Le public suit les épreuves sans effort : tâche lisible, générique, plans de caméra, habillage d'écran.
Les tâches arrivent dans des enveloppes scellées à la cire, qu'on ouvre pour lire la tâche. Les épreuves se créent et
s'arbitrent en jeu. Les joueurs disposent d'objets qui laissent libre cours à leur imagination.

Pas de nom, logo, musique ou visuel d'une émission existante : tout est propre à CraftMaster (sceau « CM », or et
velours rouge comme l'affiche, trône de LordFinn).

Base : v3 (voir `emission.md`, en particulier §6 « Interfaces livrées par le chantier A »). Ce document est la
source de vérité partagée par les chantiers D à H. Tout changement d'interface entre chantiers se note ici.

---------------------------------------------------------------------------------------------------------------------

## Qui regarde quoi

- **LordFinn (organisateur, admin)** : joue le maître du jeu sur son trône, régie vocale, commandes, écrans d'édition.
- **Les candidats** (4 créateurs) : jouent les épreuves, ouvrent leurs enveloppes.
- **Les spectateurs en jeu** : joueurs connectés au serveur sans être candidats (mode spectateur ou non). Ils ont le
  mod. Une touche et une commande leur montrent la tâche en cours.
- **Le client de diffusion** : le jeu (un compte « caméra », ou celui de LordFinn) capturé par OBS pour Twitch.
  En « mode diffusion », il n'affiche plus l'interface normale mais les plans de caméra et l'habillage TV.
- **Le public Twitch** : voit ce que montre le client de diffusion. Option : `!tache` dans le chat Twitch affiche la
  tâche à l'écran (lecture anonyme du chat, aucune clé, délai anti-spam).

Règle : **une tâche reste secrète** pour tous les non-admins (y compris spectateurs et diffusion) tant qu'elle n'a
pas été révélée (enveloppe ouverte, ou manche lancée, ou révélation forcée par l'admin).

---------------------------------------------------------------------------------------------------------------------

## D. Tâches, enveloppes et notes cachées

- **Texte de tâche** par manche : titre court, texte (plusieurs paragraphes, retours à la ligne), règles en puces
  optionnelles, durée affichée optionnelle (reprend `Manche.timer` sinon), couleur de cire. Éditeur en jeu (écran
  admin), commandes, actions vocales (dictée du texte).
- **Enveloppe scellée** (item) : liée à une manche (ou texte libre). Cire avec le sceau CM, couleur au choix. Clic
  droit : écran d'ouverture animé (l'enveloppe arrive, la cire craque, le rabat s'ouvre, la carte glisse et se
  déplie), son de papier et de cire. Ensuite la carte se lit (papier crème, belle mise en page, défilement si long).
  L'ouverture révèle la tâche (règle ci-dessus) et peut être annoncée à tous (« Ensuri ouvre sa tâche »).
  Une enveloppe ouverte reste lisible (état « ouverte », texture différente).
- **Enveloppe posée** : un bloc (ou entité) « enveloppe sur la table », qu'on ramasse comme dans l'émission, ou
  qu'on ouvre sur place.
- **Sceau à cire** (item d'admin) : sceller une enveloppe, choisir la couleur.
- **Notes cachées** : des messages à trouver dans une salle, cachés de façon insidieuse. Idées (à choisir et
  compléter) : note glissée sous un tapis ou une dalle, dans un livre d'une bibliothèque, derrière un tableau, au
  fond d'un coffre à double fond, écriture invisible révélée seulement en tenant une loupe / lampe UV, morceaux de
  note à rassembler, note dans une bouteille, note sur la face cachée d'un bloc. Chaque note : texte, apparence,
  qui peut la voir / la lire, prise une seule fois ou lisible par tous, l'admin voit qui l'a trouvée et quand.
  Lecture avec le même écran papier que la tâche.
- **Consultation** : touche (par défaut `J`, catégorie CraftMaster) et commande (`/craftMasterTask`, alias `/tache`)
  pour toute personne connectée : affiche la tâche en cours révélée (sinon « tâche encore secrète »). Pour l'admin,
  même touche = tâche en cours même secrète, avec un bouton « révéler ».
- **Twitch** (option, désactivée par défaut) : le client de diffusion lit le chat d'une chaîne en anonyme ; `!tache`
  affiche la carte de la tâche quelques secondes à l'écran, au plus une fois toutes les N secondes.
- **Contrat pour les autres chantiers** (côté client) :
  `fr.lordfinn.craftmaster.client.task.CurrentTask` avec `static Optional<Info> get()` et
  `record Info(String mancheId, String mancheName, int mancheNumber, String title, String text, List<String> rules,
  int seconds, boolean revealed)` ; `static void showCard(int ticks)` (affiche la carte de la tâche en surimpression,
  utilisé par la diffusion et `!tache`). Côté serveur : `fr.lordfinn.craftmaster.task.Tasks.reveal(MinecraftServer,
  String mancheId, ServerPlayer by)` et un écouteur `ShowEvents` ajouté si besoin (noter ici).

## E. Réalisation TV : caméras, habillage, générique

- **Caméras** : bloc ou entité « caméra » (sur trépied, numérotée, nommée) que l'admin pose dans le studio et les
  salles. Le client de diffusion passe de l'une à l'autre (touches 1–9, molette, commandes, voix « caméra 2 »,
  « plan large », « suis Ensuri »). Coupe sèche ou fondu, mouvement lent (léger travelling / respiration de caméra),
  plan qui suit un joueur, plan « réaction » sur le trône. Option réalisation automatique (coupe vers le candidat qui
  marque, vers celui qui ouvre une enveloppe, vers le podium pendant la révélation, plan large par défaut).
- **Mode diffusion** (client) : cache l'interface du jeu (main, barre, croix, chat sauf option), bandes cinéma
  optionnelles, et affiche l'habillage :
  logo CraftMaster discret dans un coin, « EN DIRECT » ; bandeau de la manche (numéro, nom, chrono) ; carte de la
  tâche à la demande (contrat D) ; synthé (« lower third ») avec le nom d'un candidat quand on le montre ; bandeau des
  scores (publiés) ; annonce des points en direct (« Ensuri +3 ») ; tout en or et velours rouge, propre et lisible à
  1080p, police lisible, animations d'entrée/sortie.
- **Générique** : séquence d'ouverture (plans du studio enchaînés, titre CraftMaster, saison, présentation de chaque
  candidat avec son nom et son portrait/plan), et de fin (crédits, classement). Musique : un son du mod
  (`craftmaster:show.theme`) que LordFinn pourra remplacer par sa musique dans un pack de ressources (fournir un son
  provisoire libre, ou une petite mélodie générée). Lancé par commande, voix ou bouton.
- **Synchronisation** : un paquet « état de l'émission » vers les clients (saison, partie, manche en cours, chrono,
  candidats, plan de caméra actif) ; noter ici son nom et son contenu.
- Le client de diffusion n'a pas besoin d'être admin pour suivre la réalisation ; piloter les caméras demande admin
  (ou une permission `craftmaster.camera`).

## F. Décor du studio et objets manquants

- **Trône** du maître du jeu (or et velours rouge, assis dessus comme sur les chaises), **siège de l'assistant**,
  **canapé / sièges des candidats** (si les chaises actuelles ne suffisent pas pour le plateau).
- **Éclairage** : lampadaire, lampe de bureau, projecteur orientable (faisceau visible, lumière réelle), suspension.
- **Chrono géant** (bloc ou entité) qui affiche le chrono de la manche en grands chiffres.
- **Écran du studio / cadre image** : remplace `/image <URL>` du plugin (qui passait par ImageFrame). Image téléchargée
  depuis une URL, sur plusieurs blocs (taille au choix), commande `/image <URL> [largeur] [hauteur]` + objet. Aussi
  utilisable pour des images d'épreuve.
- **Pupitres / tables** des candidats avec plaque à leur nom, **table des prix**, **cloche / gong** de fin.
- Tout objet « logique » qui manque au plateau d'une émission de ce genre : à proposer et faire.

## G. Finitions demandées

- **Rideau qui glisse** de façon fluide (rendu côté client : le tissu se déplace en continu, pas bloc par bloc),
  ourlet seulement en bas (déjà fait), plis plus marqués sur les 16 couleurs.
- **Éditeur de poses** plus ergonomique : cliquer une partie du corps sur l'aperçu pour la choisir, la tourner en la
  tirant à la souris, molette pour le réglage fin, annuler / refaire, miroir gauche ↔ droite, remise à zéro d'une
  partie, aperçu 3D dans l'écran.
- **Skins HD** (128×128, 256×256…) acceptés sur les tableaux ; capes si simple.

## H. Épreuves : objets interactifs, arbitrage, créativité

- **Objet d'épreuve** (bloc ou entité configurable) : apparence = n'importe quel bloc ou item (taille, rotation),
  nom ; quand un joueur interagit : message, son, donner un objet, ouvrir une note (contrat D si disponible, sinon
  message), compter les clics, noter qui l'a touché en premier et quand, signal redstone, téléporter, terminer
  l'essai du joueur. Gros **bouton rouge**, **buzzer** (qui a appuyé en premier).
- **Outils d'arbitrage** comme dans l'émission : feuille de résultats de la manche (une valeur par candidat : temps,
  nombre, distance, note du jury ou « disqualifié ») ; classement automatique → proposition 5-4-3-2-1 (ou barème
  choisi, ex-aequo gérés) → un clic applique les points via `ActionExecutor` (`score.set`), donc annulable et
  identique à la voix. Chronomètre par candidat (départ / arrêt, essais en solo pendant que les autres attendent),
  mètre (distance entre deux points), compteur (objets dans un coffre / une zone), balance, zone d'épreuve (sortir de
  la zone = avertissement), marquage disqualification.
- **Créativité** : toile à peindre (dessin pixel à la teinture sur un bloc ou un grand cadre), ardoise / tableau
  blanc où écrire, socle d'exposition (poser n'importe quel objet pour le présenter), malle à accessoires et
  costumes, objets nommables librement… tout ce qui laisse les joueurs inventer.

---------------------------------------------------------------------------------------------------------------------

## Règles communes (D à H)

- Mêmes règles de qualité que `emission.md` §5 : code anglais commenté, tests unitaires de la logique pure, build OK,
  textes fr_fr + en_us via `scripts/gen_assets.py`, wiki mis à jour (une page par chantier + Commandes, Tests,
  Developpement, Home).
- Toute action qui modifie les données passe par `ActionExecutor` (nouvelles actions ajoutées au schéma, à
  `tools/voice-regie` (outils Claude + mode hors ligne si simple) et notées ici) : voix, commandes et écrans font la
  même chose et c'est annulable.
- Textures style Minecraft 16 px, peu de cubes, détail dans la texture (skill `minecraft-modeling`). Écrans dans le
  style or / velours / papier du mod, lisibles, avec infobulles.
- Ports de test par chantier (serveur / RCON / MCP client 1 / MCP client 2) :
  D 25701 / 25702 / 25741 / 25742 ; E 25711 / 25712 / 25751 / 25752 ; F 25721 / 25722 / 25761 / 25762 ;
  G 25771 / 25772 / 25781 / 25782 ; H 25801 / 25802 / 25841 / 25842.
  (Dépôt principal : 25691 / 25692 / 25731 / 25732 ; régie vocale : 25790.)

## Interfaces livrées (à remplir par chaque chantier)

### H. Épreuves (livré par le chantier H)

**Actions ajoutées** (`show/Actions`, bloc « chantier H » ; outils Claude et phrases hors ligne dans
`tools/voice-regie`) — toutes annulables, `manche` absent = manche en cours :
- `result.set {manche?, player, value, kind?}` — `value` nombre ou texte (`"42"`, `"1:05"`, `"12,5"`, `"8/10"`) ;
  `kind` = `time | count | distance | mark | weight` (ou temps, nombre…) : change le type d'une feuille sans valeur,
  refusé si la feuille a déjà des valeurs d'un autre type. Effet `ResultRecorded` (annonce en barre d'action).
- `result.dq {manche?, player, dq?=true}` ; `result.remove {manche?, player}` ; `results.clear {manche?}`.
- `results.config {manche?, kind?, order? (lower|higher|auto), scale? ("5,4,3,2,1")}`.
- `results.apply {manche?, scale?}` — classe la feuille et fait un `score.set` par candidat classé (ex-aequo =
  même place et mêmes points, places au-delà du barème et disqualifiés = 0, sans valeur = score intact).
- `stopwatch.start {player}`, `stopwatch.stop {player, record?=true}` (le temps va en `result.set` kind time),
  `stopwatch.reset {player?}` — sans saison/partie. `buzzer.reset {}`.
- Effets ajoutés à `Actions.Effect` : `ResultRecorded`, `StopwatchStarted`, `StopwatchStopped`, `StopwatchReset`,
  `BuzzersReset` (joués par `trial/Judging.play`).

**Données** : `Manche.results` (`trial/ResultSheet`), YAML `results: {kind, order?, scale?, entries: {pseudo:
{value?, dq?}}}` sous la manche (absent si vide). Renommer un joueur renomme ses lignes.

**Pour les autres chantiers** :
- `fr.lordfinn.craftmaster.trial.Stopwatch.SHARED` (`peek/isRunning/last/find`) : chronos des candidats en cours
  (E peut les afficher dans l'habillage) ; `trial.Buzzers.LOCK.winner()/presses()`.
- `trial.TrialNetwork.Sheet` (S→C, JSON) : feuille de la manche (format dans la javadoc de `sheetJson`).
- `trial.TrialNetwork.RunActions` (C→S) : liste d'actions JSON exécutée par `ActionExecutor` pour un admin (comme la
  voix) — réutilisable par n'importe quel écran.
- `trial.TrialLog.add(server, line)` / `latest(n)` : journal des événements d'épreuve.
- Appel à D : `fr.lordfinn.craftmaster.task.Tasks.openPaper(ServerPlayer, String title, String text)` (note d'un
  prop). **H fournit un bouchon** du même nom (message dans le chat) : garder la classe de D à la fusion.
- Ajouts hors `trial/` : une ligne dans `CraftMaster`, `CraftMasterClient`, `ActionExecutor` (cas `play` +
  `TrialNetwork.refreshSheets` dans `refresh`), bloc dans `Actions`, champ dans `Manche`, bloc dans
  `gen_assets.py` (+ `scripts/gen_trials.py`), `command/TrialCommands.java`.
- Touche **R** (catégorie CraftMaster) : feuille de résultats (organisateurs).

### E. Réalisation TV (livré)

Guide organisateur : `docs/wiki/Realisation.md`. Paquets : `fr.lordfinn.craftmaster.broadcast.BroadcastNetwork`.

**Paquet « état de l'émission »** `craftmaster:show_state` (serveur → tout client qui a le mod, renvoyé dès que
quelque chose change, vérifié tous les 10 ticks, et à la connexion) — record `BroadcastNetwork.ShowState` :
`serverTick` (tick serveur à l'envoi), `season` (0 = aucune), `seasonName`, `seasonHidden`, `partie`, `partieName`,
`mancheId` ("" = aucune manche en cours), `mancheNumber`, `mancheName`, `timerName`, `timerEnd` (tick serveur de fin
du chrono, -1 sans chrono), `timerTotal` (ticks), `contestants[]` = `Contestant(name, points, rank, online)` (joueurs
non archivés de la saison active, **scores publiés uniquement**, rang 0 = non classé, tout à 0 si la saison est
cachée), `cameras[]` = `CameraSpot(number, name, role, world, x, y, z, yaw, pitch, fov, motion)` (position = objectif),
`shot` = `Shot(kind 0 aucun / 1 caméra / 2 suivi, camera, player, transition 0 coupe / 1 fondu / 2 enchaîné,
serial)`, `auto` (réalisation auto), `letterbox`, `scores` (bandeau 0 auto / 1 toujours / 2 jamais ; forcé à 2
pendant la révélation), `master` (présentateur pour les crédits), `sequence` ("" / "intro" / "outro").
Côté client : `fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.get()` (+ `timerSeconds()`, `cameras()`).

**Événements ponctuels** `craftmaster:show_cue` — `ShowCue(kind, title, subtitle, player, ticks)` ; kinds dans
`BroadcastNetwork.Cues` : `points` (player, title « +3 », subtitle = manche), `lower_third`, `scores`, `task`
(→ `CurrentTask.showCard(ticks)` sur les jeux en diffusion), `title_card`, `contestant`, `credits`, `theme`
(title "play" / "stop"), `clear`, `broadcast` (title "on" / "off", envoyé à un seul joueur par `/tv broadcast`).
Client → serveur : `craftmaster:broadcast_mode {on}` (le jeu passe en diffusion) et `craftmaster:camera_request
{json}` (une action `camera.*` / `broadcast.*` / `show.*`, exécutée si le joueur a `Permissions.CAMERA`).

**Actions ajoutées au schéma** (aucune ne modifie les données → rien à annuler ; vérifiées par
`broadcast.BroadcastActions`, jouées par `broadcast.Director` via l'effet `Actions.Broadcast`) :
`camera.cut {camera, transition?}` (camera = numéro, nom ou rôle `large` / `trone` / `podium`),
`camera.follow {player, transition?}`, `camera.auto {enabled}`, `camera.transition {transition}`
(`coupe` | `fondu` | `enchaine`), `broadcast.lowerthird {player? | text?, subtitle?, seconds?}`,
`broadcast.task {seconds?}`, `broadcast.scores {mode: show | auto | on | off, seconds?}`,
`broadcast.letterbox {enabled}`, `show.intro {}`, `show.outro {}`, `show.stop {}`. Toutes dans
`tools/voice-regie` (outils Claude ; hors ligne : « caméra 2 », « plan large », « plan sur le trône », « le podium »,
« suis X », « générique (de fin) », « réalisation automatique », « montre la tâche / les scores »), sauf
`camera.transition` (commande seulement). Le contexte de la régie gagne `cameras[] {number, name, role}`, `onAir`
("camera 2" / "follow Ensuri" / "none") et `autoDirection`.

**`ShowEvents`** (méthodes par défaut ajoutées à `Listener`) : `onScoreGiven(server, season, player, delta, manche)`,
`onMancheStarted(server, season, number, name)`, `onRevealStarted(server, season)` (appelés par `ActionExecutor`),
et le crochet générique **`ShowEvents.spotlight(MinecraftServer, String player, String reason)`** →
`onSpotlight(...)` : « tous les yeux sur ce joueur ». **Chantier D : à appeler quand un candidat ouvre son
enveloppe, avec `reason = "envelope"`** (la réalisation auto coupe sur lui avec le synthé « ouvre son enveloppe »).
`/tv spotlight <joueur> [raison]` le déclenche à la main.

**Contrat D utilisé** : `client.task.CurrentTask.get()` (titre de la tâche révélée dans le bandeau de manche) et
`CurrentTask.showCard(ticks)` (`/tv task`, « montre la tâche »). Le chantier E livre un **bouchon** de cette classe
(même API, `get()` vide, `showCard` journalise) : **à remplacer par la classe du chantier D à la fusion.**

**Autres points pour la fusion** : permission `craftmaster:camera` (`Permissions.CAMERA` = admin ou elle) ;
son `craftmaster:show.theme` (`sounds.json` généré par gen_assets.py, dictionnaire `SOUNDS` où les autres chantiers
ajoutent leurs sons) ; mixins client dans `craftmaster.client.mixins.json` (nouveau fichier) ; `Show` expose
`timerName()`, `timerEndTick()`, `timerTotalTicks()` ; le panneau de la régie vocale est masqué en diffusion.
Écarts avec la section E : la « présentation de chaque candidat avec son portrait » utilise le plan qui le suit
(connecté) ou le plan large, plus son visage sur la carte (pas les tableaux suspendus) ; le plan « réaction sur le
trône » est la caméra de rôle `trone` (ou `/cam follow LordFinn`).

### E (suite). Lieux et zones des caméras, travellings, intros de manche, écran partagé (livré)

Guide : `docs/wiki/Realisation.md` (« Les lieux », « Les travellings », « L'intro animée d'une manche »).

**Données** :
- `CameraSpot` gagne `lieu` ("studio" ou id de manche) et `zone` ("" ou nom de zone d'épreuve, toujours "" au
  studio). **Numérotation par groupe** (`group()` = "studio" | "pont" | "pont:ensuri") ; clé unique `key()` =
  "studio:2", "pont:ensuri:1". `cameras.json` : `version: 2`, champs `lieu` / `zone` ; un fichier sans eux = tout
  au studio ; une entrée illisible est sautée. Trépied (`CameraEntity`) : donnée synchronisée `Group` (NBT
  "Group", absent = studio), `cameraKey()`.
- Zones : **source de vérité = `trial.TrialZones`** (chantier H). Une zone de caméra est un nom de zone d'épreuve ;
  la « zone d'un joueur » = la zone d'épreuve où il se tient, sinon celle dont la liste `players` le contient, sinon
  l'étiquette à son nom (un nom sans zone d'épreuve reste accepté comme étiquette libre, signalé).
- Trajets : `camera.CameraPath(name, lieu, world, steps[Step(x,y,z,yaw,pitch,fov,travel,hold,curve)])`, échantillonné
  par `sample(t)` (Catmull-Rom centripète sur la position, Catmull-Rom sur yaw déroulé / pitch / fov, temps adouci
  par « course » entre deux pauses). `config/craftmaster/trajets.json` (`version: 1`, `paths[]`, champs manquants =
  défauts) via `camera.CameraPaths` ; hors journal d'annulation (comme les caméras).
- Intro : `data.MancheIntro` (`auto`, `music`, `titleCard`, `shots[Shot(kind camera|trajet|suivi, ref, seconds,
  transition, effect, card titre|synthe, text)]`) dans `Manche.intro`, YAML `intro:` sous la manche (absent si
  vide) : **annulable** avec le reste.

**Paquets** (`broadcast.BroadcastNetwork`) :
- `Shot` devient `Shot(kind, camera: String, player, transition, serial, effect, tiles[Tile(camera, label)])` —
  `camera` = clé de caméra (SHOT_CAMERA), nom de trajet (SHOT_PATH), lieu (SHOT_MOSAIC). Nouveaux genres :
  `SHOT_PATH 3`, `SHOT_MOSAIC 4`, `SHOT_POV 5` (yeux de `player`), `SHOT_FREE 6` (caméra libre).
- `ShowState` gagne `focus` (groupe visé par les touches) et `paths[]` ; `sequence` peut valoir "manche" (intro).
- `FreeCamera(x, y, z, yaw, pitch, fov, smoothing)` (`craftmaster:free_camera`, S→C, aux jeux en diffusion) : pose
  cible de la caméra libre, lissée côté client.
- `ShowCue` : `theme` porte en `subtitle` un id de son (vide = le thème) ; nouveau `path_preview` (title = trajet,
  ticks) envoyé à un seul organisateur (aperçu sur son écran).

**Actions** (`BroadcastActions`, sans données, jouées par `Director`) : `camera.cut {camera, transition?, effect?}`
(références : voir `CameraList.resolve(ref, focus, places)`), `camera.follow {player, transition?, effect?}` (choisit
aussi la zone du joueur), `camera.pov {player, …}`, `camera.path {path, transition?, effect?}`,
`camera.mosaic {lieu?, enabled?}`, `camera.focus {lieu? (studio | manche | auto), zone? (nom | joueur | next |
prev | tout), player?, cut?}`, `camera.lieu {camera, lieu, zone?}` (registre, non annulable).
**Actions de données** (`show/Actions`, bloc « round intros », annulables) : `intro.set {manche?, auto?, music?,
titleCard?}`, `intro.shot.add {manche?, camera | path | follow, seconds?, transition?, effect?, card?, text?, at?}`,
`intro.shot.set {manche?, index, …}`, `intro.shot.remove {manche?, index}`, `intro.clear {manche?}` ;
`intro.play {manche?}` → effet `Broadcast {type: intro.play, manche: id}` joué par `broadcast.MancheIntros`.
Régie vocale : outils `camera_path`, `camera_focus`, `camera_lieu`, `camera_mosaic`, `camera_pov`, `intro_play`,
`intro_set`, `intro_shot_add`, `intro_shot_remove` ; hors ligne : « caméra 2 du studio », « caméra 1 d'Ensuri »,
« zone d'Ensuri », « caméras de l'équipe bleue / du studio / du pont », « la caméra 3 est sur le pont »,
« lance le travelling du pont », « lance l'intro [du pont] », « écran partagé ». Contexte (`ShowContext`) :
`cameras[].key/lieu/zone`, `cameraFocus`, `paths[] {name, lieu, seconds}`, `manches[].intro {auto, shots[]}`.

**Foyer (focus)** : `Director.focus()` = groupe visé ; suit `Director.phaseLieu()` (intro de manche → sa manche ;
générique, révélation → studio ; manche en cours qui a des caméras → elle ; sinon studio) sauf choix de l'opérateur
(`setFocus`), oublié au changement de phase. Touches du client : 1–9 (focus), molette, **Tab** / Maj+Tab / Maj+molette
(zone suivante / précédente, `key.craftmaster.broadcast_zone`), **0** (écran partagé, `key.craftmaster.broadcast_mosaic`).

**Point d'entrée pour une réalisation automatique plus intelligente (chantier à venir)** :
- tout plan passe par `Director.take(server, ShotRequest(kind, camera, player, transition, effect, tiles, source))`
  (`Source.MANUAL | AUTO | SEQUENCE` ; une demande AUTO est ignorée pendant un générique / une intro) ;
- le choix automatique est un `Director.Planner` (un `ShowEvents.Listener` + `tick(server)` appelé chaque tick
  quand la réalisation auto est active) ; `Director.setPlanner(p)` remplace le planificateur par défaut ;
- requêtes : `camera.Lieux.zoneOf(server, player, lieu)` (groupe de caméras qui filme ce joueur),
  `Lieux.camerasFor(server, player, lieu)` (ses caméras, la plus proche d'abord), `Lieux.playersOf(zone)`,
  `Director.focus() / focusLieu() / phaseLieu() / wideSpot() / mosaicTiles(lieu)`, `Director.shot()` ;
- plans dynamiques : `SHOT_POV` (vue d'un joueur), `SHOT_FREE` + `Director.moveFree(server, FreeCamera)` (caméra
  libre lissée côté client : quelques mises à jour par seconde suffisent).

**Effets d'image (autre chantier)** : chaque plan porte un id d'effet ; le client appelle
`fr.lordfinn.craftmaster.client.fx.ScreenEffects.setBroadcastEffect(idOuNull)` à chaque changement de plan (et null
hors diffusion). **Ce chantier livre un bouchon** de cette classe (ne fait rien) : à remplacer par la vraie classe à
la fusion.

**Écarts / choix** : numérotation par groupe plutôt que globale (une touche = un numéro peint sur un trépied de la
salle choisie ; au-delà de 9 caméras par salle il faut une clé) ; pas de zone au studio ; les trajets et le registre
des caméras restent hors annulation (réglages du monde) ; l'écran partagé rend une vignette par image à tour de rôle
(pas de second rendu du monde par image), le « smart cull » du client est coupé pendant l'écran partagé.
**Fichiers hors de camera/ et broadcast/** : `Manche` (champ `intro`), `data/MancheIntro` (nouveau), `show/Actions`
(types + bloc intros), `show/ShowContext`, `command/CameraCommands`, `registry/ModContent` (`PATH_MARKER`),
`CraftMaster` (chargement de `trajets.json`, tick des lignes de trajet), mixins client `CameraMixin`,
`GameRendererMixin`, `HandsRendererMixin`, `client/fx/ScreenEffects` (bouchon), `scripts/gen_assets.py` (bloc
« chantier E, second part »), `scripts/server_smoke_test.py` (port RCON en 2e argument, bloc TV), `tools/voice-regie`.

### D. Tâches, enveloppes, notes cachées (livré)

**Données** : `Manche.task` (`fr.lordfinn.craftmaster.data.Task` : `title`, `text` — paragraphes séparés par
`\n\n` —, `rules`, `seconds` — 0 = `Manche.timer`, voir `shownSeconds(Manche)` —, `wax`, `revealOnStart`), clé
`task` dans `config.yml`, absente = pas de tâche (fichiers v3 lus tels quels). Les **révélations** et les **notes**
sont dans `config/craftmaster/tasks.json` (`fr.lordfinn.craftmaster.task.TaskStore`), hors journal d'annulation.

**Actions** (`Actions`, effets `Actions.TaskEffect` joués par `Tasks.play`) :
- `task.set {manche, title?, text?, append?, rules?, seconds?, wax?, revealOnStart?}` — seuls les champs donnés
  changent ; `append: true` ajoute `text` en nouveau paragraphe ; `rules` liste ou texte « a | b » ; `wax` en anglais
  ou en français (rouge, or…).
- `task.clear {manche}`.
- `task.reveal {manche?}` (annoncé à tous) · `task.hide {manche?}` · `task.show {manche?, seconds?=12}` (révèle
  puis la carte sur tous les écrans) · `envelope.give {manche?, players?, wax?}` (`players` absent ou `tous` = les
  candidats connectés). `manche` absent = manche en cours.
- `manche.start` révèle la tâche de la manche si elle en a une et `revealOnStart` (effet `TaskRevealed`).
- Régie vocale : outils `task_set`, `task_clear`, `task_reveal`, `task_hide`, `task_show`, `envelope_give` ; hors
  ligne : « révèle la tâche », « montre la tâche », « donne les enveloppes ».
- Contexte (`ShowContext`) : `manches[].task {title, wax, revealed}` (seulement si une tâche est écrite).

**Serveur** (`fr.lordfinn.craftmaster.task`) :
- `Tasks.reveal(MinecraftServer, String mancheId, ServerPlayer by)` → `boolean` (false : pas de telle manche avec une
  tâche dans la saison active, ou déjà révélée). `mancheId` : id, nom ou surnom. Synchronise tous les clients.
- `Tasks.openPaper(ServerPlayer, String title, String text)` : ouvre la carte papier (sans sceau de manche) chez le
  joueur ; dans le chat si son jeu n'a pas le mod. Pour les objets d'épreuve de H.
- `Notes.open(ServerPlayer, String noteId)` → `boolean` : ouvre une note comme trouvée (lecteurs respectés,
  trouvaille notée « avec un objet d'épreuve », admins prévenus). Pour H aussi.
- `Tasks.openCurrent(ServerPlayer)` (touche J / `/tache`), `Tasks.locateActive(String)`, `Tasks.isRevealed(Located)`.
- `ShowEvents.Listener` : `onTaskRevealed(server, season, mancheId, ServerPlayer by)` (by = l'ouvreur de
  l'enveloppe, ou null : lancement de manche, organisateur) et `onEnvelopeOpened(server, player, season, mancheId)`
  (`mancheId` vide = enveloppe de texte libre), appelé à chaque ouverture d'une enveloppe scellée — pour la
  réalisation automatique de E (couper sur celui qui ouvre).

**Client** (`fr.lordfinn.craftmaster.client.task`) :
- `CurrentTask.get()` → `Optional<Info>`, `record Info(String mancheId, String mancheName, int mancheNumber,
  String title, String text, List<String> rules, int seconds, boolean revealed)`. « Tâche du moment » = la manche en
  cours si elle a une tâche, sinon la dernière révélée (les admins : entre deux manches, la prochaine qui a une
  tâche). Pour un non-admin, une tâche secrète arrive **sans texte** (`revealed=false`, titre / texte / règles vides).
  En plus : `CurrentTask.wax()`, `CurrentTask.admin()`, `CurrentTask.cardShown()`, `CurrentTask.hideCard()`.
- `CurrentTask.showCard(int ticks)` : la carte glisse depuis la droite (≈ 220 px de large, centrée verticalement,
  défilement lent si longue), reste `ticks`, ressort. **Ne montre jamais un texte secret**, même chez un admin
  (« Tâche encore secrète »). Dessinée par `HudElementRegistry` (`craftmaster:task_card`) : E peut la masquer en mode
  diffusion et la redessiner dans son habillage via `PaperView` (`new PaperView(font, paper, 1.5f)`, `layout(w)`,
  `draw(g, x, y, w, h, scroll, alpha, textAlpha)`).
- Paquets (`TaskNetwork`) : `task_state` (tâche du moment), `task_paper`, `task_show_card`, `task_close_card`,
  `task_open_editor`, `task_open_note` (serveur → client) ; `task_request`, `task_run_actions`, `task_save_note`,
  `task_note_order`, `task_seal` (client → serveur, admin vérifié).
- Touche `key.craftmaster.task` (J, catégorie CraftMaster). Twitch : `config/craftmaster-client.json`
  (`twitch.enabled/channel/commands/cooldownSeconds/showSeconds`), `/craftMasterTwitch [reload|test]`.

**Lettre de tâche (v4.1)** : l'écran de lecture d'une tâche / d'un texte libre est `client.task.LetterScreen`
(lettre pliée en trois scellée à la cire : sceau à arracher à la souris, au clic ou Espace maintenu ; dépliage haut
puis bas puis volets en plus, zoom arrière ; pixel art à l'échelle entière ; police `craftmaster:typewriter`).
Logique pure testée : `task.LetterMotion` (géométrie, calendrier, échelle, sceau), `task.TaskMarkup` (mise en
forme : `#`, `---`, `**`, `__`, `~~`, `{rouge}…{/}`, `>`, `!règle`, `[tampon: …]`, `[signature: …]`, variables,
`[secret]…[/secret]`). La révélation et l'annonce d'une enveloppe attendent maintenant le paquet `task_seal_broken`
(client → serveur, quand le sceau saute ; 45 s au plus) ; `onEnvelopeOpened` reste appelé au clic droit (la caméra
coupe sur le lecteur pendant qu'il arrache le sceau). Nouveautés : action `task.reveal_more {manche?}` (effet
`TaskSecretShown`, outil vocal `task_reveal_more`, « révèle la suite »), `/tache suite [manche]`,
`/tache apercu <manche>` (`Tasks.preview`), drapeau `TaskNetwork.PREVIEW`, paquet `task_paper_update` (une lettre
ouverte prend le nouveau texte). Les parties `[secret]` sont caviardées côté serveur (`Tasks.shown`,
`TaskMarkup.redact`) dans `task_paper` et `task_state` : `CurrentTask.get().text()` peut contenir `[secret:N]`
(non-admin) ou `[secret]…[/secret]` (admin) ; `TaskMarkup.parse` / `plain` les traitent.

**Contenu** : objets `craftmaster:envelope` (composant `craftmaster:envelope`), `wax_seal`, `note` / `note_bottle`
(composant `craftmaster:note`), `uv_lamp` ; blocs `envelope_block` (+ entité de bloc), `pinned_note`.

**Écarts / choix** : révélations et trouvailles hors annulation (événements du direct) ; la carte de surimpression
respecte toujours le secret ; « tâche du moment » définie ci-dessus ; les notes ne passent pas par `ActionExecutor`
(objets du monde, comme les tableaux) mais par leurs commandes et leur éditeur ; cachettes : sous un tapis / une
dalle, dans un livre, derrière un tableau ou un cadre, double fond, dedans, feuille épinglée, bouteille, plus encre
invisible (lampe UV) et morceaux déchirés.

### F. Décor du studio (livré)
- **Blocs / objets** (`fr.lordfinn.craftmaster.studio.Studio`, champs publics) : `THRONE`, `ASSISTANT_CHAIR`, `SOFA`
  (sièges = `ChairBlock`, donc `SeatEntity`), `FLOOR_LAMP`, `DESK_LAMP`, `CEILING_PENDANT`, `SPOTLIGHT`,
  `ON_AIR_SIGN`, `TIMER_DISPLAY`, `CONTESTANT_DESK`, `DESK_BELL`, `GONG`, `PRIZE_TABLE`, `PODIUM_FIRST/SECOND/THIRD`,
  `TROPHY`, `STANCHION`, objet `STUDIO_SCREEN_ITEM`, entité `STUDIO_SCREEN` (`ImageScreenEntity`). Détail :
  `docs/wiki/Plateau.md`.
- **Actions** (bloc « Studio set » de `Actions` / `ActionExecutor`, et `tools/voice-regie`) : `spotlight.follow
  {player}` (pseudo ou surnom), `spotlight.stop {}`, `spotlight.on {}`, `spotlight.off {}`. Effet
  `Actions.SpotlightOrder(order, player)` ; rien ne change dans les données (pas d'entrée d'annulation).
- **Projecteurs** (pour E, réalisation automatique) : `studio.Spotlights.follow(server, player)`, `stop(server)`,
  `setLit(server, boolean)`, `color(server, DyeColor)` → ligne française ; `SpotlightBlockEntity.loaded()`.
- **Gong** : `GongBlock.strike(ServerLevel, BlockPos, Player|null)` (son + balancement) ; accroupi par un admin =
  action `manche.end`.
- **Paquet** `craftmaster:studio_timer` (S→C) `StudioNetwork.TimerState(running, remaining (-1 = aucun), duration,
  manche, number)`, envoyé au changement et à la connexion ; côté client `client.studio.StudioTimerClient.view()`
  → `View(clock, seconds, mode RUNNING|TIME_UP|READY|IDLE, title)`. E peut le réutiliser pour son bandeau (ou le
  remplacer par son paquet « état de l'émission » : seul `StudioTimer` l'envoie, `StudioTimerClient.receive` le lit).
- **Écrans** : paquets `studio_screen_open` (S→C, id d'entité) et `studio_screen_save` (C→S) ; client
  `client.studio.ScreenImages.get(url)` (téléchargement asynchrone, cache mémoire + `craftmaster/image-cache/`)
  réutilisable pour d'autres images d'adresse (H : images d'épreuve).
- **Permission** `Permissions.IMAGE` / `canShowImages(player)` = `craftmaster:image` ou admin.
- **Sons** : `Studio.GONG_SOUND` (`craftmaster:studio.gong`), `Studio.DESK_BELL_SOUND` (`craftmaster:studio.desk_bell`).
  `scripts/gen_assets.py` a maintenant un dictionnaire commun `SOUNDS` écrit en `sounds.json` : y ajouter ses sons
  (E : `show.theme`) au lieu d'écrire `sounds.json` soi-même.
- **Changements hors de studio/** : `ChairBlock.seatTop()` (hauteur d'assise par siège, lue par `SeatEntity`),
  `Shapes4` public, `ModContent.block()/item()` publics, `Studio.register()` dans `CraftMaster`,
  `StudioClient.init()` dans `CraftMasterClient`, `Permissions.IMAGE`, le tag `mineable/axe` est réécrit par
  `gen_studio.py` (chaises + bois du studio), nouveau tag `mineable/pickaxe`.
- **Écarts** : le trône et le lampadaire font 2 blocs de haut dans un seul bloc (modèle plus haut que le bloc,
  contour jusqu'à 16 px pour le trône, 31 px pour le lampadaire) ; l'enseigne « EN DIRECT » est plus large que son
  bloc. Le chrono géant suit le chrono de l'émission sans aucun réglage (pas de chrono propre).

### G. Finitions (rideau fluide, éditeur de poses, skins HD)

- **Rideaux** : aucun changement d'API. `Curtains.animate(ServerLevel, BlockPos, Boolean)` fait la même chose
  (états des blocs colonne par colonne, sons) et envoie en plus `CurtainNetwork.Slide(pos, open, ticks)`
  (`craftmaster:curtain_slide`, serveur → joueurs qui suivent le tronçon). Nouveau : `Curtains.group(BlockGetter,
  BlockPos)` (les blocs d'un rideau, même règle des deux côtés), `Curtains.column`, `STEP_TICKS` et `MAX_BLOCKS`
  publics. `CurtainBlock` est un `EntityBlock` (`ModContent.CURTAIN_BLOCK_ENTITY`, `craftmaster:curtain`, sans
  données) au rendu `INVISIBLE` : le tissu est dessiné par `client/curtain/CurtainRenderer`. Un décor (chantier F)
  qui pose des rideaux par `setBlock` n'a rien à faire de plus. `PortraitClock` (horloge lisse du client) est public.
- **Éditeur de poses** : pas de nouvelle action (une pose s'enregistre toujours par `PortraitNetwork.SavePose`).
  Logique pure réutilisable : `portrait/PoseEditing` (miroir, copie symétrique, remise à zéro, rotation par
  glisser), `PoseHistory` (annuler / refaire avec fusion des gestes), `PoseHitTest` (`View` orthographique et
  `pick`). `PortraitRenderer.previewState(pose, skin)` + `GuiGraphicsExtractor.entity(...)` dessinent un tableau
  dans un écran.
- **Skins** : `PortraitSkins.Skin(texture, slim, cape)` (cape nullable) ; `portrait/SkinFormat` (64·k, k ≤ 16).
- **Tests d'écrans** : `client/DevInput` (dev uniquement) et l'étape `!input` de `scripts/mcp_session.py` ; utile
  aux autres chantiers pour tester leurs écrans sans souris.

### Effets d'écran (livré)

Guide : `docs/wiki/Effets.md`. Effets (ids) : `noir_blanc`, `sepia`, `pixel`, `fisheye`, `vignette`, `masque_rond`,
`citrouille`, `flou`, `vhs`, `vieux_film`, `infrarouge`, `reve`, `ondule`, `jumelles`, `bd`, `negatif` ; combinaisons
`a+b` (6 au plus, dessinées dans l'ordre fixe de `fx.ScreenFx.ORDER`), préréglages `judas`, `camera_cachee`,
`souvenir`, `film_muet`, `cauchemar`, alias français (`fx.ScreenFx.parse`).

**API client (pour les intros / travellings)** : `fr.lordfinn.craftmaster.client.fx.ScreenEffects.setBroadcastEffect(
String idOrNull)` (fondu 0,6 s ; `setBroadcastEffect(String, float fadeSeconds)`), `broadcastEffect()`. Ne s'affiche
que sur un jeu en mode diffusion ; même emplacement que `/fx diffusion` (le dernier ordre gagne). Hors diffusion, la
vue d'un joueur combine l'effet imposé par le serveur, celui de son objet de tête et l'essai `/essaifx`.
Rendu : post effects `craftmaster:fx/<id>` appliqués au monde avant le HUD (l'habillage reste net) ; `citrouille`
est la superposition vanille `textures/misc/pumpkinblur.png` dessinée par `Hud.extractTextureOverlay` (élément HUD
`craftmaster:screen_fx_overlay` juste après les superpositions vanille, sous l'habillage).

**Actions** (bloc « Screen effects » de `Actions`, effet `Actions.ScreenEffect(target, effect, seconds, fadeTicks)`
joué par `fx.ScreenEffectsServer.set`, **pas d'entrée d'annulation** : c'est l'image du direct, comme les plans) :
`fx.set {target? = "diffusion" | "tous" | "candidats" | pseudo (ou surnom), effect, seconds?, fade? (s)}`,
`fx.clear {target? = "tout"}`. Régie vocale : outils `fx_set` / `fx_clear` ; hors ligne « passe la diffusion en
noir et blanc », « mets Ensuri en flou », « enlève les effets [de X] ».

**Paquet** `craftmaster:screen_fx` (S→C) `fx.FxNetwork.Order(layer 0 joueur / 1 diffusion, effect, fadeTicks,
ticks)` ; le serveur garde l'ordre en cours et le renvoie à la connexion.

**Objets** (`fx.FxContent`, onglet CraftMaster) : composant `craftmaster:screen_fx` (String, sur n'importe quel
objet de tête) ; `blurry_glasses`, `sepia_glasses`, `noir_blanc_glasses`, `pixel_glasses`, `peephole_glasses`,
`vintage_glasses`, `vhs_headset`, (pas de masque citrouille : la citrouille sculptée vanille suffit),
`thermal_goggles`, `heart_glasses`, `wobbly_glasses`, `binocular_goggles` (modèle 3D sur la tête par
`select display_context = head`). Serveur : `ScreenEffectsServer.wear(player, item, lock)` / `takeOff(player)`
(verrou = malédiction du lien, sans reflet).

**Hors de fx/** : une ligne dans `CraftMaster`, `CraftMasterClient`, `ActionExecutor` (cas `ScreenEffect`), bloc
dans `Actions`, `command/FxCommands`, 6 mixins client (`FxGameRendererMixin`, `FxPostChainMixin`, `FxCameraMixin`,
`PostChainAccessor`, `PostPassAccessor`, `HudInvoker`) dans `craftmaster.client.mixins.json`, bloc dans
`gen_assets.py` (+ `scripts/gen_fx.py`), section « fx » de `scripts/server_smoke_test.py` (port RCON en 2e
argument).
