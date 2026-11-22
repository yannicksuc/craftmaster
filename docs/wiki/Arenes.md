# Arènes d'épreuve : un parcours par joueur, mots de passe, vase, dessin au sol, village à refaire, inventaire alphabétique

Des briques communes pour monter vite une épreuve, toutes côté serveur et avec des visuels du jeu de base
(entités d'affichage, dialogues du jeu, blocs et panneaux) : **rien à installer chez les joueurs**. Elles servent
aux cinq épreuves de LordFinn décrites plus bas, et chacune peut resservir seule.

« Candidats » = les joueurs de la saison active ; « organisateurs » = permission `craftmaster:admin` ou opérateur.

## 1. Les briques

### Arène : une zone de référence copiée pour chaque joueur
Une **arène** est une boîte construite une fois (la **référence**), copiée côte à côte (vers les X croissants) pour
chaque candidat : chacun joue seul dans **sa** copie.
- `/cm arene creer <nom>` : la boîte entre les deux derniers points du **mètre ruban** (deux coins opposés, sol compris :
  pour un village sur bedrock, descendre jusqu'à la bedrock).
- `/cm arene spawn <nom>` (debout dans la référence) : point de départ, le même dans chaque copie.
- `/cm arene copier <nom> [joueurs|tous]` : une copie par candidat (sans liste : ceux de la saison), construite à
  l'identique : **blocs, contenu des coffres, tonneaux et étagères, cadres, golem, fleurs posées**… Chaque copie est
  entourée d'un mur et d'un plafond de **barrières** (personne ne sort ni n'atteint le voisin ; `/cm arene option <nom>
  murs false` pour s'en passer).
- `/cm arene placer <nom> [joueurs]` : chacun au départ de sa copie, **réapparition fixée là**, avec le **kit**
  (`/cm arene kit <nom>` = les objets de ton inventaire ; `kit <nom> vide` ; `option <nom> inventaireVide true` vide
  d'abord leur inventaire).
- `/cm arene reset <nom>` : toutes les copies refaites comme la référence (objets cachés compris), prêtes à rejouer.
- `/cm arene tp <nom> <joueur> [dessus]` : aller voir une copie (au-dessus, en vol, pour juger un dessin) ;
  `/cm arene tp <nom> reference [dessus]` : la **salle de référence** (son point de départ, dans le monde de l'arène ;
  bouton « Référence » du menu N). C'est là que se posent les caméras de l'intro : debout dans une arène,
  `/cm cam ici <n>` et `/cm cam ajouter` rangent la caméra dans le lieu de **sa manche** (sans avoir à écrire
  `pomme:3`) ; le Tab de `/cm cam ici|viser|aller|nom|retirer…` n'y propose que les caméras de cette manche (« 1 · Pomme
  — caméras de la manche La pomme »), le numéro libre suivant (« nouvelle ») et les rôles.
- `/cm arene retirer <nom> <joueur>` · `supprimer <nom>` · `liste`. Tout est gardé dans `config/craftmaster/arenas.json`.

**Terrain commun** : `/cm arene option <arène> terrainCommun true` fait jouer tout le monde sur le même terrain, sans copie ni remise à neuf. Chacun a son départ, réparti sur le vrai sol (`/cm arene depart <arène> <joueur>` pour le poser à la main, `auto` pour revenir au calcul). Les candidats sont en aventure, ne cassent et ne posent rien, et ne sortent pas de la zone. Les bannières rouge et verte se déclenchent d'un clic droit sans être posées ; le tracé de chacun reste invisible et séparé. `joueursVisibles` (oui par défaut) : les joueurs se voient. `finSpectateur false` : un candidat qui a fini reste sur le terrain, sans proposition de regarder ni F7 jusqu'à la fin de l'épreuve.

**Livre à signer** : `/cm arene option <arène> livreNom true` (terrain commun) donne à chaque candidat un livre « Le nom de ton animal ». Signer fixe la réponse, envoyée aux seuls organisateurs et gardée avec le dessin ; un livre non signé à la fin du chrono est relevé tel quel. Il ne se jette pas, ne se range nulle part et revient à son propriétaire. Au jury, `/cm arene jury <arène> reveler <joueur>` (ou le bouton [Révéler le nom]) affiche le nom à l'écran.

**Aperçu du tracé** : en regardant un candidat, les organisateurs et les vrais spectateurs voient son tracé en direct dans un coin. `/cm apercu on|off|basculer`, `coin hg|hd|bg|bd`, `taille petit|moyen|grand`, une touche à régler dans les commandes, et une ligne dans le menu N. Sur la diffusion (F8), il est coupé par défaut : `/cm apercu antenne on`. Le serveur n'envoie jamais un tracé à un candidat.

**Depuis une sélection WorldEdit** : `/cm arene creer <nom> selection` prend la sélection du joueur comme zone, sans mètre ruban. Avant de copier, `/cm arene plan <arène|selection> [copies]` annonce sans rien changer les blocs par copie et au total, la place de chaque copie, ce qu'elles chevauchent (arènes et zones déclarées) et ce qu'il y a sur place dans les chunks chargés. Une copie se fait en une fois : au-delà de 1 million de blocs au total, `/cm arene copier` et les remises à neuf sont refusées pour ne pas geler le serveur.


La **réapparition** suit aussi les points de TP des manches : un candidat téléporté au lancement d'une manche
réapparaît là s'il meurt (pour toutes les épreuves).

### Les copies suivent les candidats de la saison
Les copies d'une arène sont toujours **une par candidat de la saison active** : au lancement de l'épreuve (au plus
tard), ou tout de suite avec `/cm arene synchroniser [arène]` (bouton « Synchro » du menu), les nouveaux candidats ont
leur copie (à la première place libre), celles des anciens sont **retirées et leur sol remis** (chaque bloc redevient
ce qu'il y a à côté, entre deux copies : le sol du monde, l'air au-dessus). Les candidats déjà là **gardent leur place**.
Une arène **sans copie** (l'inventaire alphabétique, une salle commune) est **partagée** : personne n'y touche.

### Lancer l'épreuve en une action
`/cm epreuve lancer <manche>` (Stream Deck, menu N) met tout en place : fin de la manche d'avant, manche lancée,
copies synchronisées et **remises à neuf**, chaque candidat **au départ de sa copie** (réapparition là) dans le **mode
de jeu de l'arène** avec son **kit**, puis les **enveloppes**. `/cm epreuve suite` fait l'étape suivante,
`/cm epreuve terminer` ramène chacun où il était. Tout est dans le [déroulé du direct](Deroule-du-direct.md).
- `option <nom> mode aventure|survie` : mode de jeu des candidats pendant l'épreuve (aventure par défaut) ;
- `option <nom> vocalLancement true` : le canal vocal commun s'ouvre au lancement ;
- `/cm arene etapes <nom> <étapes…>` : les étapes de la « suite », dans l'ordre (`revele`, `vocal`, `vocalfin`,
  `chrono:<secondes>`, `panneaux` ; sans réglage : `revele`). Sans étape : `/cm arene etapes <nom>` les montre.

### Lien avec la manche
`/cm arene manche <nom> <manche>` lie l'arène à sa manche, puis :
- `option <nom> chronoLettre true` : le **chrono de chaque candidat part quand il ouvre sa lettre** ;
- `option <nom> chronoSuite <secondes>` : quand l'organisateur révèle la **partie cachée** de la tâche
  (`/cm tache suite`, voix « révèle la suite »), le chrono de la manche part avec cette durée ;
- `option <nom> panneauxSuite true` : à cette révélation, les **panneaux** de chaque copie sont verrouillés (plus moyen
  de les changer) et leur texte arrive chez les organisateurs (« Ensuri : girafe ») ; `/cm arene panneaux <nom>
  [verrouiller]` les lit (et les verrouille) à la main.

### Fin d'un essai, en privé
Quand un candidat finit (vase plein, pomme mangée, sol touché), son chrono s'arrête et son temps s'écrit sur la
feuille de la manche. **Seuls lui, les organisateurs et les spectateurs le savent** : les autres candidats ne voient
ni titre, ni barre, ni son. Lui voit « Terminé ! » et son temps. Sans manche en cours, le chrono s'arrête quand même
(temps dans le journal). Dans le chat des organisateurs, **chaque candidat a sa couleur**.

### Objet à mot de passe
Un objet posé à plat sur le sol (n'importe lequel : une fleur…), avec un numéro au-dessus si voulu. Un clic ouvre la
fenêtre de saisie du jeu (« Fleur n°3 — Mot de passe ») ; le bon mot le donne (il disparaît du sol). Un mauvais
mot : « ✗ « xyz » : mauvais mot de passe » et la fenêtre se rouvre aussitôt, **sans attente**. Le candidat et les
organisateurs voient **avec quel mot** il l'a eu (« ✿ Fleur n°3 prise avec « chat » »).
Poser : objet en main, viser le sol, `/cm epreuve motdepasse initiale C [nom]` (un mot qui commence par C, 3 lettres au
moins, chacun **une seule fois par candidat**, accents ignorés), `motdepasse mot sesame [nom]` (exactement ce mot)
ou `motdepasse libre`. Les mots refusés et leur raison sont dans `/cm epreuve journal`.

### Vase qui montre ses fleurs
Viser le pot : `/cm epreuve vase <nombre>`. Un objet pris avec un mot de passe, en main, clic sur le vase : il apparaît
dans le bouquet (vraies fleurs du jeu, en spirale, de plus en plus larges), « ✿ 12 / 26 ». Plein : fin de l'essai,
et un golem de fer à côté tend une fleur. Main vide : où il en est. Seul le candidat de la copie peut le remplir,
et seulement avec les fleurs de son parcours.

### Panneau d'indice
Viser un panneau : `/cm epreuve panneau Indice :|1 = A, 2 = B…|Un mot par fleur` (« | » = ligne suivante). Il est écrit
et ciré (les candidats ne peuvent pas le changer).

### Champ à dessiner en marchant
`/cm arene champ <nom> [bloc d'encre]` : le sol entre les deux points du mètre ruban (le bloc du premier point est le
« papier », l'encre est le béton noir par défaut). Le candidat **plante sa bannière rouge** dans le champ : le dessin
commence ; **sa bannière verte** le termine. La fin du chrono arrête tous les dessins. Il faut être en **survie**
(pour planter les bannières et toucher le sol).
- **À l'aveugle** (`/cm arene option <nom> aveugle true`, réglé d'office par `/cm arene animal`) : rien ne se voit pendant
  qu'il marche, son chemin est seulement enregistré (un saut ou une sortie du champ lève le crayon).
- Sinon : chaque bloc où il marche devient noir.
- Dans les deux cas, à la fin, son chemin devient une **image** (vue de dessus, nord en haut, trait d'encre lissé sur
  papier), gardée dans `config/craftmaster/dessins/<arène>/<joueur>.png`. `/cm arene reset` les efface.

### Écran du jury
Les images sont faites par le serveur et arrivent dans les jeux par leur connexion, comme les photos de CrazyPhone
(pas de site web, adresse `cmimg://…`), sur les **écrans du studio** : n'importe quelle taille, jusqu'à l'écran
géant.
1. Un écran du studio dans la salle principale, agrandi (`/cm media image taille 12 7` en le regardant), nommé « jury »
   (`/cm media tele nommer jury`). Plusieurs écrans du même nom montrent la même chose.
2. `/cm arene jury <arène> tous` : tous les dessins côte à côte, le nom de chacun dessous. `/cm arene jury <arène>
   <joueur>` : son dessin en grand, son nom au-dessus. `suivant` / `precedent` : le dessin d'après / d'avant.
3. Pour changer en direct en parlant : la ligne de boutons dans le chat **[Tous] [Ensuri] [Cyllu]… [◀] [▶]**, un clic
   par changement. Elle arrive à chaque dessin terminé et après chaque `/cm arene jury` (`/cm arene jury <arène>` seul la
   redonne). Un autre écran : `/cm arene jury <arène> tous <nom de l'écran>`.

### Objet marqué (« la pomme »)
Objet en main : `/cm epreuve marquer <but>`. Le manger termine l'essai de celui qui le mange (seulement cet objet-là : une
autre pomme ne fait rien).

### Fin au sol et inventaire alphabétique
`option <nom> solFin true` : une fois sorti de la boîte de l'arène (la plateforme de départ), un candidat qui se tient
**sur un bloc** termine sa tâche. Ne comptent pas : être dans l'eau ou la lave, dans une toile d'araignée, sur une
monture, en vol plané. Le candidat doit être passé par la plateforme pendant la manche (quelqu'un qui attend au sol
avant le départ n'est pas « terminé »). Ensuite son **inventaire est verrouillé** : aucun clic ne le change, il ne
peut rien lâcher ni changer de main, ni ouvrir un coffre.
`option <nom> alphabet true` : en plus, le score est la **plus longue suite rangée par ordre alphabétique** :
- dans le **sens de lecture** de l'écran d'inventaire : les trois rangées du sac de gauche à droite et de haut en
  bas, puis la barre d'objets (la rangée du bas) ; casques, armures et seconde main ne comptent pas ;
- les cases vides sont sautées, deux objets du même nom se suivent ;
- avec les **noms que voit ce joueur, dans la langue de son jeu**, objets renommés compris, selon l'ordre
  alphabétique de cette langue (majuscules et accents ignorés) ;
- un objet **dans un bundle** à la fin : les organisateurs ont l'alerte avec **[disqualifier]**.
La suite est notée sur la feuille (nombre, le plus grand gagne) et détaillée chez les organisateurs (« Ensuri (fr_fr)
: Carotte ≤ Diamant ≤ Flèche… »).

Les langues : le serveur n'a que l'anglais. Il télécharge une fois le fichier de la langue d'un candidat depuis le
serveur public des ressources de Mojang (dès qu'il est sur la plateforme) et le garde dans
`config/craftmaster/lang/<langue>.json` ; les noms des objets des mods viennent des fichiers de langue des mods.
Si le serveur n'a pas accès à Internet, les noms restent en anglais (le chat le dit) : on peut aussi déposer le
fichier à la main dans ce dossier (il est dans `assets/objects` d'un jeu installé).

### Canal vocal commun
`/cm epreuve vocal commun` : tous les candidats et les organisateurs dans un même groupe de Simple Voice Chat (pour
annoncer un rebondissement à tous). `/cm epreuve vocal fin` le ferme, et `/cm epreuve chrono <secondes>` aussi : il lance le
chrono de la manche en cours **et** rend à chacun le vocal de proximité.

## 2. Les cinq épreuves

### Les fleurs de Jeremy
> Cueillez les 26 fleurs du chemin et mettez-les toutes dans le vase de Jeremy. Le plus rapide l'emporte. Votre
> tâche commence maintenant.

Un chemin par joueur. 26 fleurs posées au sol, numérotées 1 à 26 ; le mot de passe de la fleur n est un mot qui
commence par la n-ième lettre (1 = A, 2 = B…). La fenêtre n'explique pas la règle : l'indice est sur un panneau à
part, ailleurs dans la salle.
1. Debout au début du chemin, regard dans le sens du chemin : `/cm arene fleurs <nom>` pose une bande d'herbe, un chemin,
   les 26 fleurs, le vase et le golem Jeremy, et crée l'arène (chrono à l'ouverture de la lettre).
2. `/cm manche creer "Fleurs de Jeremy"`, la tâche (`/cm tache editer`), `/cm epreuve feuille mesure temps`,
   `/cm arene manche <nom> "Fleurs de Jeremy"`.
3. Le panneau d'indice : `/cm epreuve panneau …`.
4. `/cm arene copier <nom>`, lancer la manche, `/cm arene placer <nom>`, les enveloppes.
5. Chacun : la lettre ouverte lance son chrono, la 26e fleur dans le vase l'arrête (temps sur la feuille).
   Rejouer : `/cm arene reset <nom>`.

### Animal
> Choisissez un animal original et écrivez son nom sur votre panneau. Votre tâche commence maintenant.
>
> [partie cachée] Dessinez cet animal en marchant dans votre champ. Plantez la bannière rouge pour commencer le
> dessin, la verte pour le finir. L'animal le plus beau et le plus ressemblant l'emporte. Vous avez 10 minutes.
> Votre tâche commence maintenant.

1. Debout devant l'emplacement : `/cm arene animal <nom>` pose un champ blanc de 21 × 21, un panneau à l'entrée, et crée
   l'arène : champ à dessiner **à l'aveugle**, kit bannière rouge + bannière verte, panneaux verrouillés et chrono de
   10 min à la révélation de la suite.
2. La manche, sa tâche avec la seconde partie entre `[secret]…[/secret]`, `/cm epreuve feuille mesure note`, `/cm arene manche`.
3. `/cm arene copier`, lancer, `/cm arene placer` (en **survie**).
4. Chacun écrit son animal sur son panneau. Quand tout le monde a écrit : « révèle la suite » ou `/cm tache suite` :
   panneaux verrouillés (les animaux arrivent dans ton chat), chrono de 10 min.
5. Le jury : `/cm arene jury <nom> tous` sur l'écran « jury » de la salle principale, puis chaque dessin en grand avec
   les boutons du chat ; la note sur la feuille.

### La pomme
> Mangez la pomme. Le plus rapide l'emporte. Votre tâche commence maintenant.

Une salle façon escape game, construite par LordFinn. La pomme de l'épreuve : une pomme en main, `/cm epreuve marquer
pomme`, à cacher dans la salle. Une salle par joueur : `/cm arene creer`, `/cm arene spawn`, `option chronoLettre true`,
`/cm arene manche`, `/cm arene copier` (la pomme marquée est copiée dans chaque salle avec le reste). Une salle commune : la
même chose sans copier (le premier qui la mange termine). `/cm epreuve feuille mesure temps`.

### Le village
> Mettez le plus de bordel possible dans votre village. Vous avez 5 minutes. Votre tâche commence maintenant.
>
> [partie cachée] Reconstruisez votre village au bloc près. Le village le plus fidèle l'emporte. Vous avez
> 10 minutes. Votre tâche commence maintenant.

1. Dans une carte vide : le village de référence, peu profond, sur bedrock. Les objets cachés (pelle, pioche et hache
   enchantées, TNT, seaux de lave et d'eau, briquet…) dans des tonneaux, coffres, cadres et étagères ; un des outils
   bien à portée et visible sans être évident ; une plaque de pression qui fait sauter une TNT près du départ.
2. `/cm arene creer village` (mètre ruban jusqu'à la bedrock), `/cm arene spawn village`, `/cm arene copier village` : un village
   par candidat, murs de barrières autour. La manche : chrono de 300 s au départ de la lettre, `/cm arene manche village
   <manche>`. Candidats en **survie**.
3. Fin des 5 minutes : `/cm epreuve vocal commun`, tu annonces la suite, `/cm tache suite <manche>` (la lettre se complète).
4. Quand tu veux : `/cm epreuve chrono 600` (chrono de 10 min, vocal commun fermé).

   En direct, tout ça tient en touches : « Lancer village » (survie : `/cm arene option village mode survie`), puis
   trois fois « Suite » avec `/cm arene etapes village vocal revele chrono:600`.
5. À la fin : `/cm arene comparer village` : pour chacun, la part des blocs identiques à la référence (le contenu des
   coffres, tonneaux, étagères et cadres ne compte pas ; la bedrock et les barrières non plus) ;
   **[noter sur la feuille]** l'écrit (le plus grand gagne). Rejouer : `/cm arene reset village` (villages et objets
   cachés remis).

### L'inventaire alphabétique
> Mettez votre jeu dans la langue de votre choix. Rangez votre inventaire par ordre alphabétique, dans le sens de la
> lecture. À la fin, aucun objet ne doit être dans un bundle. Votre tâche s'arrête quand vous touchez le sol :
> ensuite, vous ne pouvez plus toucher à votre inventaire. La plus longue suite bien rangée l'emporte. Votre tâche
> commence maintenant.

1. Une carte normale, bordure du monde de 500 × 500, une rivière ; une petite plateforme à y 320 ; au sol, cochons,
   dromadaires et chevaux sellés, un happy ghast harnaché.
2. `/cm arene creer inventaire` autour de la plateforme (elle seule), `/cm arene spawn inventaire`, `/cm arene option
   inventaire alphabet true`, `option inventaire murs false`, `option inventaire inventaireVide true`.
3. Le kit : les objets à ranger, un seau d'eau, une fusée de durée 3 (`firework_rocket[fireworks={flight_duration:3}]`),
   un bateau, dans ton inventaire, puis `/cm arene kit inventaire`.
4. La manche (`/cm epreuve feuille mesure nombre`), `/cm arene manche inventaire <manche>`, lancer, `/cm arene placer inventaire` (tous
   ensemble sur la plateforme, réapparition là).
5. Chacun saute quand il veut : au premier bloc touché, sa tâche s'arrête (en privé) et sa suite est notée.

### Caméra d'inventaire (pour les viewers)
Quand le candidat **à l'antenne** (suivi par la caméra ou vu par ses yeux) ouvre son inventaire, le jeu de diffusion
(F8) et le flux de l'émission (F7) le montrent dans un panneau aux couleurs de l'émission, qui glisse depuis le bord
droit de l'image et repart quand il le ferme :
- les objets, en direct, comme il les déplace ;
- la case sous sa souris, éclairée ;
- la flèche de sa souris, lissée, et **l'objet qu'il tient au curseur** qui la suit ;
- sous la grille, le nom de l'objet tenu (sinon celui de la case survolée).
Marche avec tout écran qui montre l'inventaire (inventaire, coffre, établi…). Les autres candidats ne reçoivent rien :
seuls les jeux de diffusion et les organisateurs. Il faut la version du mod qui a cette caméra **chez les candidats**
(leur jeu dit quand l'écran s'ouvre et où est la souris) **et sur le jeu de diffusion** (qui la dessine).

## Commandes
Voir [Commandes](Commandes.md#arènes-dépreuve-admin).
