# Replays des manches (Server Replay)

Si **Server Replay** (senseiwells, MIT) est installé sur le serveur, CraftMaster enregistre tout seul chaque manche,
ou chaque partie : un fichier par candidat (ce que son jeu a reçu : sa vue, son HUD) et un fichier pour **la zone de
l'épreuve** (des chunks fixes : tous les joueurs qui y sont, vus de n'importe où). Sans Server Replay, rien ne change
et rien ne casse.

## Installer
Sur le serveur seulement : `ServerReplay-3.5.2+26.2.jar` et sa dépendance `fabric-language-kotlin` (Modrinth).
Les joueurs n'ont rien à installer. Au démarrage, le journal du serveur dit
« [Replay] Server Replay trouvé : enregistrement automatique des manches possible ».

## Ce qui est enregistré
- **Quand** : `/cmreplay mode manche` (défaut) : du lancement de chaque manche à sa fin (`/cfma end`, ou la manche
  suivante). `mode partie` : de la première manche d'une partie jusqu'à ce qu'une autre partie devienne active ou
  qu'elle soit terminée (`/craftMasterPartie status 1 terminee`), entre les manches compris. `mode off` : rien
  d'automatique, `/cmreplay start` et `/cmreplay stop` à la main.
- **Qui** : les candidats de la saison (joueurs non archivés) connectés. Un candidat qui arrive en cours de route est
  ajouté ; s'il se déconnecte, Server Replay ferme son fichier, et un nouveau commence (même dossier) à son retour.
  Un joueur déjà enregistré par `/replay start players` est laissé tel quel.
- **La zone** : les caméras de la manche (et les zones d'épreuve qu'elles regardent), ses points de TP ; sans rien de
  tout ça, autour des candidats au lancement. Plus une marge de 2 chunks (`/cmreplay marge <n>`) ; 40 chunks de côté
  au plus. La caméra du replay démarre sur le plan large de la manche.
- **En plus de Minecraft** : l'état de l'émission (plan à l'antenne, caméras, chrono, scores publiés, réalisation
  automatique…) au début de chaque fichier puis à chaque changement, les habillages (synthés, points en direct…) et
  la caméra libre. Et des **repères** sur la timeline : début et fin de manche, points donnés (« Ensuri +3 »),
  enveloppes ouvertes, projecteur, buzzers, tâche révélée, scores publiés, révélation ; `/cmreplay repere <texte>`
  en ajoute un.

## Les fichiers
Dans les dossiers de Server Replay (`recordings/` du serveur par défaut), un dossier par enregistrement :

| Dossier | Contenu |
|---|---|
| `recordings/chunks/cm-s1-p3-m2-le-pont/<date-heure>.mcpr` | La zone de la manche 2 « Le Pont », partie 3, saison 1 |
| `recordings/players/cm-s1-p3-m2-le-pont-ensuri/<date-heure>.mcpr` | La vue d'Ensuri pendant cette manche |
| `recordings/chunks/cm-s1-p3/…`, `players/cm-s1-p3-ensuri/…` | Mode partie : toute la partie 3 |
| `recordings/craftmaster.json` | L'index : chaque enregistrement, ses dossiers, sa zone, ses repères (secondes) |

**Format** : Flashback (`.zip`) par défaut ; `/cmreplay format replaymod` ou `serveur` (celui de la configuration de
Server Replay, `config/server-replay/config.json`, `default_encoding`) pour changer. Il ne faut pas le mod Flashback
pour regarder : le lecteur est sur le serveur, rien à installer chez les joueurs, aucun conflit avec WaterMedia.

**Épreuves longues (une heure et plus)** :
- **Morceaux** : un fichier Flashback est écrit sur le disque par morceaux, chacun avec le monde au départ. CraftMaster
  ferme un morceau **toutes les 10 s pour la zone** et **toutes les 60 s pour les fichiers des joueurs**
  (`/cmreplay morceaux <zone s> <joueurs s>`, de 10 à 300). Un saut va directement au bon morceau, dans les deux sens.
