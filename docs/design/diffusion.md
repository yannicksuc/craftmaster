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
