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

Format ReplayMod (`.mcpr`) par défaut ; le format se change dans la configuration de Server Replay
(`config/server-replay/config.json`, `default_encoding`), comme les dossiers, les paquets ignorés, la taille maximale.

## Regarder (la régie de replay)
Sur le serveur qui a les fichiers, sans rien installer de plus chez le joueur : `/cmreplay voir` liste les
enregistrements (le plus récent en premier), un clic sur `[zone]` ou sur un candidat lance la lecture
(`/cmreplay voir <n> [zone|joueur]` à la main). Le lecteur est celui de Server Replay : pendant la lecture tu sors du
monde réel, tu n'y reviens qu'à la fermeture.

Avec CraftMaster sur ton jeu, le replay passe en **mode diffusion** et une **barre de lecture** s'affiche en bas
(lecture / pause, temps, vitesse, ce que tu regardes, qui réalise, et la timeline avec les repères en couleur). Elle
reste à la même place quel que soit le plan ou le fichier ; en mode diffusion elle s'efface au bout de 4 s sans
toucher au replay (sauf en pause), **H** la cache.

| Touche | Effet |
|---|---|
| **B** | Lecture / pause |
| **←** **→** | 5 s en arrière / en avant (Maj : 30 s) |
| **↑** **↓** | Plus vite / moins vite (×0,25 à ×8) |
| **Page préc.** / **Page suiv.** | Repère précédent / suivant |
| **G** | Réalisation : Direct → Auto → Manuel → Libre |
| **1–9**, molette | Caméra du lieu (passe en Manuel) |
| **0** | Plan large |
| **Tab** (Maj+Tab) | Suivre le candidat suivant (le voir par ses yeux) |
| **M** ou `/cmbarre` | Le panneau à la souris : clic sur la timeline pour sauter, survol d'un repère pour son nom, boutons pour tout le reste, dont **changer de fichier** |

Les touches sont dans les contrôles, catégorie CraftMaster.

**Qui réalise** :
- **Direct** (par défaut) : les plans choisis en direct, tels qu'enregistrés (coupes, suivis, transitions,
  habillage). Là où le direct n'avait pas de plan, la réalisation auto.
- **Auto** : recalculée à partir des repères, comme la réalisation automatique du direct : qui marque est suivi 7 s,
  qui est sous le projecteur (enveloppe, buzzer, projecteur) 9 s, sinon le plan large.
- **Manuel** : tes touches, sur ce jeu seulement.
- **Libre** : ta propre caméra de spectateur (mode diffusion coupé).

**Changer de fichier** (panneau, « Fichier ») : la zone, ou ce que le jeu d'un candidat a reçu (sa vue, son
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
| `/cmreplay repere <texte>` | Un repère maintenant, dans tous les fichiers en cours |
| `/cmreplay voir` · `voir <n> [zone\|joueur]` | Les enregistrements ; en regarder un (barre de lecture, voir plus haut) |

Réglages : `config/craftmaster/replays.json`.

## Limites connues
- **La taille** : un fichier joueur fait environ 0,3 Mo sur le disque pour 30 s (4 Mo une fois décompressé), presque
  tout pour le monde envoyé au départ (la distance de vue) ; ensuite il grossit peu. La zone : moins de 0,1 Mo.
- **Les caméras** ne montrent que ce qui est dans le fichier : une caméra qui filme hors de la zone enregistrée (le
  studio pendant une manche dehors) ne voit que le ciel. Agrandir la zone : `/cmreplay marge`.
- **Revenir en arrière** recharge le fichier (2 à 4 s) : c'est le lecteur de Server Replay qui repart du début.
- Le lecteur retire le joueur de la liste du serveur pendant la lecture : un serveur où il est seul se croit vide
  (« Server empty… pausing » au bout d'une minute) ; la lecture et la barre continuent quand même.
- Server Replay ne garde dans la zone que ce que le serveur envoie à tout le monde : barres de boss propres à un
  joueur (le chrono), titres envoyés à un seul joueur… ne sont que dans le fichier de ce joueur.
- Un joueur qui se déconnecte en pleine manche a deux fichiers dans son dossier : la régie ouvre celui qui finit le
  plus près de la fin de l'enregistrement.
- Des barres de boss enregistrées sans leur création (un fichier joueur commencé pendant le chrono) faisaient planter
  le jeu de celui qui regarde : CraftMaster les ignore maintenant de son côté, et écrit leur création au début des
  nouveaux fichiers.
