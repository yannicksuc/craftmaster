# Saisie des points (arbitre), StreamDeck, cadre joueur

## L'écran de saisie (organisateurs)
Touche **U** (modifiable dans Options > Commandes > CraftMaster), `/craftMasterScores [saison]` (`/cfsc`) ou le
bouton `/menu` du StreamDeck. S'ouvre sur l'épreuve en cours, sinon sur « Toutes les épreuves ». Conception :
[docs/design/saisie-ux.md](../design/saisie-ux.md) (phase 1 faite).

**Tout est un brouillon** (hachuré jaune) jusqu'à **✔ Valider la manche** (ou Entrée) : tous les scores du
brouillon partent d'un coup, une seule entrée du journal. Le brouillon **reste quand on ferme l'écran** (Échap) :
on le retrouve en rouvrant, et le cadre du HUD rappelle « ✎ brouillon : Château » tant qu'il n'est pas validé.
✕ oublie le brouillon. Il est perdu seulement à la déconnexion.
- **◁ ▷** (Ctrl+Z / Ctrl+Y) : défaire / refaire **le dernier geste** du brouillon.
- **Annuler : …** dit ce qu'il va défaire sur le serveur (« Annuler : Steve : 5 pts (Château) ; … », l'infobulle
  donne l'heure) ; ▷ à côté refait. C'est le même journal que `/cfun` et `/cfrf`.
- L'écran se met à jour sur place après chaque modification (la sienne comme celles des autres). Changer de saison
  avec un brouillon est refusé, avec la raison.

### Une épreuve : marches, cartes, jetons
- Chaque **place** est une **marche** à la couleur de la place (or, argent, bronze, puis gris) avec sa **base**
  peinte dessus (« 1er · 5 »). Sous la marche : le score du joueur (tête de chiffre, -10 à +10, tons froids pour les
  positifs, chauds pour les négatifs).
- Chaque **joueur** est une **carte** (sa tête, son nom) posée sur une marche. Son **bonus** est un **jeton** sous
  la carte (« +1 ») : il le suit quand il change de marche. Survol : « LordFinn : 4 (2e place) +1 (bonus) = 5 ».
- **Règle** (validée par LordFinn) : score = base de la marche + jeton du joueur. Exemple : A 5, B 3, C 1 ; +1 à A
  (6) ; on échange A et C → C 5, B 3, A 1 + 1 = 2.
