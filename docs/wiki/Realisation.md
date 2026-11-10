# Réalisation TV : caméras, mode diffusion, habillage, générique

Ce que le public Twitch voit : un jeu « caméra » (le **client de diffusion**, capturé par OBS) qui n'affiche plus
l'interface de Minecraft mais **les plans des caméras du studio** et **l'habillage de l'émission** (logo, bandeau de
la manche avec le chrono, synthés avec le nom des candidats, scores publiés, points en direct, générique).
Tout est piloté depuis le serveur : LordFinn (ou un réalisateur) change de plan par commande, à la voix ou avec les
touches 1 à 9 du client de diffusion ; plusieurs jeux en mode diffusion montrent exactement la même chose.

## En bref, le soir du direct
1. Une fois : poser les caméras du studio (voir plus bas), leur donner un rôle (`/cam role 1 large`,
   `/cam role 2 trone`, `/cam role 4 podium`).
2. Sur le PC de diffusion : se connecter avec le compte caméra, de préférence **en spectateur**
   (`/gamemode spectator <compte>`), puis **F8** (ou `/diffusion`, ou depuis n'importe quel jeu admin
   `/tv broadcast <compte> on`). OBS capture la fenêtre du jeu (1920 × 1080 conseillé).
3. `/tv intro` (ou « générique ») au début, `/cam auto on` (ou « réalisation automatique ») si on veut que la
   réalisation se fasse toute seule, `/tv outro` (« générique de fin ») à la fin.

## Les caméras
- **Objet « Caméra de studio »** (onglet CraftMaster, recette : lingot de fer, verre, lingots, bâtons) : clic droit
  sur le sol → un trépied avec sa caméra, numéro suivant, objectif tourné comme le regard du joueur.
- **`/cam here <n> [nom]`** (le plus pratique pour cadrer) : la caméra n se met **à la place de tes yeux, regardant
  là où tu regardes**. On se place, on regarde le plan voulu, on tape la commande. Une caméra existante est déplacée.
- `/cam aim <n>` : la caméra garde sa place, elle regarde là où tu regardes. `/cam goto <n>` : tu vois exactement ce
  que voit la caméra (pour vérifier, puis `/cam here <n>` pour recadrer d'ici).
- Clic droit sur un trépied (réalisation) : son numéro, son réglage et des liens cliquables (à l'antenne, viser,
  renommer, rôle). Un coup dessus le retire (avec sa caméra).
- Réglages : `/cam name <n> <nom>`, `/cam role <n> large|trone|podium|aucun` (un seul rôle par caméra et par rôle),
  `/cam zoom <n> <15..110>` ou `reset` (champ de vision), `/cam motion <n> fixe|respiration|travelling|panoramique`.
- **Mouvement** : *respiration* (défaut) = la caméra « respire » de quelques centimètres et d'une fraction de degré,
  comme une caméra sur pied tenue par un cadreur ; *travelling* = avance lentement (1,6 bloc max, ralentit) ;
  *panoramique* = balaie doucement ±7° ; *fixe* = immobile.
- Sur la diffusion, les trépieds **ne sont jamais affichés** (on voit à travers), ni le joueur caméra, ni les
  spectateurs, ni les pseudos au-dessus des têtes (le synthé nomme les candidats). En jeu, le **voyant rouge** d'une caméra s'allume quand elle est à l'antenne, et son numéro est peint
  sur ses flancs.
- Les caméras sont enregistrées dans `config/craftmaster/cameras.json` (le plan marche même si le trépied est dans
  une zone non chargée).

## Changer de plan
| Moyen | Exemples |
|---|---|
| Commandes | `/cam cut 2` · `/cam cut "Plan large" fondu` · `/cam cut trone enchaine` · `/cam wide` · `/cam next` / `prev` · `/cam follow Ensuri` |
| Voix | « caméra 2 », « plan large », « plan sur le trône », « le podium », « suis Ensuri », « caméra 3 en fondu » |
| Client de diffusion | touches **1 à 9** = caméra 1 à 9 du lieu actif, **molette** = caméra précédente / suivante, **Tab** / Maj + molette = zone suivante, **0** = écran partagé, `/diffusion camera <n, nom ou clé>`, `/diffusion zone <zone\|next>` |

- Les touches et la molette du client de diffusion demandent la coupe au serveur : elle n'est faite que si ce
  joueur est organisateur ou a la permission **`craftmaster:camera`**. Suivre la réalisation ne demande rien.
- **Transitions** : *coupe* (sèche, par défaut), *fondu* (passage par le noir), *enchaîné* (l'image précédente se
  fond dans la nouvelle). Par plan (`/cam cut 2 enchaine`) ou par défaut (`/cam transition fondu`).
- **Plan qui suit un joueur** (`/cam follow`) : de trois quarts face, un peu au-dessus, lissé (le cadre suit sans
  à-coups), jamais derrière un mur. Son nom s'affiche en synthé quelques secondes.
- **Réalisation automatique** (`/cam auto on|off`, « réalisation automatique ») : coupe sur le candidat qui marque
  (7 s), sur celui qui ouvre son enveloppe ou qu'on met en lumière (`/tv spotlight <joueur>`, 9 s, avec un synthé
  « ouvre son enveloppe »), sur la caméra *podium* pendant toute la révélation, sur le plan large au lancement d'une
  manche et à la fin de chaque plan automatique. Une coupe à la main reprend la main.