- **Crash ou arrêt brutal** : au redémarrage, Server Replay récupère seul les fichiers jusqu'au dernier morceau écrit
  (testé : serveur tué à 1:36, fichier de zone récupéré jusqu'à 1:28). L'index est réécrit toutes les 10 s : le replay
  apparaît dans `/cmreplay voir` avec « (interrompu) ».
- **Chrono et synchro** : toutes les 10 s, chaque fichier note où en sont les ticks du serveur. Le chrono rejoué et le
  passage d'un fichier à l'autre suivent ces relevés : pas de dérive même si le serveur rame (sans eux : environ 1 s
  par heure à 20 TPS, plus si ça rame).
- **Poids** (morceaux de 10 s pour la zone, 60 s pour les joueurs) : la zone environ 15 Mo/h sur le disque ; un
  fichier joueur environ 1 à 2 Mo/h avec des morceaux de 5 min, nettement plus avec des morceaux courts (mesuré :
  environ 50 Mo/h à 10 s, et son cache de chunks grossit vite pendant l'enregistrement).
- **Disque** : `/cmreplay disque` (place prise, place libre), alerte aux organisateurs sous 5 Go libres
  (`/cmreplay alerte <Go>`), purge automatique des vieux replays (`/cmreplay garder <jours>`, `/cmreplay max <Go>` :
  les plus anciens partent d'abord ; rien n'est supprimé tant que ce n'est pas réglé), ou tout de suite
  (`/cmreplay purge <jours>`). Seuls les fichiers des enregistrements de l'index sont supprimés.

**Mesures** (26.2, serveur à 20 TPS, 2 joueurs + zone) :

| | ReplayMod, 12 min | Flashback, 10 min |
|---|---|---|
| Joueur qui se déplace (sur le disque) | 1,64 Mo | 0,20 Mo |
| Autre joueur | 0,47 Mo | 0,21 Mo |
| Zone | 0,24 Mo | 0,15 Mo |
| Saut de 9–11 min en avant | 0,3 s | 0,2 s |
| Retour en arrière | rechargement, 2–4 s | instantané |
| Serveur tué en pleine manche | non testé | récupéré au redémarrage, sauf la fin du morceau en cours (≤ 5 min) |

Soit environ 1 à 1,5 Mo par heure et par fichier en Flashback. L'écart entre l'horloge des fichiers et les ticks du
serveur a été de 0,2 s au plus en 12 min.

## Regarder (la régie de replay)
Sur le serveur qui a les fichiers, sans rien installer de plus chez le joueur : `/cmreplay voir` liste les
enregistrements (le plus récent en premier), un clic sur `[zone]` ou sur un candidat lance la lecture
(`/cmreplay voir <n> [zone|joueur]` à la main). Le lecteur est celui de Server Replay : pendant la lecture tu sors du
monde réel, tu n'y reviens qu'à la fermeture.

Avec CraftMaster sur ton jeu, le replay passe en **mode diffusion**. L'image reste celle des viewers : à l'écran, rien
d'autre qu'une **fine ligne de temps** en bas (dans les couleurs de l'habillage, le temps à gauche, la durée à droite,
les repères en petits traits de couleur) qui n'apparaît que **3 s après un ordre** et pendant une pause, et une
courte légende de ce que l'ordre a changé (« ×2 », « Caméra 2 », « Écran d'Ensuri »). **H** l'enlève tout à fait. Le
badge « EN DIRECT » devient « REPLAY ».

**Piloter** : à la voix, au Stream Deck ou au clavier ; pas de boutons à l'écran.

| À la voix | Touche | Stream Deck (`/replay/…`) |
|---|---|---|
| « pause » · « reprends » | **B** | `toggle` · `play` · `pause` |
| « recule de 10 secondes » · « avance de 30 secondes » | **←** **→** (Maj : 30 s) | `back?s=10` · `forward?s=30` |
| « va à 1 minute 20 » · « au début » | | `goto?s=80` · `start` |
| « vitesse 2 » · « au ralenti » · « vitesse normale » · « plus vite » | **↑** **↓** | `speed?x=2` · `faster` · `slower` |
| « repère suivant » · « repère précédent » | **Page suiv.** / **Page préc.** | `marker/next` · `marker/prev` |
| « caméra 2 » · « plan large » | **1–9**, molette · **0** | `camera?camera=2` (ou `/camera/cut`) |
| « suis Ensuri » · « vue d'Ensuri » | **Tab** · **Maj+Tab** | `follow?player=Ensuri` · `pov?player=Ensuri` |
| « l'écran d'Ensuri » · « la zone » | | `view?name=Ensuri` · `view?name=zone` |
| « les plans du direct » · « réalisation auto » · « caméra libre » | **G** | `mode?m=direct\|auto\|manuel\|libre` (ou `/camera/auto`) |
| « ferme le replay » | | `close` |

À la souris : **M** (ou `/cmbarre`) donne la souris sur la ligne de temps, et rien d'autre : un clic y saute, le
survol d'un repère dit son nom, la molette avance ou recule de 5 s ; M ou Échap rend la souris au jeu. Les touches
sont dans les contrôles, catégorie CraftMaster. Pendant un replay la voix ne passe pas par le serveur (le lecteur ne
laisse passer que des commandes) : le jeu comprend et exécute lui-même ; les autres ordres (points, manches…)
répondent « pas pendant un replay ».

**Qui réalise** :
- **Direct** (par défaut) : les plans choisis en direct, tels qu'enregistrés (coupes, suivis, transitions,
  habillage). Là où le direct n'avait pas de plan, la réalisation auto.
- **Auto** : recalculée à partir des repères, comme la réalisation automatique du direct : qui marque est suivi 7 s,
  qui est sous le projecteur (enveloppe, buzzer, projecteur) 9 s, sinon le plan large.
- **Manuel** : tes touches, sur ce jeu seulement.
- **Libre** : ta propre caméra de spectateur (mode diffusion coupé).

**Changer de fichier** (« l'écran d'Ensuri », « la zone ») : la zone, ou ce que le jeu d'un candidat a reçu (sa vue, son
interface). Le fichier est rouvert **au même instant** : les fichiers d'un enregistrement démarrent à quelques
dixièmes de seconde d'écart, CraftMaster note le tick de départ de chacun dans l'index et recale. La pause et la
vitesse sont gardées. Il faut 2 à 4 s (le monde est rechargé).