- **Épreuve jamais notée** : les joueurs attendent sur le **banc**, les marches portent le barème de la feuille
  de résultats (5-4-3-2-1 par défaut). **Clic sur une tête du banc** = elle monte sur la marche libre suivante :
  cliquer dans l'ordre d'arrivée suffit. « Le reste ex aequo dernier » donne la marche suivante à tout le banc.
  Un joueur laissé sur le banc garde son score (rien n'est envoyé pour lui).
- **Glisser une carte** (ou une tête du banc) : un repère doré montre où elle va, les autres s'écartent ; lâcher
  entre deux cartes = l'insérer (les autres se décalent) ; lâcher sur le banc = la retirer du classement.
- **Ex aequo** : lâcher une carte **sur** une autre (au centre, elle s'entoure d'or avec « = ») : même place, mêmes
  points, et la place suivante est sautée (deux 1ers puis un 3e), comme la feuille de résultats. La marche dit
  « = 1er ». Déplacer ensuite la carte la sort de l'ex aequo.
- **±1** (ce joueur seulement) : **molette** sur la carte, **▲ ▼** au survol, **clic droit** = -1, **Maj+clic** = +1.
- Clavier : ← → choisir une carte (cadre blanc), Maj+← → la déplacer, ↑ ↓ ±1, Espace = premier du banc sur la marche
  suivante, Suppr = renvoyer sur le banc, Page ↑ / ↓ = épreuve précédente / suivante, Entrée = valider.

### Toutes les épreuves
Épreuves en lignes × joueurs en colonnes (8 joueurs au plus ; au-delà, « 8 joueurs max : N non affichés »).
**Clic gauche** sur une case : +1, **clic droit** : -1 (une case vide part de 0 ; pour le barème, ouvrir l'épreuve).
Le **numéro** doré d'une épreuve l'ouvre. Molette : défiler. L'épreuve en cours est surlignée en vert, les cases du
brouillon hachurées.

## API StreamDeck
Une petite API HTTP dans le jeu de l'organisateur, sur **127.0.0.1** seulement (rien n'est accessible depuis un
autre PC). Désactivée par défaut : dans `config/craftmaster-client.json` (dossier de l'instance), section
`streamDeck` :

```json
"streamDeck": {"enabled": true, "port": 25710, "token": ""}
```

Redémarrer le jeu après modification. `token` facultatif : s'il est rempli, ajouter `?token=…` à chaque adresse
(ou l'en-tête `X-Token`). Un bouton StreamDeck = une adresse (plugin « API Request » ou « Website » en GET) :

| Adresse | Effet |
|---|---|
| `http://127.0.0.1:25710/menu` | Ouvre l'écran de saisie (le ferme s'il est ouvert) |
| `/ranking` | Ouvre le classement (le ferme s'il est ouvert) |
| `/undo` · `/redo` | Annule / refait la dernière modification (organisateurs : le serveur vérifie) |
| `/season/next` · `/season/prev` | Saison suivante / précédente dans l'écran de saisie ou de classement ouvert |
| `/points?player=Ensuri&delta=1` | Points pour l'épreuve en cours, **tout de suite** (pas de brouillon) ; plusieurs appuis rapides sur le même joueur partent en un seul « +3 » annoncé (0,8 s) ; `delta=-1` pour retirer |
| `/round/next` · `/round/start?manche=pont` · `/round/end` · `/timer/stop` | Épreuve suivante (titre, TP, chrono) · une épreuve précise · fin · arrêter le chrono |
| `/resume` · `/resume/force` | Show gelé après un crash : reprise (**deux appuis en 3 s**) ; refusée si un candidat manque, sauf `/resume/force` ([Épreuves](Epreuves.md#6-après-un-crash--le-show-gelé)) |
| `/publish` · `/reveal/start` | Publier les scores · lancer la révélation : **deux appuis en 3 s** (le premier répond `"armed": true`) |
| `/camera/cut?camera=2` · `/camera/auto` | Couper sur une caméra (numéro ou nom) · réalisation automatique (`?enabled=false` pour l'arrêter) |
| `/replay/toggle` · `back?s=10` · `forward?s=10` · `speed?x=2` · `marker/next` · `view?name=Ensuri` · `mode?m=auto` · … | Pendant un replay : le piloter (liste complète : [Replays](Replays.md#regarder-la-régie-de-replay)) ; `/camera/cut` et `/camera/auto` agissent alors sur le replay |
| `/tablet/start` · `/pause` · `/toggle` · `/reset` · `/restart` | Tablettes chrono (`?group=nom` : seulement ce groupe ; [Plateau](Plateau.md#tablette-chrono)) |
| `/clip/start` · `/clip/stop` · `/clip/toggle` · `/clip/status` | Clip de la vue de ce jeu ([Clips](Clips.md)) |
| `/tele?nom=gauche&clip=dernier` · `&lien=URL` · `&direct=Ensuri` · `&image=URL` · `&chat` · `&off` | Ce que montre une télé du studio ; `chat` = dernier clip Twitch posté par les viewers ([Clips](Clips.md)) |
| `/status` | JSON : connecté, organisateur, écran ouvert, saison affichée, annulations / refaire possibles |

Réponse toujours en JSON `{"ok": true}` ou `{"ok": false, "error": "…"}` (409 : pas connecté ou aucun écran ouvert,
403 : mauvais jeton ou requête venue d'une page web, 404 : adresse inconnue). `/undo` et `/redo` disent ce qu'ils
défont (`"did"`). Les requêtes d'une page web ouverte sur le PC (en-tête `Origin`) sont toujours refusées : seuls
les outils locaux (StreamDeck, Companion, curl) passent.

**Disposition conseillée (StreamDeck 15 touches, 5 × 3)** — une colonne par candidat :

| | Colonne 1 … 5 (un candidat chacune) |
|---|---|
| Rangée 1 | `/points?player=<pseudo>&delta=1` (sa tête en image) |
| Rangée 2 | `/points?player=<pseudo>&delta=-1` |
| Rangée 3 | `/round/next` · `/timer/stop` · `/menu` · `/undo` · `/publish` |

Une deuxième page pour la réalisation : `/camera/cut?camera=1` à `5`, `/camera/auto`, `/tablet/toggle`,
`/reveal/start`, `/ranking`. Un StreamDeck XL (8 × 4) tient 8 candidats sur une seule page.

**Choix pour le direct** : les points du StreamDeck (et de la voix) partent tout de suite, sans validation, parce
qu'il n'y a pas d'écran pour valider ; l'écran de saisie, lui, fait un brouillon. Rien n'est caché si ta vue passe à
l'antenne : les points donnés sont de toute façon annoncés à tous (« +3 » dans la barre d'action) ; seul le
classement publié reste secret, et l'écran de saisie ne le montre pas.

## Cadre joueur (HUD)
En haut à droite : sa propre tête et son total dessous (tête de chiffre de -10 à +10, nombre au-delà) pour **la
saison active** du serveur (ou une saison choisie dans les réglages),
mis à jour tout seul à chaque changement (les joueurs voient leur total **publié**, les organisateurs le direct) ;
un « +3 » apparaît quelques secondes à côté quand il change. Caché sans score, dans un écran, en mode diffusion et
avec F1, et pour les joueurs quand la saison est cachée. Réglages, section `playerFrame` de
`config/craftmaster-client.json` : `enabled` (true par défaut), `corner` (`top_left`, `top_right`, `bottom_left`,
`bottom_right`), `season` (`"active"` par défaut : suit la saison active, même quand elle change ; ou un numéro,
ex. `"4"`, pour rester sur une saison).

```json
"playerFrame": {"enabled": true, "corner": "top_right", "season": "active"}
```