- Le joueur caméra en **spectateur** est déplacé tout seul près du plan (4 blocs sous l'objectif) pour que le
  serveur lui envoie le décor et les joueurs ; un joueur qui n'est pas spectateur n'est jamais déplacé.

## Les lieux : caméras du studio et caméras des épreuves
Chaque caméra a un **lieu** : le **studio** (par défaut ; toutes les anciennes caméras y sont) ou une **manche**
(une épreuve, par son id : `pont`). Dans une manche, une caméra peut aussi appartenir à une **zone** : la salle d'un
candidat ou d'une équipe. Les zones sont les **zones d'épreuve** (`/craftMasterZone create salle-ensuri …` puis
`players salle-ensuri Ensuri`) : on donne à la caméra le nom de la zone, ou simplement le nom du candidat.

**Numéros : chaque lieu, et chaque zone, a ses caméras 1, 2, 3…** La salle d'Ensuri a sa caméra 1, celle de Cyllu
aussi. C'est le plus simple en direct : on choisit la salle, puis on appuie sur 1, 2 ou 3, et le numéro peint sur
le trépied est celui de la touche. (Le lieu et la zone sont écrits en petit sous le numéro, sur le trépied.)
Chaque caméra a aussi une **clé** unique pour la viser de n'importe où : `studio:2`, `pont:1`, `pont:ensuri:1`.

**Quelles caméras visent les touches 1–9, la molette et « caméra 2 » ?** Ça suit l'émission tout seul :
- manche lancée (et jusqu'à la révélation) : les caméras **de cette manche** si elle en a ;
- générique, intro du studio, révélation, entre deux manches : celles **du studio** ;
- dans une manche à zones : **Tab** (ou Maj + molette) passe à la zone suivante (Maj + Tab : précédente), puis
  1–9 = les caméras de cette salle ; « zone d'Ensuri » / « caméras de l'équipe bleue » à la voix (et ça montre son
  plan large), `/cam zone ensuri` ; « suis Ensuri » choisit aussi sa zone ;
- à la main, jusqu'au prochain temps de l'émission : `/cam focus studio`, `/cam focus pont [zone]`,
  `/cam focus auto` pour revenir au suivi automatique. `/cam focus` dit où on en est.
- Viser ailleurs reste possible à tout moment : `/cam cut studio:2`, « caméra 2 du studio », « caméra 1 d'Ensuri »,
  `/diffusion camera pont:2`. « Plan sur le trône » et « le podium » trouvent la caméra du studio même en manche.

| Mettre en place | Commande |
|---|---|
| Ranger une caméra | `/cam lieu <caméra> <studio\|manche> [zone]` : `/cam lieu 3 pont ensuri` · « la caméra 3 est sur le pont » |
| Poser directement | `/cam focus pont ensuri` puis `/cam here 1` (ou l'objet Caméra de studio) ; ou `/cam here pont:ensuri:1` |
| Voir | `/cam list` (le lieu actif), `/cam list tout` (tous les lieux), clic droit sur un trépied (son lieu + bouton [Lieu]) |

- Changer de lieu garde le numéro s'il est libre là-bas (sinon le premier libre) ; le trépied reste où il est.
- **Réalisation automatique en manche** : quand un candidat marque, ouvre son enveloppe ou buzze, elle coupe sur
  **la caméra de sa zone la plus proche de lui** (sinon le plan qui le suit), puis revient au plan large de la manche.
- **Écran partagé** : `/cam mosaic` (« écran partagé », touche **0** du client de diffusion) : jusqu'à 4 salles côte à
  côte, chacune par le plan large de sa zone, avec le nom des candidats. `/cam mosaic off` ou 0 pour revenir. Les
  vignettes se rafraîchissent à tour de rôle (une image sur deux, trois ou quatre) : parfait pour des salles où l'on
  construit, moins pour une course rapide.
- `/cam pov Ensuri` (« vue d'Ensuri ») : à travers les yeux d'un joueur.

## Les travellings enregistrés (trajets)
Un **trajet** est un mouvement de caméra en plusieurs **étapes** : la caméra glisse de l'une à l'autre en courbe
douce (pas de cassure aux étapes), démarre et s'arrête en douceur, et peut marquer une **pause** sur une étape.
1. Place-toi au départ, regarde le premier cadre : `/cam trajet pont point`.
2. Déplace-toi, regarde le cadre suivant : `/cam trajet pont point 3` (3 secondes pour y aller). Recommence.
   Plus simple : `/cam trajet pont objet` donne un **Repère de travelling** (clap) : **clic droit = une étape à tes
   yeux**, accroupi + clic droit = retirer la dernière.
3. Régler : `duree <s> [étape]` (temps pour arriver à l'étape), `pause <s> [étape]` (arrêt sur l'étape),
   `courbe lisse|lineaire|entree|sortie [étape]`, `zoom <15..110>|reset [étape]`, `remplacer <étape>` (refaire
   l'étape d'ici), `aller <étape>` (y aller pour vérifier), `suppr [étape]`, `lieu <lieu>`, `effacer`.
4. Vérifier : `/cam trajet pont` (la liste des étapes) ; `montrer` (la ligne dorée dans le monde 20 s, étapes en
   rouge, vue seulement par les organisateurs, jamais par la diffusion) ; `apercu` (le trajet joué **sur ton écran
   seulement**, avec le temps en haut).
5. À l'antenne : `/cam jouer pont [transition] [effet]` · « lance le travelling du pont ». À la fin, la caméra reste
   sur la dernière étape jusqu'au plan suivant. `/cam trajets` les liste.
- Les trajets sont dans `config/craftmaster/trajets.json`. Ils sont joués image par image par le jeu de diffusion
  (fluide à 60 i/s, même avec un serveur qui rame).

## L'intro animée d'une manche
Chaque manche peut avoir son **intro** : une suite de plans (caméras, trajets, joueurs suivis), chacun avec sa durée,
sa transition (enchaîné par défaut, fondu pour le premier), un **effet d'image** facultatif (`sepia`, `noir_blanc`,
`pixel`, `fisheye`, `vignette`, `masque_rond`, `citrouille`) et une **carte** facultative : *titre* (grand titre au
centre) ou *synthé* (bandeau en bas), avec le nom de la manche ou un texte (`Titre | sous-titre`). Sans carte, le
dernier plan montre le titre de la manche. Musique facultative (le thème, ou n'importe quel son). Pendant l'intro, le
logo et le bandeau de la manche se retirent, la réalisation automatique attend, puis le plan large de la manche
prend le relais.

| Faire | Commande (`<m>` = la manche) |
|---|---|
| Ajouter un plan | `/tv intro <m> ajouter camera large 4` · `ajouter trajet survol` · `ajouter suivi Ensuri 5` (+ `[transition] [effet]`) |
| Régler un plan | `/tv intro <m> plan 2 duree 3` · `plan 2 transition coupe` · `plan 2 effet sepia` · `plan 1 carte synthe Le Pont \| Épreuve 1` · `plan 3 retirer` |
| Réglages | `/tv intro <m> musique theme\|aucune\|<son>` · `titre on\|off` · `auto on` (jouée toute seule au lancement de la manche) · `effacer` |
| Voir / jouer | `/tv intro <m> voir` · `/tv intro <m>` · « lance l'intro du pont » · `/tv stop` l'arrête |

- Les caméras d'un plan se lisent **dans le lieu de la manche** : `large`, `2`, `ensuri:1`, ou `studio:3`.
- Tout réglage d'intro est **annulable** (« annule », `/craftMasterUndo`) : il est rangé avec la manche.
- `/tv intro` seul lance toujours le générique d'ouverture de l'émission.

## Le mode diffusion (client)
- **F8** (touche réglable, catégorie CraftMaster) ou `/craftMasterBroadcast` (`/diffusion`) `[on|off]` ;
  `/diffusion chat on|off` montre ou cache le chat ; `/diffusion status`.
- Cache : main et objet tenu, barre d'objets, cœurs, viseur, effets, barre de boss, barre d'action, liste des
  joueurs, sous-titres, notifications (« toasts »), panneau de la régie vocale. Les **titres** (manche, décompte des
  10 dernières secondes, révélation) restent.
- **Habillage** (or, velours rouge, crème, lisible à 1080p) :
  - sceau CM + « CRAFTMASTER » en haut à gauche, « ● EN DIRECT » en haut à droite ;
  - bandeau de la manche en haut au centre : « MANCHE 2 », son nom (ou le titre de la tâche révélée, chantier D),
    le chrono, une ligne d'or qui raccourcit, le tout rouge et clignotant dans les 10 dernières secondes ;
  - **synthé** en bas à gauche (visage, nom, « 2e · 14 pts » / « Candidat » / « Maître du jeu », ou texte libre :
    `/tv lowerthird player Ensuri`, `/tv lowerthird text Pause | On revient vite`) ;
  - **bandeau des scores publiés** en bas (rang, visage, nom, points) : 14 s après chaque publication (mode `auto`),
    toujours (`on`), jamais (`off`), ou maintenant (`/tv scores`). Caché pendant la révélation et pour une saison
    cachée ;
  - **points en direct** à droite : « Ensuri **+3** » qui apparaît avec un petit rebond ;
  - **carte de la tâche** à la demande (`/tv task [secondes]`, « montre la tâche ») : c'est la carte du chantier D ;
  - **bandes cinéma** : `/tv letterbox on|off`.

## Générique
- **Ouverture** (`/tv intro`, « générique ») : le thème démarre ; les caméras du studio s'enchaînent en fondus
  (plan large d'abord) sous le titre CRAFTMASTER, le sceau, la saison et la partie ; puis chaque candidat est
  présenté (plan qui le suit s'il est connecté, plan large sinon) avec sa grande carte (visage, « CANDIDAT 1/4 »,
  nom, rang publié) ; enfin le nom de la manche en cours (ou « Que le jeu commence ! »). ≈ 3,5 s par caméra
  (5 au plus) + 4 s par candidat.
- **Fin** (`/tv outro`, « générique de fin ») : 30 s de crédits qui défilent (CraftMaster, saison, partie, présenté
  par, les candidats, le **classement publié**, réalisation) sur des fondus lents entre les caméras.
- `/tv stop` arrête le générique (cartes effacées, musique coupée).
- **Musique** : le son `craftmaster:show.theme` (fichier `assets/craftmaster/sounds/show/theme.ogg`), joué pour tout
  le monde, sans atténuation, sur le volume « Juke-box / Blocs musicaux ». Le thème fourni est **provisoire** : une
  petite fanfare générée par `scripts/gen_theme.py` (rien d'échantillonné, libre de droits).
  **Mettre sa propre musique** : créer un pack de ressources contenant
  `assets/craftmaster/sounds/show/theme.ogg` (Ogg Vorbis, mono conseillé) et l'activer sur le jeu de diffusion (et
  chez qui doit l'entendre). Aucun autre fichier n'est nécessaire.

## Permissions
- Piloter (commandes `/cam`, `/tv`, objet caméra, touches de la diffusion) : organisateur ou la permission `craftmaster:camera` (LuckPerms…).
- Suivre la réalisation (mode diffusion) : tout le monde.
