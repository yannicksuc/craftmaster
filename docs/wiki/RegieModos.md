# Régie des modos

Des modérateurs pilotent l'émission comme le Stream Deck de LordFinn, **dans le jeu** (touche `;`) ou **depuis
lordfinn.fr** (`/craftmaster/regie`, sur téléphone ou ordinateur). Les deux envoient les mêmes actions que la voix et le
Stream Deck ; le serveur vérifie le droit de chaque action et note qui a fait quoi. Rien ne s'affiche à l'image : la régie
ne s'ouvre jamais sur un jeu en mode diffusion (F8), et le compte rendu ne va qu'au modo.

Conception : [docs/design/regie-moderateurs.md](../design/regie-moderateurs.md).

## Les droits
Chaque action demande un droit. L'organisateur (`craftmaster:admin` ou opérateur niveau 2) a tout.

| Droit | Actions | Permission (LuckPerms) |
|---|---|---|
| `camera` | caméras (coupe, auto, plan large, transition, suivre, vue d'un joueur, mosaïque…), habillage, générique, projecteurs, effets sur l'image de la diffusion | `craftmaster:camera` |
| `tv` | télés du plateau (`tele.show` : clip, lien, direct d'un candidat, image, éteindre) | `craftmaster:tv` ou `craftmaster:image` |
| `replay` | replays (`replay.*`, avec la régie de replay) | `craftmaster:replay` |
| `jeu` | points du direct, lancer / finir une manche, arrêter le chrono, chronos et buzzers, feuille de résultats, tablettes du minuteur | `craftmaster:jeu` |
| (organisateur) | publier, révélation, annuler, saisons, parties, joueurs, réglages des manches, effets forcés sur les joueurs | `craftmaster:admin` |

`craftmaster:modo` donne `camera`, `tv` et `replay` d'un coup.

Sans LuckPerms, la **liste des modos** du mod suffit (et c'est elle qui sert pour le site) :

| Commande | Effet |
|---|---|
| `/modo` ou `/modo liste` | Les modos (login Twitch, pseudo Minecraft, droits) |
| `/modo ajouter <twitch> [minecraft\|-] [droits]` | Ajoute ou modifie ; droits : `camera,tv,replay,jeu` (par défaut `camera,tv,replay` : points et manches restent à l'organisateur) |
| `/modo retirer <nom>` | Retire (login Twitch ou pseudo Minecraft) |
| `/modo pause` · `/modo reprendre` | Coupe d'un coup toutes les régies des modos (en jeu et sur le site) · les rend |
| `/modo journal [n]` | Qui a fait quoi : les n derniers ordres (10 par défaut), réussis ou refusés |
| `/modo site [etat]` · `on` · `off` | État du lien avec le site · l'activer · le couper |
| `/modo site lier [adresse]` | Nouveau secret du lien (voir plus bas) |

Le droit « organisateur » ne se donne jamais par la liste. Fichier : `config/craftmaster/moderateurs.json`.
Un modo ne peut pas envoyer plus de 5 ordres par seconde.

## Dans le jeu
Touche **`;`** (réglable dans les contrôles, « Régie des modos ») ou `/regie`. Un onglet par droit :
- **Caméras** : une touche par caméra (● rouge : à l'antenne ; survol : son lieu), réalisation auto oui / non, plan large,
  transition (coupe, fondu, enchaîné), suivre un candidat ; deux plans côte à côte, voir plus bas ;
- **Manche** : manche suivante, fin de manche, stop chrono, les tablettes du minuteur (go, pause, à zéro, relancer), la
  manche en cours et son chrono, une touche par manche pour la lancer ;
- **Points** : pour la manche en cours, −1 / +1 / +2 / +3 par candidat ; les appuis rapprochés sur un même candidat
  partent en un seul « +3 » (comme au Stream Deck) ;
- **Journal** (organisateur) : les derniers ordres de tout le monde.

L'écran suit le serveur (une fois par seconde au plus) : un droit ajouté fait apparaître son onglet, `/modo pause` ou un
droit retiré ferme la régie.

## Deux plans à l'antenne (écran partagé)
Dans la grille des plans, en jeu comme sur le site :
- **clic** : ce plan seul à l'antenne (comme avant) ;
- **Ctrl+clic** (⌘ sur Mac ; **appui long** au doigt sur le site) sur un autre plan : les deux côte à côte, celui qui
  était à l'antenne à gauche ; les deux tuiles sont encadrées en rouge, « DIRECT · GAUCHE » / « DIRECT · DROITE » (en
  jeu : « DIRECT G » / « DIRECT D ») ;
- Ctrl+clic sur l'un des deux : il sort, l'autre reste seul ; Ctrl+clic sur un troisième : il remplace celui de droite ;
- un clic seul revient toujours à un seul plan.

C'est l'écran partagé du mode diffusion (même image que `/cam mosaic`, à deux tuiles), choisi plan par plan. Action :
`camera.cut` avec `pair: true` (le serveur décide d'ajouter, de remplacer ou de retirer selon ce qui est à l'antenne).
Sur un plan qui suit un candidat ou un trajet, Ctrl+clic fait une coupe normale.

## Sur le site
Page **https://lordfinn.fr/craftmaster/regie** : mêmes onglets, faits pour le téléphone. Le modo se connecte au site
avec Twitch ; son login Twitch doit être dans `/modo liste`. La page dit quoi faire sinon (se connecter, lier Twitch,
demander à LordFinn de l'ajouter).

Comment c'est relié : **le serveur Minecraft appelle le site, jamais l'inverse** (aucun port à ouvrir). Il garde une
requête ouverte vers l'API (`/api/craftmaster/link/orders`, 25 s) pour recevoir les ordres, et pousse l'état et les
réponses (`/link/push`) quand ils changent. Le site ne décide d'aucun droit : il dit seulement quel login Twitch est
connecté ; le serveur revérifie chaque ordre avec sa liste. L'API locale du Stream Deck (127.0.0.1, dans le jeu de
LordFinn) n'a rien à voir avec ce lien et ne sort jamais du PC.

Mise en place (une fois) :
1. Sur le serveur Minecraft : `/modo site lier https://lordfinn.fr/api/craftmaster/link` ; le secret s'affiche (clic
   pour copier). Fichier : `config/craftmaster/site.json`.
2. Dans le `.env` de l'API du site : `CRAFTMASTER_LINK_SECRET=<le secret>`, puis redémarrer l'API.
3. `/modo site` doit dire « connecté ». Un nouveau `lier` change le secret (l'ancien ne marche plus).

Le lien n'accepte que `https://` (ou `http://127.0.0.1` / `localhost` pour les essais).

## Ce qui viendra avec les autres branches
- **Télés** (clips, liens Twitch, direct d'un candidat, image) : un onglet Télés dès que la branche clips/télés écrit
  ses ordres comme des actions `tele.show` (fait, branche `claude/friendly-keller-d41f2f`). Listes pour la régie :
  `MediaServer.screenNames`, `clips().list()`, `channels().all()`. Le clip lui-même (`clip.start`) s'enregistre dans le
  jeu de LordFinn : une régie distante ne peut pas le lancer (il reste au Stream Deck et à la voix).
- **Replays** : un onglet Replay. L'action d'un modo appellera, sur le jeu de diffusion qui regarde, l'API serveur de la
  branche replays : `ReplayViewing.seekFor` / `pausedFor` / `speedFor` / `viewFor` (et pas `ServerReplayBridge` : pendant
  la réouverture d'un fichier, il n'y a plus de lecteur et l'ordre serait perdu ; ces fonctions l'appliquent à la
  réouverture). La réalisation du replay (Direct, Auto, Manuel, caméra, vue d'un joueur) sera envoyée à ce jeu par
  `ReplayViewing.directFor(watcher, action)` (fait dans la branche replays : `replay.control`, `camera.cut`, `camera.follow`,
  `camera.pov`, `camera.auto`). Le jeu qui regarde est retiré de la liste des joueurs pendant la lecture : le retrouver
  avec `ReplayViewing.watcher(server, nom)`, pas `getPlayerByName` ni un sélecteur.

## Vérifié (13/11/2026, 26.2)
Serveur de dev + Joueur2 (non opérateur) dans la liste : régie ouverte par `/regie` avec le seul onglet Caméras, coupe
sur la caméra 2 (● à l'antenne, journal « Joueur2 (jeu) À l'antenne : caméra 2 »), droit `jeu` ajouté → onglets Manche
et Points sans rouvrir, manche lancée, 3 appuis sur +1 → un seul « +3 pour Ensuri », `/modo pause` ferme la régie,
mode diffusion : `/regie` n'ouvre rien. Site : la vraie route de l'API en local (connexion simulée) reliée au serveur de
dev, page ouverte : caméra coupée depuis la page (aller-retour 0,12 s), « +3 » groupé, login hors liste refusé par le
site, `score.add` et `scores.publish` refusés par le serveur Minecraft à un modo qui n'a que `camera`.