**Le chrono** du bandeau est celui du direct, à la seconde, à toute vitesse et après un saut (calculé sur les ticks
du serveur). Pour les enregistrements faits avant le 13/11/2026 (sans tick de départ dans l'index), il est compté
sur l'horloge du replay : juste à vitesse normale, approximatif après un saut.

Sans CraftMaster chez celui qui regarde, la lecture marche quand même (commandes de Server Replay :
`/replay view pause`, `unpause`, `speed 2`, `restart`, `close`), sans barre ni réalisation.

**ReplayMod** : copier le `.mcpr` dans `replay_recordings/` du jeu. Les repères apparaissent sur sa timeline (sans
leurs couleurs : le format n'en garde pas). Flashback lit aussi ces fichiers si on choisit son format, mais son FFmpeg
casse WaterMedia (écrans vidéo du studio) : pas les deux dans le même jeu.

## Commandes (`/craftMasterReplay`, alias `/cmreplay`, organisateurs)
| Commande | Effet |
|---|---|
| `/cmreplay` | Server Replay trouvé ?, mode, ce qui s'enregistre en ce moment |
| `/cmreplay mode manche\|partie\|off` | Enregistrement automatique de chaque manche, chaque partie, ou jamais |
| `/cmreplay start` · `stop` | À la main : la manche en cours, sinon la partie active ; `stop` sauvegarde et n'est pas relancé avant la manche suivante |
| `/cmreplay joueurs on\|off` · `zone on\|off` | Un fichier par candidat / pour la zone (défaut : les deux) |
| `/cmreplay marge <0..8>` | Chunks ajoutés autour de la zone |
| `/cmreplay format flashback\|replaymod\|serveur` | Format des prochains enregistrements (Flashback par défaut) |
| `/cmreplay morceaux <zone s> <joueurs s>` | Longueur des morceaux : au plus ça de perdu après un crash (défaut 10 et 60) |
| `/cmreplay disque` | Place prise par les replays, place libre, réglages de purge |
| `/cmreplay garder <jours>` · `max <Go>` · `alerte <Go>` | Purge automatique par l'âge, plafond, alerte de place libre (0 = aucun) |
| `/cmreplay purge <jours>` | Supprime maintenant les replays de plus de tant de jours |
| `/cmreplay repere <texte>` | Un repère maintenant, dans tous les fichiers en cours |
| `/cmreplay voir` · `voir <n> [zone\|joueur]` | Les enregistrements ; en regarder un (barre de lecture, voir plus haut) |

Réglages : `config/craftmaster/replays.json`.

## Limites connues
- **La taille** : voir les mesures plus haut. En ReplayMod, l'enregistrement en cours est écrit non compressé
  (environ 30 Mo pour 12 min et 3 fichiers) puis compressé à la fin.
- **Les caméras** ne montrent que ce qui est dans le fichier : une caméra qui filme hors de la zone enregistrée (le
  studio pendant une manche dehors) ne voit que le ciel. Agrandir la zone : `/cmreplay marge`.
- **Revenir en arrière** en ReplayMod recharge le fichier (2 à 4 s, l'écran de chargement passe à l'image) : le
  lecteur de Server Replay repart du début. En Flashback, c'est instantané.
- Après un arrêt brutal, la manche est toujours « en cours » au redémarrage : un enregistrement repart dès que le
  serveur est lancé (le gel du show après un crash est un chantier à part).
- Le lecteur retire le joueur de la liste du serveur pendant la lecture : un serveur où il est seul se croit vide
  (« Server empty… pausing » au bout d'une minute) ; la lecture et la barre continuent quand même.
- Server Replay ne garde dans la zone que ce que le serveur envoie à tout le monde : barres de boss propres à un
  joueur (le chrono), titres envoyés à un seul joueur… ne sont que dans le fichier de ce joueur.
- Un joueur qui se déconnecte en pleine manche a deux fichiers dans son dossier : la régie ouvre celui qui finit le
  plus près de la fin de l'enregistrement.
- Des barres de boss enregistrées sans leur création (un fichier joueur commencé pendant le chrono) faisaient planter
  le jeu de celui qui regarde : CraftMaster les ignore maintenant de son côté, et écrit leur création au début des
  nouveaux fichiers.
