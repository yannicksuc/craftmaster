# Conception — Saisie des points et régie des organisateurs (UX)

Point de départ : LordFinn trouve l'écran de saisie (touche U) « pas hyper clair et facile à prendre en main ». Son
ancien plugin Paper avait « plein de petits trucs » (glisser-déposer, classer…) dont on n'a pas retrouvé le code
(l'historique git ne contient que la version commandes). Ce document diagnostique l'écran actuel, propose un modèle
d'interaction, une architecture de régie, une surface StreamDeck, et un plan par tranches.

Sources : `docs/wiki/Saisie.md`, `ScoreEntryScreen`, `ScoreBoard`, `StreamDeckServer`, `docs/design/emission.md`
(schéma d'actions §4 / §6), `Epreuves.md` (feuille de résultats), `Classement.md`, `Realisation.md`,
`RegieVocale.md`, captures t_a / t_b / t_f / t_g2 / u_a / r4 / u_c.

Principe directeur, inchangé : **le serveur est la seule source de vérité**. Écran, StreamDeck et voix ne font que
produire des listes d'actions (`score.set`, `score.add`, `manche.start`, `camera.cut`, `scores.publish`, `undo`…)
exécutées par `Actions` / `ActionExecutor`, une liste = une entrée du journal d'annulation.

---------------------------------------------------------------------------------------------------------------------

## 1. Diagnostic de l'écran actuel

### 1.1 Le geste principal n'est pas celui qu'on attend
- **Classer = insérer, pas échanger.** L'arbitre pense « Ensuri est arrivé 1er ». Aujourd'hui clic-clic = *échange* :
  mettre le 5e en 1er envoie le 1er en 5e. Pour reclasser 5 joueurs il faut raisonner en permutations. Seul Maj-clic
  insère, et seulement en tête — invisible (écrit dans une ligne d'aide).
- **Ce n'est pas du glisser-déposer.** On « prend » au clic, la tête suit la souris, on « pose » au clic suivant
  (t_b). Aucun marqueur d'insertion, aucune place qui s'écarte, la case vide reste à l'ancienne place : on ne voit
  pas ce qui va se passer avant de cliquer. Un clic droit « repose » (annule) — personne ne le devine.
- **+1 / −1 sur une autre place quand un joueur est pris = déplacer puis bonifier** : règle correcte mais
  indécouvrable ; en pratique c'est un piège (on voulait donner +1 à quelqu'un d'autre, on a déplacé le joueur pris).

### 1.2 La règle « base de la place + bonus perso » est invisible
- La tête de chiffre montre un total. Rien ne dit « 2 de la place + 1 de bonus ». En t_f, Notch est 4e avec 3 : on
  ne peut pas savoir pourquoi sans faire le calcul de tête.
- Le petit « +1 » / « −1 » de bonus est dessiné au coin haut-droit de la tête, **à cheval sur le voisin** (t_f : le
  « +1 » de Notch semble appartenir à Bob ; le « −1 » de LordFinn colle à la tête de Steve).
- La base appartient à la *place* mais rien ne matérialise la place : les « #1 #2 #3 » sont de petits textes au-dessus
  des boutons +1, loin des têtes. La place n'a pas d'objet visuel qui « reste » pendant que le joueur bouge.

### 1.3 Une manche jamais notée ressemble à une manche déjà notée
- À l'ouverture d'une manche vide (t_a), les joueurs sont dans l'ordre de la saison et le barème 5-4-3-2-1 est déjà
  distribué : **LordFinn « +5 » simplement parce qu'il est premier dans la liste**. Avec « 5 change(s) not sent » en
  dessous. Sous pression, un réflexe « Mettre à jour » donne les points dans l'ordre alphabétique / d'arrivée en jeu.
- Il manque un état « pas encore classé » : un banc d'où l'on tire les joueurs dans l'ordre d'arrivée.

### 1.4 Envoyer, annuler, fermer : trois risques de perte ou d'erreur
- **« Mettre à jour les scores »** est exactement la phrase vocale de **publier** (« mettez à jour les scores » →
  `scores.publish`). Le même mot désigne deux choses opposées (envoyer un brouillon en direct / révéler aux joueurs).
- **« Annuler » agit sur le journal du serveur alors qu'un brouillon local existe** : avec 4 changements non envoyés,
  « Annuler » défait l'envoi *précédent* (une autre manche, voire une coupe caméra), pas le dernier geste. Le bouton
  ne dit pas ce qu'il va défaire. Il n'y a aucun Ctrl+Z local.
- **« Terminé » / Échap / U / `/menu` du StreamDeck ferment sans rien demander et jettent le brouillon** (aucun
  `onClose` gardé). Le changement de saison est bloqué par un simple son d'erreur, sans message.

### 1.5 Densité, hiérarchie, vocabulaire
- 16 boutons +1/−1 permanents (8 × 2) : le bruit visuel domine, les visages (l'information) sont noyés.
- Deux rangées de ◀ ▶ identiques (saison / manche) l'une sous l'autre ; « 8 players max: 5 not shown » occupe le
  bandeau du titre ; onglets « All rounds / Round » peu parlants ; la ligne d'aide déborde et touche les « #1 ».
- **L'or veut dire quatre choses** : 1re place, case modifiée (u_a), joueur pris (t_b), bonus. Le vert « en cours »
  est le seul code d'état clair.
- Vue « Toutes les épreuves » : pas de colonne **total**, pas d'écart **publié / direct**, et un clic gauche part de
  0 alors que la vue Épreuve part du barème : deux règles différentes pour la même case.

### 1.6 Contexte du direct ignoré
- Le panneau couvre tout le jeu : l'arbitre ne voit plus les candidats pendant qu'il note.
- L'écran montre les scores **non publiés** : si la vue de LordFinn est diffusée, c'est un spoiler. Rien ne le signale.
- Aucun lien avec la feuille de résultats (R), qui fait pourtant le même travail (mesures → places → barème), ni avec
  « publier » / « révéler », ni avec la manche suivante. Chaque outil est une île.

---------------------------------------------------------------------------------------------------------------------

## 2. Qui, quand, sous quelle pression

| Situation | Qui | Pression | Outil naturel |
|---|---|---|---|
| **Direct, fin de manche** | LordFinn (joue et anime) ou un orga | Très forte : on parle au public, on ne réfléchit pas | StreamDeck, voix, écran en tiroir |
| **Direct, pendant la manche** (bonus, pénalité, « +1 pour la blague ») | idem | Forte, geste réflexe | StreamDeck, voix |
| **Correction** (« non, c'était Cyllu ») | idem | Forte et stressante (le public voit le « +3 » annoncé) | Annuler visible, écran |
| **Préparation / vérification** (avant, entre deux parties) | orga | Faible | Écran complet, tableau |
| **Réalisation** (plans, manche suivante, publier, révélation) | LordFinn ou réalisateur | Forte, rythme TV | StreamDeck d'abord |

Tâches cœur et budget :

| # | Tâche | Budget |
|---|---|---|
| T1 | Classer une manche finie (5 joueurs, barème) et l'envoyer | ≤ 10 s, ≤ 7 gestes (ouvrir, 5 têtes dans l'ordre, Valider), sans lire d'aide |
| T2 | +1 / −1 à un joueur dans la manche en cours | 1 appui StreamDeck (< 1 s), ou 2 gestes à l'écran |
| T3 | Corriger la dernière erreur | 1 geste, en sachant **ce qui** sera défait avant d'appuyer |
| T4 | Enchaîner : fin de manche → publier → manche suivante | 1 touche chacune, publier / révéler protégés contre l'appui accidentel |
| T5 | Changer de plan caméra | 1 touche, latence imperceptible |
| T6 | Vérifier toute la saison (totaux, trous, non publié) | 1 écran, lisible d'un coup d'œil |

---------------------------------------------------------------------------------------------------------------------

## 3. Modèle d'interaction de la saisie

### 3.1 La métaphore : des marches de podium et des jetons
- Chaque **place** est une **marche** fixe, avec sa base peinte dessus (« 1er · 5 »). Elle ne bouge jamais.
- Chaque **joueur** est une **carte** (tête + pseudo) posée sur une marche.
- Le **bonus perso** est un **jeton** accroché sous la carte (« +1 » froid / « −1 » chaud) : il voyage avec la carte.
- Sous la marche : **= total de la manche** (tête de chiffre), calculé marche + jeton.
- Ainsi la règle validée se lit sans explication : on déplace la carte, elle prend la base de sa nouvelle marche,
  son jeton la suit. Survol d'une carte : infobulle « 2 (4e place) + 1 (bonus) = 3 ».

### 3.2 Manche jamais notée : le banc
Une manche sans score s'ouvre avec les **marches vides** (barème affiché dessus) et les joueurs **sur le banc** (à
droite, grisés, ordre de la saison). Rien n'est en brouillon tant qu'on n'a rien fait (plus de « 5 changes » fantômes).
- **Clic sur une tête du banc = « suivant »** : elle monte sur la première marche libre (animation de saut, son
  dont la hauteur dépend de la place, la marche « s'allume »). Cinq clics dans l'ordre d'arrivée = T1.
- Un joueur laissé sur le banc à la validation : **son score n'est pas touché** (comme « sans valeur » dans la
  feuille de résultats). Bouton « Le reste ex aequo dernier » si on veut leur donner la dernière marche.
- Une manche déjà notée s'ouvre triée par score, banc vide (comportement actuel de `ScoreBoard.of`).

### 3.3 Glisser-déposer (le geste de correction)
- **Appuyer-glisser** une carte (seuil 3 px pour distinguer du clic) : elle se soulève (ombre, légère inclinaison
  de 3–4° selon la vitesse, échelle 1,08), son emplacement d'origine devient un contour pointillé.
- **Marqueur d'insertion** : barre verticale dorée entre deux cartes ; les autres cartes **s'écartent** en douceur
  (interpolation ~120 ms par image, `extractRenderState` + position cible). Ce qu'on verra au lâcher est visible
  avant de lâcher (les totaux des marches se recalculent en aperçu, en italique).
- **Lâcher entre deux cartes = insérer** (les autres se décalent : `ScoreBoard.moveTo`). Lâcher **sur une carte** =
  **ex aequo** (voir question Q3) ; lâcher **sur le banc** = retirer du classement ; lâcher hors de la zone = retour
  à l'origine (animation élastique, son « drop » grave).
- L'échange (swap) disparaît de la souris (cas rare ; reste au clavier : Alt+←/→).
- Technique : `mouseClicked` mémorise la carte, `mouseDragged(MouseButtonEvent, dx, dy)` passe en glisser au-delà
  du seuil, `mouseReleased` décide ; positions animées stockées par joueur côté écran (pas dans `ScoreBoard`).

### 3.4 Points rapides (bonus) sans boutons permanents
- **Molette sur une carte = ±1** (geste le plus rapide, 0 visée). **Clic droit = −1**, **Maj+clic = +1** en
  secours.
- Au survol (ou carte sélectionnée), deux petites flèches ▲ ▼ apparaissent sur les flancs de la carte — elles
  remplacent les 16 boutons permanents.
- Juice : le jeton « pop » (échelle 1,3 → 1), la tête de chiffre du total défile de l'ancienne à la nouvelle valeur,
  son `UiSounds.score` déjà en place (hauteur selon la valeur).
- Le jeton n'apparaît que s'il est ≠ 0 ; il est **sous** la carte, jamais au coin (plus de chevauchement).

### 3.5 Clavier (écran Direct)
| Touche | Effet |
|---|---|
| ← → | sélectionner la carte voisine (cadre blanc) |
| Maj + ← → | déplacer la carte sélectionnée (insertion) |
| ↑ ↓ | ±1 de bonus à la carte sélectionnée |
| Espace | banc : monter le joueur sélectionné sur la marche libre suivante |
| Suppr | renvoyer la carte sur le banc |
| Entrée | **Valider la manche** |
| Ctrl+Z / Ctrl+Y | défaire / refaire **le dernier geste du brouillon** ; brouillon vide → annuler / refaire **l'envoi** (avec confirmation de ce qui sera défait, voir 3.7) |
| Page ↑ / Page ↓ | manche précédente / suivante |
| Tab | onglet suivant de la régie |
| Échap | fermer (le brouillon est **gardé**) |
La barre du bas affiche en permanence 4–5 de ces raccourcis selon le contexte (pas de ligne d'aide en tête).

### 3.6 Ce qui est en direct, ce qui est en brouillon (une seule règle par outil)
| Outil | Comportement | Pourquoi |
|---|---|---|
| **Écran, onglet Direct** | **Brouillon** de la manche affichée, puis **Valider la manche** (Entrée) = une liste de `score.set` | Les états intermédiaires d'un classement n'ont pas de sens et chaque changement est annoncé à tout le monde (« +3 » dans la barre d'action) |
| **Écran, onglet Tableau** | Brouillon aussi (mêmes règles, mêmes couleurs), validé par le même bouton | Une seule règle à l'écran |
| **StreamDeck, voix** | **Direct**. Appuis répétés sur le même joueur groupés pendant 0,8 s → un seul `score.add {points: n}` | Pas d'écran pour valider ; un seul « +2 » annoncé au lieu de « +1 », « +1 » |
- Le brouillon se voit : cartes / cases en **jaune hachuré** (le jaune hachuré ne sert qu'à ça), compteur
  « Brouillon : 4 changements » à côté du bouton Valider, qui n'est actif que s'il y a quelque chose.
- **Changement venu d'ailleurs pendant un brouillon** (StreamDeck, voix, autre orga) sur un joueur de la manche
  brouillonnée : l'écart s'ajoute **à son jeton** (le bonus suit le joueur, la règle reste vraie) et la carte porte
  un petit repère « ⚡ » 3 s. Rien n'est perdu, rien n'est écrasé en silence.
- Le bouton s'appelle **« Valider la manche »** (jamais « mettre à jour », réservé à publier). « Publier » n'est
  jamais dans cet onglet (il est dans Classement et sur le StreamDeck), pour qu'on ne confonde pas les deux.

### 3.7 Annuler : local d'abord, serveur ensuite, toujours étiqueté
- Pile locale des gestes du brouillon (Ctrl+Z / Ctrl+Y, bouton « ↶ geste »).
- Annuler côté serveur affiche **ce qui sera défait** : « ↶ Annuler : Château — 5 scores (il y a 2 min) »
  (la description vient du journal, déjà exposée par `history[]` du contexte). Le serveur reste juge (organisateur).
- Ligne « Dernier envoi » en bas : ce que le serveur a fait au dernier `Valider` (retour de `Result.lines`),
  pour confirmer sans lire le chat.

### 3.8 Fermer en sécurité
- Le brouillon **survit à la fermeture** (gardé dans le client, par saison + manche, jusqu'à Valider, « Tout
  effacer » ou déconnexion). Rouvrir U le retrouve tel quel. Pas de boîte de dialogue au moment du stress.
- Rappel discret dans le cadre joueur (HUD, organisateurs) : « ✎ brouillon : Château » tant qu'il en reste un.
- Changer de saison avec un brouillon : message « Brouillon en cours sur Château — Valider ou effacer d'abord »
  au lieu d'un son seul.

### 3.9 Deux tailles : tiroir (direct) et plein écran (préparation)
- **Tiroir** (défaut quand une manche est en cours) : bande en bas de l'écran, ~45 % de la hauteur, le jeu reste
  visible au-dessus. Le jeu n'est pas en pause (déjà le cas).
- **Plein écran** : onglets Tableau, Feuille, Manches, Classement. Touche F (ou icône ⤢) pour basculer.
- Avertissement de diffusion : petit cartouche « ● scores non publiés visibles » dans le coin du panneau quand
  l'écran montre du non publié — à garder si la vue de LordFinn passe à l'antenne (voir Q2).

### 3.10 Maquettes

Tiroir Direct, manche notée en cours de correction (GUI ≈ 427 × 240 à l'échelle 2) :
```
                         (le jeu reste visible au-dessus)
┌ RÉGIE · S3 · Partie 1 ── Manche 3/5 « Château » ● en cours 02:41 ── [3 non publiés] ── ⤢ ┐
│  [Direct] Tableau  Feuille  Manches  Classement                         ◀ manche ▶ │
│                                                                                    │
│    ┌──────┐  ┌──────┐    ┊    ┌──────┐  ┌──────┐  ┌──────┐   │ BANC              │
│    │ [@@] │  │ [@@] │    ┊    │ [@@] │  │ [@@] │  │ [@@] │   │  (vide)           │
│    │Ensuri│  │ Finn │    ┊    │Cyllu │  │Arreau│  │JustEd│   │                   │
│    └──┬───┘  └──┬───┘    ┊    └──┬───┘  └──┬───┘  └──┬───┘   │                   │
│       │       (+1)       ┊      (−1)       │         │       │                   │
│   ▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄  insertion ▄▄▄▄▄▄  ▄▄▄▄▄▄  ▄▄▄▄▄▄                           │
│   █ 1er 5 █ █ 2e  4 █          █ 3e 3 █ █ 4e 2 █ █ 5e 1 █                           │
│     = 5       = 5                = 2      = 2      = 1                             │
│                                                                                    │
│ ↶ geste  Ctrl+Z · molette ±1 · Maj+←→ déplacer      Brouillon : 2   [✔ Valider ⏎]   │
│ Dernier envoi : Le Pont — 5 scores (il y a 4 min)        [↶ Annuler l'envoi] [↷]   │
└────────────────────────────────────────────────────────────────────────────────────┘
```

Manche jamais notée (ordre d'arrivée) :
```
│   ▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄   │ BANC — clique dans l'ordre   │
│   █ [@@]  █ █  ?   █ █  ?   █ █  ?   █ █  ?   █   │  [@@]Finn  [@@]Cyllu         │
│   █1er  5 █ █2e  4 █ █3e  3 █ █4e  2 █ █5e  1 █   │  [@@]Arreau [@@]JustEd       │
│   Ensuri                                          │  [Le reste ex aequo dernier] │
```

Tableau (plein écran) — totaux et non publié en plus de l'existant :
```
┌ RÉGIE · S3 ─ Direct [Tableau] Feuille Manches Classement ───────────────────────────┐
│  Manche              Ensuri  Finn  Cyllu  Arreau  JustEd                           │
│  1  Le Pont           +5     +4     +3     +2     +1                               │
│  2  Final             +4     +5      ·     +3     +2                               │
│ ●3  Château           +5    [+5]    +2     +2     +1     ← case brouillon hachurée │
│  ─────────────────────────────────────────────────────                              │
│  Total direct          14     14     5      7      4                               │
│  Publié                 9      9     3      5      3     (écart = non publié)       │
│  clic +1 · clic droit −1 · n° de manche = l'ouvrir dans Direct   [✔ Valider ⏎]      │
└────────────────────────────────────────────────────────────────────────────────────┘
```
Règle unifiée : une case vide cliquée dans le Tableau part de 0 (c'est un bonus isolé) ; pour donner un barème,
on ouvre la manche (son numéro) — et le Tableau le dit dans sa barre d'aide.

---------------------------------------------------------------------------------------------------------------------

## 4. Architecture de la régie (back-office)

### 4.1 Un hub, des raccourcis
Un seul écran **Régie** à onglets, mêmes en-tête et pied partout. Les touches existantes restent des raccourcis
vers un onglet (personne ne réapprend) :

| Onglet | Contenu | Ouvert par | Existant repris |
|---|---|---|---|
| **Direct** | La manche en cours : marches, banc, bonus (tiroir) | U, StreamDeck `/menu` | vue « Épreuve » de `ScoreEntryScreen` refaite |
| **Tableau** | Manches × joueurs, totaux, publié | U puis Tab | vue « Toutes les épreuves » |
| **Feuille** | Mesures → places (feuille de résultats) | R | `ResultsScreen` (contenu gardé tel quel) |
| **Manches** | Ordre des manches, lancer / finir / suivante, chrono, intro | (nouveau) | actions `manche.*`, `timer.stop`, `intro.play` |
| **Classement** | Totaux direct vs publié, **Publier**, **Révélation** | K pour un orga ouvre ici ; K joueur = écran actuel | `RankingScreen` |
La réalisation caméra **ne va pas dans le hub** au début : elle vit sur le StreamDeck, la voix et le client de
diffusion (touches 1–9). Un onglet « Plateau » (caméras, rideaux, projecteurs) pourra venir plus tard.

### 4.2 Fusion Feuille ↔ Direct
Deux chemins mènent aujourd'hui aux mêmes `score.set` (barème de la feuille, classement manuel). On en fait un :
« Appliquer le barème » de la feuille **remplit le brouillon de l'onglet Direct** (ordre, ex aequo, DQ sur le banc)
et y bascule ; on valide au même endroit. (La voix `results.apply` reste directe.)

### 4.3 En-tête et pied communs (« bandeau de régie »)
- En-tête : saison · partie · manche en cours + chrono · pastille **« N non publiés »** · pastille brouillon ·
  voyant **StreamDeck** (vert si une requête depuis < 10 s) · ⤢ taille.
- Pied : raccourcis du contexte · **Valider** (si brouillon) · **Dernier envoi** + **Annuler l'envoi** étiqueté.

### 4.4 Composants à ajouter au kit `client/ui`
| Composant | Rôle |
|---|---|
| `SortableRow` | rangée triable horizontale : glisser, seuil, marqueur d'insertion, cartes qui s'écartent, dépôt sur carte / banc |
| `Pedestal` | marche de podium (place, base, couleur or / argent / bronze / neutre) |
| `BonusChip` | jeton ±N sous une carte, animation « pop » |
| `Tween` | interpolation de positions / valeurs par joueur, indépendante des widgets reconstruits |
| `RegieHeader` / `RegieFooter` | bandeau et pied communs |
| `HoldButton` | bouton à maintenir 0,6 s (jauge qui se remplit) pour Publier, Révélation, Supprimer |
| `Toast` | ligne de retour serveur en bas à droite (« ✔ Château : 5 scores » / « ✗ … ») avec ↶ |
| `KeyHints` | rappel des raccourcis du contexte |
| `Draft` (modèle client) | brouillon par saison+manche : `ScoreBoard` + pile de gestes + rebase sur changement serveur |

### 4.5 Règles de cohérence
1. **Couleurs d'état** : or / argent / bronze = podium seulement ; jaune hachuré = brouillon non envoyé ; vert =
   en cours / à l'antenne ; violet pastille = non publié ; rouge = erreur, action dangereuse. Les têtes de chiffre
   gardent leur échelle froid / chaud pour les valeurs.
2. **Verbes réservés** : *Valider* = envoyer un brouillon ; *Publier* = montrer aux joueurs ; *Révéler* = publication
   mise en scène ; *Annuler* = toujours suivi de ce qui sera défait. « Mettre à jour » n'est plus utilisé.
3. Toute action dangereuse ou visible du public (publier, révéler, supprimer, manche suivante) : `HoldButton` à
   l'écran, double appui sur le StreamDeck.
4. ◀ ▶ = seulement pour la manche ; la saison se choisit dans un menu déroulant de l'en-tête (rarement changée).
5. Toute modification passe par `RunActions` (un aller, un retour `Result`), jamais par un paquet dédié.

---------------------------------------------------------------------------------------------------------------------

## 5. Surface StreamDeck

### 5.1 Toujours joignable
- API activée **par défaut pour les organisateurs** (le serveur dit déjà si l'on est organisateur), port fixe
  25710 (repli 25711–25719 si pris, écrit dans `/status` et dans le journal du jeu), réglages relus sans redémarrer.
- `/status` et `/state` répondent **même déconnecté** (`connected: false`) pour que les touches affichent « hors
  ligne » au lieu de rester muettes ; les actions renvoient 409 avec une raison lisible.
- Elle tourne dans **tout** client du mod : on peut brancher le StreamDeck sur le PC de jeu de LordFinn ou sur le PC
  de diffusion (compte caméra avec `craftmaster:camera` pour les coupes ; points / manches refusés par le serveur
  si ce n'est pas un organisateur).

### 5.2 Sécurité (127.0.0.1 ne suffit pas)
Une page web ouverte dans le navigateur du PC peut appeler `http://127.0.0.1:25710/undo` (balise image, `fetch`
sans CORS) : aujourd'hui, n'importe quel site visité pendant le direct peut annuler un score.
- **Jeton obligatoire**, généré au premier lancement (`streamDeck.token` aléatoire), copiable depuis le hub
  (bouton « Copier le lien StreamDeck ») ; en-tête `X-Token` ou `?token=`.
- Refuser toute requête qui porte un en-tête `Origin` (sauf liste blanche vide par défaut) ; actions en **POST**
  (GET gardé pour `/status`, `/state`, images, et compatibilité des anciens boutons pendant une version).
- Écoute toujours sur 127.0.0.1 uniquement.

### 5.3 API (v2)
Toutes les actions traduisent vers le schéma existant et passent par `RunActions` ; le serveur vérifie les droits.
Réponse `{"ok":true,"lines":[…]}` / `{"ok":false,"error":"…"}`.

| Adresse (POST sauf mention) | Action(s) serveur |
|---|---|
| `/action` (corps = liste JSON d'actions) | passe-plat générique (tout le schéma) |
| `/points?player=Ensuri&delta=1[&manche=current]` | `score.add {player, points}` (groupé 0,8 s par joueur) |
| `/points/set?player=…&value=…` | `score.set` |
| `/arrival?player=…` · `/arrival/undo` · `/arrival/clear` · `/arrival/commit` | ordre d'arrivée : remplit le brouillon Direct ; `commit` = liste de `score.set` au barème |
| `/round/start?manche=next\|<id>` · `/round/end` · `/timer/stop` | `manche.start`, `manche.end`, `timer.stop` |
| `/camera/cut?camera=2\|studio:2\|next\|prev[&transition=fondu]` | `camera.cut {camera, transition}` |
| `/camera/auto?enabled=toggle` · `/camera/mosaic?enabled=toggle` | `camera.auto`, `camera.mosaic` |
| `/camera/follow?player=…` · `/camera/zone?zone=next\|prev\|<nom>` · `/camera/path?path=…` | `camera.follow`, `camera.focus {zone}`, `camera.path` |
| `/publish` · `/reveal/start` · `/reveal/stop` | `scores.publish`, `reveal.*` — **double appui** : le 1er arme 3 s (son + toast « Appuie encore pour publier »), le 2e exécute |
| `/curtain/open?name=` · `/curtain/close` · `/buzzer/reset` · `/spotlight?player=` | `curtain.*`, `buzzer.reset`, `spotlight.follow` |
| `/undo` · `/redo` | `undo` / `redo` (réponse = description de ce qui a été défait) |
| `/menu?tab=direct\|tableau\|feuille\|manches\|classement` · `/ranking` | ouvrir / fermer l'écran |
| GET `/state[?since=<version>]` | état pour les images des touches (longue attente jusqu'à 10 s si `since` donné) |
| GET `/img/face/<pseudo>.png[?score=…]` | tête 72 × 72 (option : tête de chiffre incrustée) pour les touches |

`/state` (compact, versionné) :
```
{v, connected, organiser, season:{id,name}, manche:{id,number,name,status,timer}, next:{id,name},
 players:[{name, round, total, published, place}], draft:{manche, changes},
 camera:{onAir:"studio:2", label, auto, mosaic, focus:{lieu, zone}, cams:[{n,label,onAir}]},
 undo:{count, label}, redo:{count, label}, unpublished, reveal:{running}, armed:"publish"|null}
```
Il se calcule côté client à partir de ce qu'il reçoit déjà (plateau de saisie, statut régie) ; ce qui manque
(caméra à l'antenne, chrono) demande un petit paquet d'état serveur → client (organisateurs).

### 5.4 Images des touches : trois niveaux
1. **Jour 1** : plugin Elgato « API Request » (ou « Website » en GET pendant la transition), icônes fixes fournies
   dans `tools/streamdeck/` (profil `.streamDeckProfile` prêt à importer).
2. **Cible** : petit plugin StreamDeck maison (`tools/streamdeck-plugin`, SDK Elgato en Node/TS) qui écoute `/state`
   et dessine : tête + score de manche sur les touches joueurs, voyant rouge sur la caméra à l'antenne, chrono,
   « Confirmer ? » sur une touche armée, gris « hors ligne ». Gère aussi l'appui long nativement.
3. Alternative à évaluer : Bitfocus Companion (pages, variables, retour d'état par requêtes HTTP) si LordFinn
   l'utilise déjà — à vérifier sur sa version.

### 5.5 Disposition 15 touches (5 × 3) — cinq colonnes = cinq candidats
**Page DIRECT** (accueil)
```
┌──────────┬──────────┬──────────┬──────────┬──────────┐
│ ▶ MANCHE │ ■ FIN    │ ⏱ STOP   │ ✎ SAISIE │ PUBLIER  │   déroulé (manche suivante et publier : double appui)
│ suivante │ manche   │ chrono   │ (U)      │ ×2       │
├──────────┼──────────┼──────────┼──────────┼──────────┤
│ CAM 1    │ CAM 2    │ CAM 3    │ LARGE    │ AUTO     │   plans (rouge = à l'antenne ; AUTO vert/gris)
├──────────┼──────────┼──────────┼──────────┼──────────┤
│ → POINTS │ → ARRIVÉE│ → CAMÉRAS│ ↶ ANNULER│ ↷ REFAIRE│   navigation + correction (Annuler affiche ce qu'il défera)
└──────────┴──────────┴──────────┴──────────┴──────────┘
```
**Page POINTS** (manche en cours, nom sur la touche « ← »)
```
│ [@@] Ens │ [@@] Fin │ [@@] Cyl │ [@@] Arr │ [@@] Jus │   +1 (tête + score de manche)
│   −1     │   −1     │   −1     │   −1     │   −1     │
│ ← DIRECT │ +3 mode  │ ✎ SAISIE │ ↶ ANNULER│ PUBLIER×2│   « +3 mode » : bascule +1 → +3 (touche colorée tant qu'actif, revient seul à +1 après 10 s)
```
**Page ARRIVÉE** (classer sans écran)
```
│ [@@] Ens │ [@@] Fin │ [@@] Cyl │ [@@] Arr │ [@@] Jus │   appui = place suivante (la touche affiche « 1er », « 2e »…)
│ ex aequo │ ↶ retirer│ tout     │          │ ✔ VALIDER│   ex aequo : le prochain appui partage la place précédente
│ ← DIRECT │          │ effacer  │          │ (barème) │
```
**Page CAMÉRAS**
```
│ CAM 1    │ CAM 2    │ CAM 3    │ CAM 4    │ CAM 5    │   caméras du lieu actif (libellé depuis /state)
│ LARGE    │ TRÔNE    │ PODIUM   │ MOSAÏQUE │ ZONE ▶   │
│ ← DIRECT │ SUIVRE…  │ FONDU    │ AUTO     │ TRAJET   │   SUIVRE… ouvre une rangée de têtes ; FONDU = transition du prochain plan (une fois)
```
Plus de 5 joueurs : la page Points passe en 2 pages (▶ joueurs 6–8).

### 5.6 StreamDeck XL (32 touches, 8 × 4) — huit colonnes = 8 joueurs max de l'écran
Rangée 1 : têtes +1 · rangée 2 : −1 · rangée 3 : CAM 1–5, LARGE, MOSAÏQUE, AUTO · rangée 4 : ▶ MANCHE, ■ FIN,
⏱, ARRIVÉE, ✎ SAISIE, ↶, ↷, PUBLIER×2. Une seule page pour tout le direct.

### 5.7 StreamDeck+ (molettes)
Molette 1 : tourner = choisir le joueur (l'écran tactile montre sa tête), appuyer = +1, tourner en appuyant = ±1.
Molette 2 : tourner = présélection de caméra, appuyer = couper. Molette 3 : manche précédente / suivante en
présélection, appuyer longuement = lancer. Molette 4 : parcourir le journal, appuyer = annuler jusque-là (avec
libellé).

---------------------------------------------------------------------------------------------------------------------

## 6. Plan par tranches (chacune utilisable de bout en bout)

| # | Tranche | Effort | À tester en jeu |
|---|---|---|---|
| **1** | **Saisie claire, même écran** : marches + jetons sous les cartes, banc pour manche vide (clic = place suivante), glisser-insérer avec marqueur et écartement, molette ±1, boutons +1/−1 permanents retirés, « Valider la manche » (Entrée), Ctrl+Z local, Annuler serveur étiqueté, brouillon gardé à la fermeture, couleurs d'état | **M** | Classer 5 joueurs d'une manche vide en < 10 s ; reclasser le 5e en 1er en un glisser ; fermer avec Échap puis rouvrir (brouillon là) ; Ctrl+Z ; Annuler montre la bonne description ; 8 joueurs à l'échelle 2 en 854 × 480 |
| 2 | **StreamDeck v2** : jeton obligatoire + refus d'`Origin`, POST, `/points` groupé, `/round/*`, `/camera/*`, `/publish` à double appui, `/arrival/*`, `/undo` décrit, `/status` hors ligne ; profil 15 touches à icônes fixes | **M** | Chaque touche de la page Direct ; double appui Publier ; +1 ×3 rapide = un seul « +3 » annoncé ; requête depuis une page web refusée ; jeu relancé → touches reprennent sans rien faire |
| 3 | **Touches vivantes** : `/state` (+ paquet d'état caméra / chrono), `/img/face`, plugin StreamDeck maison (têtes + scores, voyant caméra, armé, hors ligne) | **M/L** | Coupe faite par la voix → la touche caméra passe rouge < 1 s ; score donné à l'écran → touche joueur à jour |
| 4 | **Hub Régie** : en-tête / pied communs, onglets Direct (tiroir) / Tableau (totaux, publié) / Feuille / Classement (Publier, Révélation en `HoldButton`), Feuille → brouillon Direct, rebase « ⚡ » des changements venus d'ailleurs | **L** | Feuille de mesures → barème → Direct → Valider ; +1 au StreamDeck pendant un brouillon → jeton +1 et repère ⚡ ; Publier depuis Classement |
| 5 | **Finitions** : animations (inclinaison, élastique, défilement des chiffres), sons par place, ex aequo par dépôt sur carte, onglet Manches, StreamDeck XL / + | **S/M** chacune | Sensation (LordFinn joue avec 5 min sans explication), ex aequo identiques à la feuille de résultats |

Tests unitaires (logique pure, `fr.lordfinn.craftmaster.data`) : `Draft` (gestes, défaire, rebase d'un changement
serveur sur le jeton), placement depuis le banc, ex aequo (mêmes règles que la feuille), groupement des `+1`.

---------------------------------------------------------------------------------------------------------------------

## 7. Questions ouvertes pour LordFinn

1. **L'ancien plugin** : as-tu des captures, une vidéo d'un ancien direct, ou le code de son menu (glisser-déposer,
   classement) ? On reprendra ses idées telles quelles plutôt que de les réinventer.
2. **Ta vue de jeu passe-t-elle à l'antenne** pendant que tu notes (ou seulement le client de diffusion) ? Si oui,
   il faut un écran qui ne montre pas le non publié (ou une scène OBS basculée par le StreamDeck).
3. **Ex aequo en saisie manuelle** : déposer une carte sur une autre = même place, mêmes points, et la place
   suivante sautée (comme la feuille de résultats) — ça te va ?
4. **Joueur non classé** dans une manche (resté sur le banc) : score non touché (proposé) ou 0 ?
5. **StreamDeck** : quel modèle (15, XL 32, StreamDeck+) et quel logiciel (Elgato, Companion) ? Branché sur ton PC
   de jeu ou sur le PC de diffusion ?
6. **Direct ou brouillon** : les +1 du StreamDeck partent tout de suite (proposé, groupés 0,8 s) — ou préfères-tu
   aussi les valider ?
