# Épreuves : objets d'épreuve, arbitrage, créativité

Tout ce qu'il faut pour **monter**, **jouer** et **arbitrer** une épreuve en jeu, comme dans une émission de
tâches : des objets avec lesquels les candidats interagissent, les outils du juge (feuille de résultats, chrono,
mètre, compteur, balance, zone) et des objets pour laisser libre cours à l'imagination (tableaux à
dessiner, socle d'exposition, objets nommés). Tout est dans l'onglet créatif CraftMaster.

« Candidats » = les joueurs (non archivés) de la **saison active**. « Organisateurs » = permission
`craftmaster:admin` ou opérateur.

## 1. Objets d'épreuve (props)

Un **objet d'épreuve** est n'importe quel bloc ou objet, à la taille et à l'angle voulus, qui fait quelque chose
quand un candidat l'utilise (clic droit **ou** coup).

- **Poser** : la *Baguette d'objet d'épreuve* (organisateurs) sur un bloc. L'objet prend l'apparence de ce que
  tu tiens dans l'autre main (une cloche sinon) et ses réglages s'ouvrent.
- **Régler** : accroupi + clic droit sur l'objet (ou la baguette, ou `/craftMasterTrial prop` en le regardant).
  Quatre onglets, avec infobulles :
  - *Apparence* : nom (affiché au-dessus et dans les messages), taille (0,1 à 8 blocs), rotation, inclinaison,
    tourne sur lui-même, flotte, brille ; « Apparence = l'objet que je tiens » (tête de joueur, objet renommé…).
  - *Effets* : message (`{player}`, `{count}`, `{total}`), au joueur ou à tous ; son (liste) et sa hauteur ;
    objet donné (identifiant + nombre, ou « Main » = copie exacte de l'objet tenu) ; note (titre + texte, ouverte
    sur l'écran du joueur — chantier D, en attendant un message dans le chat).
  - *Actions* : téléportation (x y z, « Ici » = là où tu es) ; signal redstone (un bloc de redstone apparaît
    N ticks à un décalage de l'objet, par défaut 2 blocs dessous, **seulement là où il y a de l'air** — mettre le
    câblage sous le sol) ; « Lance l'essai » / « Termine l'essai » (chrono du joueur, voir §3).
  - *Règles* : une fois par joueur, délai entre deux utilisations, candidats seulement, annoncer le premier (titre
    pour tous) ; **mémoire** : nombre d'utilisations par joueur, qui l'a touché **en premier et à quelle heure**,
    l'ordre de passage. Remise à zéro, suppression.
  - « Tester » enregistre et l'utilise comme un candidat.
- Les organisateurs reçoivent « ★ Ensuri a touché « Le trésor » en premier » ; tout est dans le journal
  (`/craftMasterTrial log`).
- Un objet d'épreuve ne casse jamais (on le supprime depuis ses réglages).

**Gros bouton rouge** : un socle noir et or avec un dôme rouge. Appuyé (clic ou coup), il descend une seconde,
donne un signal redstone (fort dans le bloc dessous), et les organisateurs savent qui a appuyé (le premier depuis
`/craftMasterTrial reset` est marqué).

**Buzzers** (16 couleurs, une par candidat) : le premier qui buzze allume son buzzer (lumière + signal redstone),
un titre annonce « Ensuri a buzzé en premier ! » et les autres sont bloqués jusqu'à la remise à zéro ; les suivants
sont quand même notés (2e, 3e, avec l'écart en secondes) : `/craftMasterBuzzer status`. Remise à zéro :
`/craftMasterBuzzer reset`, la voix (« remets les buzzers »), un organisateur accroupi + clic droit sur un buzzer, ou un
**signal redstone** qui arrive dans un buzzer (bouton, levier, pédale du juge : au front montant).

**Modes des buzzers** (`/craftMasterBuzzer mode` : un menu cliquable dans le chat ; gardé avec les données de
l'émission, annulable) :

| Mode | Id | Effet |
|---|---|---|
| Premier qui buzze | `first` | Par défaut : le premier allume son buzzer et bloque les autres jusqu'à la remise à zéro |
| Tous dans l'ordre | `order` | Chacun buzze une fois, tous les buzzers s'allument, l'ordre d'arrivée s'affiche dans le chat |
| Réarmement automatique | `auto` | Comme « premier », puis les buzzers se réarment seuls après le délai (`/craftMasterBuzzer rearm <secondes>`, 5 par défaut) |
| Libre | `free` | Chaque appui fait clignoter son buzzer une seconde et sonne, sans blocage ni ordre |

## 2. Feuille de résultats et barème

Chaque manche a sa **feuille de résultats** : une valeur par candidat (temps, nombre, distance, note du jury,
poids) ou « disqualifié ». Le classement est automatique (le plus petit ou le plus grand gagne, au choix) et les
**ex-aequo partagent la place** et ses points (deux premiers à 5, le suivant est 3e et prend 3). Le barème par
défaut est 5-4-3-2-1 (places au-delà : 0 point ; disqualifié : 0 ; sans valeur : son score n'est pas touché).

- **Écran** : touche **R** (organisateurs) ou `/craftMasterResult`. Sur papier crème : tête, candidat, valeur
  (taper `42`, `1:05`, `12,5`… Entrée = ligne suivante, vide = effacer), **DQ**, **⏱** (son chrono, le temps
  défile en direct), place, points proposés, score actuel de la manche entre parenthèses. En haut : mesure, ordre,
  barème ; ◀ ▶ pour les autres manches.
- **« Appliquer le barème »** fixe le score de chaque candidat dans la manche (`score.set`) en **une seule
  modification annulable** (`/craftMasterUndo`), exactement comme la voix. Les points sont annoncés comme d'habitude.
- La feuille est enregistrée avec la manche (fichier de données) et elle aussi annulable.

## 3. Outils du juge

| Outil | Utilisation |
|---|---|
| **Chronomètre du juge** | Sur un candidat : départ, puis arrêt (le temps est noté sur la feuille). En l'air : tous les chronos avec des boutons ▶ / ■ dans le chat. Chaque chrono est une barre visible de tous (essai en solo pendant que les autres attendent), « Top départ ! » / « Stop ! » au candidat. |
| **Mètre ruban** | Sur un point puis sur un autre : la distance entre les **points exacts visés** par le réticule (sur la face du bloc, pas son centre), au centième de bloc, plus la distance à plat et la hauteur. Pendant 10 s, un **ruban jaune** est posé entre les deux points avec la distance écrite au-dessus, visible de tous. Accroupi : recommencer. Les deux derniers points servent à créer une zone. |
| **Compteur** | Sur **tout ce qui contient des objets** : blocs (coffre, coffre double entier, tonneau, boîte de shulker, entonnoir, distributeur, four…, les rangements des autres mods par l'API de transfert Fabric, le tiroir des pupitres et la table des prix, prix posés compris) et entités (joueur, wagonnet ou bateau à coffre, âne / lama chargé, villageois, porte-armure et mobs : ce qu'ils portent et tiennent, cadre, objet au sol). Nombre d'objets et les trois plus nombreux ; seulement le type tenu dans l'autre main s'il y en a un (« combien d'œufs ? »). |
| **Balance** (bloc) | Clic droit en tenant un objet : son poids ; main vide : le poids des objets jetés sur le plateau. Table de poids ludique mais cohérente (netherite > or > fer, plume ≈ 0). |
| **Zones d'épreuve** | `/craftMasterZone create <nom>` (avec les deux points du mètre) ou `… <de> <à>`. Un candidat qui sort a un titre « Hors zone ! », un rappel tant qu'il est dehors, et les organisateurs sont prévenus ; le retour est noté. `players <nom> <joueurs…\|all>`, `show` (contour 15 s), `enable`, `remove`, `list`. Enregistrées dans `config/craftmaster/zones.json`. |

Après chaque mesure (mètre, compteur, balance), un organisateur a dans le chat **« Noter pour : [Ensuri] [Cyllu]… »** :
un clic écrit la valeur sur la feuille de la manche en cours.

## 4. Créativité

- **Toile de peintre, ardoise, tableau blanc** : se posent sur un mur (2×2 ou 3×2 blocs, `/craftMasterBoard size
  <l> <h>` jusqu'à 8×6). 16 pixels par bloc. **Teinture : peindre** (maintenir le clic droit pour tracer) ;
  accroupi + teinture : remplir une zone (pot de peinture) ; **éponge** : gommer ; **main vide : écrire** (quelques
  lignes, centrées, aussi grandes que possible). Sur l'ardoise la teinture devient de la craie. Accroupi + frapper :
  décrocher. `/craftMasterBoard lock true` réserve un tableau aux organisateurs ; `clear` efface ; `text` écrit.
- **Socle d'exposition** : poser n'importe quel objet dessus (il flotte et tourne, son nom au-dessus) ; main vide
  pour le reprendre. Idéal pour présenter une création au juge.
- **Garde-robe** : une armoire qui contient toutes les tenues (vêtements Selfexpression, armures) ; un clic pour
  enfiler une tenue entière ou une seule pièce, « Mes affaires » pour revenir ([Garde-robe](GardeRobe.md)).
- **Nommer** : `/craftMasterName <nom>` (tous) renomme l'objet tenu, codes couleur `&6`, `&l`… sans enclume ni
  XP ; sans nom : retire le nom. Les objets nommés gardent leur nom sur le socle, les props, les tableaux.

**Retirés** (retours de LordFinn, 12/11/2026) : la malle à costumes et le carton rouge. Un monde qui en contient
se charge sans erreur : la malle devient un coffre vide, le carton rouge disparaît. La disqualification reste là :
bouton DQ de la feuille de résultats, `/craftMasterResult dq`, la voix (« Ensuri est disqualifié »).

## 5. À la voix

« Ensuri a fait 42 secondes », « Cyllu : 12 blocs », « note de 8 pour Juste », « Arreaubaz est disqualifié »,
« applique le barème », « barème 3 2 1 », « top chrono pour Ensuri », « stop Ensuri », « remets les buzzers ».
Les quatre plus simples marchent aussi hors ligne ([Régie vocale](RegieVocale.md)).

## Commandes

Voir [Commandes](Commandes.md#épreuves-admin) : `/craftMasterResult` (cfre), `/craftMasterChrono` (cfch),
`/craftMasterBuzzer` (cfbz), `/craftMasterZone` (cfzo), `/craftMasterTrial` (cftr), `/craftMasterBoard` (cfbd),
`/craftMasterName` (cfnm).

## Exemple : « Le premier à sonner la cloche »
1. `/craftMasterManche create "La Cloche"` puis `/craftMasterResult kind temps`.
2. Une baguette d'objet d'épreuve sur la ligne de départ (laine verte en main gauche) : *Lance l'essai*,
   message « C'est parti {player} ! ». Une autre sur l'arrivée (cloche) : *Termine l'essai*, *Annonce le 1er*,
   signal redstone vers des feux d'artifice.
3. `/craftMasterZone create parcours` avec le mètre ruban sur deux coins.
4. Chaque candidat passe seul : son temps s'écrit sur la feuille. Touche **R**, vérifier, « Appliquer le barème ».
