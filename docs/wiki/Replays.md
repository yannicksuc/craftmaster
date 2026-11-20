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
- **Quand** : `/cm replay mode manche` (défaut) : du lancement de chaque manche à sa fin (`/cm manche fin`, ou la manche
  suivante). `mode partie` : de la première manche d'une partie jusqu'à ce qu'une autre partie devienne active ou
  qu'elle soit terminée (`/cm partie statut 1 terminee`), entre les manches compris. `mode off` : rien
  d'automatique, `/cm replay lancer` et `/cm replay stop` à la main.
- **Les zones de l'épreuve, une vidéo chacune** : les zones d'épreuve existent déjà (`/cm epreuve zone creer <nom>`
  avec les deux points du mètre ruban, `show <nom>` pour voir leur contour, voir [Épreuves](Epreuves.md)). On les donne
  à l'épreuve : `/cm manche zones <manche> <zone> [<zone>…]` (`/cm manche zones <manche>` seul pour les retirer). Plusieurs zones
  éloignées (ateliers, terrains, une salle par candidat) : chacune a son enregistrement, et en lecture on passe de
  l'une à l'autre au même instant (« la zone d'Ensuri » à la voix, `view?name=…` au Stream Deck). Sans zone donnée :
  celles que filment les caméras de la manche (`/cm cam lieu … <zone>`) ; sans aucune : une zone déduite de ses caméras
  et points de TP (ou autour des candidats au lancement). Marge de 2 chunks autour (`/cm replay marge <n>`), 40 chunks
  de côté au plus. La caméra du replay démarre sur le plan large de la zone.
- **Filet de sécurité** : un candidat qui n'est dans **aucune** zone est enregistré à part (sa vue, son interface)
  tant qu'il est dehors, jusqu'à 10 s après son retour. Sur la timeline : « Ensuri hors zone », « Ensuri revient dans la
  zone ». En lecture, ce fichier s'appelle « Ensuri 12:30 » (le moment de sa sortie) ; « Ensuri » tout court prend celui
  qui couvre l'instant regardé.
- **Fichiers par joueur** (option, `/cm replay joueurs true`) : une vidéo complète par candidat connecté (un nouveau
  fichier s'il se reconnecte) ; le filet de sécurité n'est alors plus nécessaire. Par défaut non : le poids ne dépend
  plus du nombre de joueurs. Un joueur déjà enregistré par `/replay start players` est laissé tel quel.
- **En plus de Minecraft** : l'état de l'émission (plan à l'antenne, caméras, chrono, scores publiés, réalisation
  automatique…) au début de chaque fichier puis à chaque changement, les habillages (synthés, points en direct…) et
  la caméra libre. Et les **moments clés** sur la timeline (voir plus bas).

## Les moments clés
Chaque moment est noté avec son **type** (une petite icône sur la barre), le **candidat** qu'il concerne (sa
couleur : chaque candidat a la sienne, dans l'ordre de la saison) et la **zone** où il était :

| Icône | Moment |
|---|---|
| trait doré | Début et fin de manche (ou de partie) |
| croix | Mort d'un candidat |
| coche | Résultat noté (`/cm epreuve feuille regler`, la feuille de résultats) ; disque barré : disqualifié |
| sablier | Temps d'un chrono arrêté (« Ensuri ⏱ 1:23,4 ») ; sablier doré : fin du chrono de la manche |
| cloche | Un candidat sonne sa cloche de bureau |
| point | Points donnés (« Ensuri +3 ») |
| losange | Projecteur, enveloppe ouverte, buzzer |
| croix dorée | Tâche révélée, scores publiés, révélation du classement |
| carré | Candidat hors de toutes les zones ; petit carré : il revient |
| drapeau plein | **Drapeau de la régie** : de la couleur du candidat à l'antenne (suivi ou vue subjective), crème sur un plan large |
| fanion | **Drapeau d'un joueur** : forme à part, 20 s entre deux, 30 par manche au plus |

**Poser un drapeau**, en direct, à un moment à revoir : touche **I** (« Replay : drapeau sur ce moment », dans les
contrôles CraftMaster) pour tout le monde ; la régie aussi par la voix (« marque ce moment », « drapeau pour belle
chute »), le Stream Deck (`/replay/flag`, `?text=` pour une note) ou `/cm replay drapeau [texte]`.

Les moments trop proches sur la barre n'en font qu'un (un petit point au-dessus) : la bulle les liste tous. Les fins
du minuteur des tablettes ne sont pas notées (la tablette n'est pas touchée) : la cloche qui l'arrête, si.

## Les fichiers
Dans les dossiers de Server Replay (`recordings/` du serveur par défaut), un dossier par enregistrement :

| Dossier | Contenu |
|---|---|
| `recordings/chunks/cm-s1-p3-m2-le-pont/<date-heure>.mcpr` | La zone de la manche 2 « Le Pont », partie 3, saison 1 |
| `recordings/players/cm-s1-p3-m2-le-pont-ensuri/<date-heure>.mcpr` | La vue d'Ensuri pendant cette manche |
| `recordings/chunks/cm-s1-p3/…`, `players/cm-s1-p3-ensuri/…` | Mode partie : toute la partie 3 |
| `recordings/craftmaster.json` | L'index : chaque enregistrement, ses dossiers, sa zone, ses repères (secondes) |

**Format** : Flashback (`.zip`) par défaut ; `/cm replay format replaymod` ou `serveur` (celui de la configuration de
Server Replay, `config/server-replay/config.json`, `default_encoding`) pour changer. Il ne faut pas le mod Flashback
pour regarder : le lecteur est sur le serveur, rien à installer chez les joueurs, aucun conflit avec WaterMedia.

**Épreuves longues (une heure et plus)** :
- **Morceaux** : un fichier Flashback est écrit sur le disque par morceaux, chacun avec le monde au départ. CraftMaster
  ferme un morceau **toutes les 10 s**, pour les zones comme pour les fichiers des joueurs
  (`/cm replay morceaux <zone s> <joueurs s>`, de 10 à 300). Un saut va directement au bon morceau, dans les deux sens.
- **Crash ou arrêt brutal** : au redémarrage, Server Replay récupère seul les fichiers jusqu'au dernier morceau écrit
  (testé : serveur tué à 1:36, fichier de zone récupéré jusqu'à 1:28). L'index est réécrit toutes les 10 s : le replay
  apparaît dans `/cm replay voir` avec « (interrompu) ».
- **Chrono et synchro** : toutes les 10 s, chaque fichier note où en sont les ticks du serveur. Le chrono rejoué et le
  passage d'un fichier à l'autre suivent ces relevés : pas de dérive même si le serveur rame (sans eux : environ 1 s
  par heure à 20 TPS, plus si ça rame).
- **Poids** (morceaux de 10 s) : une zone environ 15 Mo/h sur le disque ; un fichier joueur environ 50 Mo/h, et son
  cache de chunks grossit vite pendant l'enregistrement (jusqu'à ~1,5 Go/h non compressé, compressé à la fin) : d'où
  les zones par défaut. Coût serveur : rien de mesurable (`/tick query` : 3 à 5 ms par tick pendant un enregistrement
  de deux zones, 7 ms avant).
- **Fluidité** (morceaux de 10 s, mesuré) : chaque morceau commence par un instantané du monde, pris sur le fil du
  serveur. Tous ensemble, ils faisaient un tick de 30 à 130 ms toutes les 10 s (au-delà des 50 ms d'un tick) ;
  CraftMaster étale donc les coupes dans les 10 s : un instantané par tick, 17 à 25 ms pour une zone, 28 à 46 ms pour un
  fichier joueur (les ticks normaux : 3 à 6 ms). En lecture, le passage d'un morceau au suivant ne rejoue pas
  l'instantané : pas d'à-coup (mesuré sur une minute, le fichier avance au rythme du temps réel aux passages de 10,
  20… 60 s). Sauts : 9 à 200 ms, en avant comme en arrière.
- **Disque** : `/cm replay disque` (place prise, place libre), alerte aux organisateurs sous 5 Go libres
  (`/cm replay alerte <Go>`), purge automatique des vieux replays (`/cm replay garder <jours>`, `/cm replay max <Go>` :
  les plus anciens partent d'abord ; rien n'est supprimé tant que ce n'est pas réglé), ou tout de suite
  (`/cm replay purge <jours>`). Seuls les fichiers des enregistrements de l'index sont supprimés.

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
Sur le serveur qui a les fichiers, sans rien installer de plus chez le joueur : `/cm replay voir` liste les
enregistrements (le plus récent en premier), un clic sur `[zone]` ou sur un candidat lance la lecture
(`/cm replay voir <n> [zone|joueur]` à la main). Le lecteur est celui de Server Replay : pendant la lecture tu sors du
monde réel, tu n'y reviens qu'à la fermeture.

Avec CraftMaster sur ton jeu, le replay passe en **mode diffusion**. L'image reste celle des viewers : à l'écran, rien
d'autre qu'une **fine ligne de temps** en bas (dans les couleurs de l'habillage, le temps à gauche, la durée à droite,
les moments clés en petites icônes) qui n'apparaît que **3 s après un ordre** et pendant une pause, et une
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
| « que les morts » · « montre les cloches » · « les drapeaux » | | `filter?kind=morts` (résultats, chronos, cloches, drapeaux, drapeaux des joueurs, points, manches, sorties de zone, projecteurs) |
| « les moments d'Ensuri » · « les repères de la zone B » | | `filter?player=Ensuri` · `filter?zone=B` (se cumulent) |
| « tous les repères » · « enlève le filtre » | | `filter/clear` |
| « caméra 2 » · « plan large » | **1–9**, molette · **0** | `camera?camera=2` (ou `/camera/cut`) |
| « suis Ensuri » · « vue d'Ensuri » | **Tab** · **Maj+Tab** | `follow?player=Ensuri` · `pov?player=Ensuri` |
| « l'écran d'Ensuri » · « la zone » | | `view?name=Ensuri` · `view?name=zone` |
| « les plans du direct » · « réalisation auto » · « caméra libre » | **G** | `mode?m=direct\|auto\|manuel\|libre` (ou `/camera/auto`) |
| « caméra libre depuis la caméra 2 » | | `mode?m=libre&camera=2` |
| « ferme le replay » | | `close` |

À la souris : **M** (ou `/cmbarre`) donne la souris sur la ligne de temps, et rien d'autre : un clic y saute, le
survol d'une icône ouvre une bulle (heure et nom de chaque moment, 5 au plus), un clic sur une icône saute à ce
moment, la molette avance ou recule de 5 s ; un filtre en cours est rappelé discrètement à droite ; repère suivant /
précédent ne passent que par les moments filtrés ; M ou Échap rend la souris au jeu. Les touches
sont dans les contrôles, catégorie CraftMaster. Pendant un replay la voix ne passe pas par le serveur (le lecteur ne
laisse passer que des commandes) : le jeu comprend et exécute lui-même ; les autres ordres (points, manches…)
répondent « pas pendant un replay ».

**Qui réalise** :
- **Direct** (par défaut) : les plans choisis en direct, tels qu'enregistrés (coupes, suivis, transitions,
  habillage). Là où le direct n'avait pas de plan, la réalisation auto.
- **Auto** : recalculée à partir des repères, comme la réalisation automatique du direct : qui marque est suivi 7 s,
  qui est sous le projecteur (enveloppe, buzzer, projecteur) 9 s, sinon le plan large.
- **Manuel** : tes touches, sur ce jeu seulement.
- **Libre** : ta propre caméra de spectateur, qui part **exactement** de l'image du moment (position et angle du plan
  à l'antenne, travelling ou suivi compris) ou d'une caméra choisie (« caméra libre depuis la caméra 2 »). L'habillage
  reste celui de la diffusion (rien de l'interface du jeu à l'image) ; tu te déplaces comme en spectateur. Pour en
  sortir : **1–9** ou la molette (une caméra), **0** (plan large), **G** (retour aux plans du direct), ou les mêmes
  ordres à la voix et au Stream Deck.
  - **Arènes clonées** (une par candidat, `/cm manche zones`) : en changeant de zone (« la zone B »), la caméra libre
    garde sa place **par rapport au coin de la zone** : même endroit, même angle dans l'autre arène. Après un recul
    qui rouvre le fichier, elle revient où elle était. Une zone déduite (sans zone d'épreuve) n'a pas de coin : la
    caméra repart du début du fichier.

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

## Commandes (`/cm replay`, alias `/cm replay`, organisateurs)
| Commande | Effet |
|---|---|
| `/cm replay` | Server Replay trouvé ?, mode, ce qui s'enregistre en ce moment |
| `/cm replay mode manche\|partie\|off` | Enregistrement automatique de chaque manche, chaque partie, ou jamais |
| `/cm replay lancer` · `stop` | À la main : la manche en cours, sinon la partie active ; `stop` sauvegarde et n'est pas relancé avant la manche suivante |
| `/cm replay joueurs true\|false` · `zone true\|false` | Un fichier complet par candidat (défaut non) / un par zone (défaut oui) |
| `/cm manche zones <manche> [<zone>…]` | Les zones de l'épreuve (rien = les retirer) |
| `/cm replay marge <0..8>` | Chunks ajoutés autour de la zone |
| `/cm replay format flashback\|replaymod\|serveur` | Format des prochains enregistrements (Flashback par défaut) |
| `/cm replay morceaux <zone s> <joueurs s>` | Longueur des morceaux : au plus ça de perdu après un crash (défaut 10 et 10) |
| `/cm replay disque` | Place prise par les replays, place libre, réglages de purge |
| `/cm replay garder <jours>` · `max <Go>` · `alerte <Go>` | Purge automatique par l'âge, plafond, alerte de place libre (0 = aucun) |
| `/cm replay purge <jours>` | Supprime maintenant les replays de plus de tant de jours |
| `/cm replay realisation <joueur> <json>` | Un ordre de réalisation pour le replay que ce joueur regarde (ex. `{"type":"camera.follow","player":"Ensuri"}`, `{"type":"replay.control","do":"mode","mode":"auto"}`) ; c'est ce qu'utilise la régie des modos |
| `/cm replay drapeau [texte]` | Un drapeau maintenant (couleur du candidat à l'antenne) ; un joueur non organisateur pose un fanion |
| `/cm replay repere <texte>` | Un repère maintenant, dans tous les fichiers en cours |
| `/cm replay voir` · `voir <n> [zone\|joueur]` | Les enregistrements ; en regarder un (barre de lecture, voir plus haut) |

Réglages : `config/craftmaster/replays.json`.

## Limites connues
- **La taille** : voir les mesures plus haut. En ReplayMod, l'enregistrement en cours est écrit non compressé
  (environ 30 Mo pour 12 min et 3 fichiers) puis compressé à la fin.
- **Les caméras** ne montrent que ce qui est dans le fichier : une caméra qui filme hors de la zone enregistrée (le
  studio pendant une manche dehors) ne voit que le ciel. Agrandir la zone : `/cm replay marge`.
- **Revenir en arrière** en ReplayMod recharge le fichier (2 à 4 s, l'écran de chargement passe à l'image) : le
  lecteur de Server Replay repart du début. En Flashback, c'est instantané.
- Après un redémarrage en pleine manche, la manche est toujours « en cours » : un enregistrement repart dès que le
  serveur est lancé. Une manche jouée dans des **arènes** est filmée sur ses arènes (référence et copies) même si
  personne n'est encore revenu ; à la reprise (`/cm reprise reprendre`, « Forcer la reprise »), un enregistrement resté
  vide est refermé et la manche repart en **partie 2** (3…), liée à la précédente dans l'index (`part`, `follows`),
  avec les fichiers des candidats revenus.
- Le lecteur retire le joueur de la liste du serveur pendant la lecture : un serveur où il est seul se croit vide
  (« Server empty… pausing » au bout d'une minute) ; la lecture et la barre continuent quand même.
- Server Replay ne garde dans la zone que ce que le serveur envoie à tout le monde : barres de boss propres à un
  joueur (le chrono), titres envoyés à un seul joueur… ne sont que dans le fichier de ce joueur.
- Un joueur qui se déconnecte en pleine manche a deux fichiers dans son dossier : la régie ouvre celui qui finit le
  plus près de la fin de l'enregistrement.
- Des barres de boss enregistrées sans leur création (un fichier joueur commencé pendant le chrono) faisaient planter
  le jeu de celui qui regarde : CraftMaster les ignore maintenant de son côté, et écrit leur création au début des
  nouveaux fichiers.
