# Déroulé du direct : une épreuve = une touche

En direct, une épreuve se pilote en **trois gestes**, au choix au Stream Deck (dossier CraftMaster ▸ Épreuves), dans le
menu **N** (onglet Épreuves, ligne « Direct ») ou en commande :

| Geste | Commande | Ce qui se passe |
|---|---|---|
| **Lancer** | `/cm epreuve lancer <manche>` | Voir ci-dessous : tout est en place en une fois |
| **Suite** | `/cm epreuve suite` | L'étape suivante de l'épreuve (révélation, vocal commun, chrono…) |
| **Terminer** | `/cm epreuve terminer` | Fin de la manche ; personne ne bouge, le choix arrive dans le chat |
| **Revoir le replay** | `/cm replay revoir [zone\|<candidat>]` | La manche qui vient de finir, pour celui qui clique |
| **Revenir au plateau** | `/cm epreuve plateau` | Chacun revient à sa place, mode de jeu remis, copies remises à neuf |

## Lancer
`/cm epreuve lancer la-pomme` (ou la touche « Lancer pomme ») fait, dans l'ordre :
1. **fin de la manche d'avant** si une autre est en cours ;
2. **lancement de la manche** comme `/cm manche lancer` (titre, son, chrono selon son départ : lancement, lettre ou
   bouton, décor, intro animée…) ;
3. pour chaque **arène liée à la manche** (`/cm arene manche <arène> <manche>`) :
   - les **copies suivent les candidats de la saison active** : une copie par candidat, créée s'il en manque une,
     retirée (sol remis) pour qui n'est plus candidat ; chacun garde sa place ;
   - toutes les copies **remises à neuf** depuis la référence (objets cachés, tablettes, coffres…) ;
   - chaque candidat connecté **téléporté au départ de sa copie**, réapparition fixée là, **mode de jeu de l'arène**
     (aventure par défaut, survie pour Animal et le Village : `/cm arene option <arène> mode survie`) et **kit** ;
   - **vocal commun** ouvert si l'arène le demande (`option <arène> vocalLancement true`) ;
4. les **enveloppes** de la manche distribuées (voir [Tâches](Taches.md#enveloppes)) : le **chrono à la lettre** part
   quand chacun ouvre la sienne.

### Rien ne s'affiche avant d'être prêt
- **Serveur** : avant tout, les lieux de l'épreuve sont **chargés et gardés chargés** (copies et référence élargies de
  16 blocs, départs, points de TP de la manche, positions des plans de l'intro, plan large), blocs **et entités**
  (mannequins, villageois, cadres). La manche ne part qu'ensuite ; au bout de 15 s les organisateurs lisent « Zones pas
  chargées à temps … » avec **[Lancer quand même]** (`/cm epreuve lancer <manche> force`). Réglable par arène :
  `/cm arene chargement <arène> [marge <blocs> | ajouter x0 z0 x1 z1 | vider]`. Les chunks sont relâchés quand les
  joueurs sont sur place (ou à « Terminer »).
- **Diffusion** : derrière un noir (sur les jeux de diffusion seulement), chaque plan de l'intro est montré le temps
  que la diffusion l'ait **reçu et dessiné** ; l'intro démarre sous ce noir, qui s'ouvre sur son premier plan.
- **Lancement** : noir, téléportation dans les copies **dans le noir**, titre tapé ; l'image s'ouvre quand **chaque
  jeu avec le mod** (candidats, diffusion) a reçu les chunks autour de ce qu'il montre et construit leur rendu (10 s
  au plus de plus ; les organisateurs lisent qui n'était pas prêt). Les jeux sans le mod restent aveuglés jusqu'à
  l'ouverture.
- Pendant un lancement et une intro, les **aperçus des caméras** (site, Stream Deck) attendent : la diffusion ne
  dessine que ce qui est à l'antenne.
- **Show gelé** (`/cm reprise`) : le lancement est refusé (la reprise ramènerait aussitôt les candidats à leur place).

Vérifié en dev image par image (candidat venant de loin, client de diffusion) : aucun chunk manquant, aucune image
isolée, un seul fondu à chaque ouverture. `rec <secondes> [ticks]` dans `dev-input.txt` enregistre l'image finale
(monde, effets, interface) tous les N ticks ([Tests](Tests.md)).

Lancer une manche autrement (`/cm manche lancer`, la voix, le site) fait la même mise en place : la touche
« Lancer » ajoute seulement la fin de la manche d'avant.

Les organisateurs lisent dans le chat ce qui a été fait (« ▶ « pomme » : 4 copie(s) remise(s) à neuf », « ▶ LordFinn,
Nirozyka… en place (aventure) », « ▶ 4 enveloppe(s) distribuée(s) »).

## Suite
Chaque appui fait **l'étape suivante** de l'arène (`/cm arene etapes <arène> <étapes…>`, menu N : ligne « Suite ») :

| Étape | Effet |
|---|---|
| `revele` | la partie cachée suivante de la tâche (comme `/cm tache suite`) ; avec elle, le chrono `chronoSuite` et les panneaux verrouillés si l'arène le demande |
| `vocal` · `vocalfin` | canal vocal commun (candidats et organisateurs) · retour au vocal de proximité |
| `chrono:600` | le chrono de la manche, 600 s (ferme le vocal commun) |
| `panneaux` | les panneaux de chaque copie verrouillés, leur texte chez les organisateurs |

Sans réglage : une seule étape, `revele`. Réglages des épreuves de la saison 1 :
- **Animal** : `revele` (le chrono de 600 s et les panneaux suivent la révélation : `chronoSuite`, `panneauxSuite`) ;
- **Le village** : `vocal revele chrono:600` — fin des 5 minutes : Suite (vocal commun, tu annonces) ; Suite (la
  lettre se complète) ; Suite (10 minutes, vocal fermé).

## Terminer, puis choisir
`/cm epreuve terminer` (touche « Terminer » à confirmer : deux appuis) : **fin de la manche, rien d'autre** — personne
n'est déplacé, rien ne se lance tout seul. Dès que l'enregistrement de la manche est sauvegardé (ou quelques secondes
après, s'il n'y en a pas), les organisateurs reçoivent dans le chat :
- **▶ Revoir le replay : [zone] [Ensuri] [Kamel]…** : la zone de l'épreuve, ou la vue d'un candidat, rejouée pour
  celui qui clique seulement (`/cm replay revoir zone|<candidat>`). Si les fichiers s'écrivent encore, le replay le
  dit et s'ouvre tout seul dès qu'ils sont prêts.
- **⌂ [Revenir au plateau]** (`/cm epreuve plateau`) : chaque candidat **revient où il était avant le lancement**
  (dimension, place, mode de jeu ; inventaire vidé si l'arène vide l'inventaire), vocal commun fermé, copies **remises
  à neuf** pour la prochaine fois.

Le même choix arrive quand la manche finit autrement (`/cm manche fin`, la voix, le chrono). Menu N, ligne « Direct » :
« Replay » (la zone) et « Plateau » (Maj+clic) ; Stream Deck, page Épreuves : « Revoir replay » et « Plateau ». La feuille de résultats et les scores ne bougent
pas : publier reste la touche « Publier ».

## Spectateurs

Pendant une épreuve, **les candidats qui ont fini** (pomme mangée, chrono arrêté…) **et les joueurs connectés qui ne
jouent pas** peuvent regarder, jusqu'à « Revenir au plateau ». Ça leur est proposé tout seul dans le chat (et dans le
menu N, ligne « Regarder ») :

- **📺 L'émission** : la retransmission comme elle passe à l'antenne (réalisation automatique, intros, F7 imposé),
  commentée en vocal avec LordFinn (mode « commentaire », plus bas) ;
- **👁 un candidat encore en jeu** : par ses yeux (la caméra spectateur de Minecraft) ; on change d'un clic ;
- **✖ Quitter** : retour à sa place et à son mode de jeu.

Garde-fous : un spectateur est en mode spectateur (invisible, aucune interaction, jamais à l'antenne), tenu sur sa
caméra ; son vocal est dans un groupe isolé (aucun candidat qui joue ne l'entend) ; ce qu'il écrit dans le chat ne
va qu'à ceux qui ne jouent pas. Il revient à sa place et à son mode s'il quitte, si les spectateurs sont coupés, s'il se
déconnecte, et à « Revenir au plateau ». Les candidats encore en jeu ne peuvent pas regarder.

Autorisé par défaut. `/cm spectateurs autoriser off` (partout) ou `… off <manche>` (une épreuve), même à chaud : les
spectateurs reviennent à leur place. Menu N, onglet Épreuves, ligne « Spectateurs » (Autorisés, Cette épreuve, Qui
regarde). `/cm spectateurs` seul : le menu des choix et qui regarde quoi. Stream Deck : `/spectateurs` (bascule) ou
`/spectateurs?enabled=true|false`.

## Le vocal de LordFinn

Avec Simple Voice Chat, un seul système de groupes (le canal commun des étapes `vocal` / `vocalfin` compris). Le vocal
de LordFinn a des **modes exclusifs** ; chacun revient tout seul dans son groupe d'avant au changement :

| Mode | Qui l'entend | Commande | Stream Deck |
|---|---|---|---|
| Off | personne : chacun son vocal habituel (proximité, canal commun) | `/cm vocal mode off` (`/cm vocal fin`) | `/vocal/off` |
| Commentaire | les spectateurs (ouvert : ils l'entendent et se parlent ; fermé : coupé) — jamais les candidats qui jouent | `/cm vocal mode commentaire [ouvert\|ferme\|basculer]` | `/vocal/commentaire` (bascule) |
| Tout le monde | l'annonce : tous, ou les candidats, ou les spectateurs | `/cm vocal mode tous [candidats\|spectateurs]` (`/cm vocal annonce …`) | `/vocal/tous[?qui=candidats]` |
| Joueur / équipe | un candidat, ou une équipe du tableau des scores, seul avec lui ; par défaut celui qu'il regarde | `/cm vocal mode joueur [pseudo\|equipe:nom]` (`/cm vocal parler …`) | `/vocal/joueur?nom=…` |

**Touche F6** (réglable) : le mode suivant (off → commentaire → tous → joueur). Menu N, onglet Épreuves, ligne
« Ton vocal ». En haut de l'écran de LordFinn : le mode et qui l'entend ; chez ceux à qui il parle : « 🎙 LordFinn te
parle » (rien sur l'image de la diffusion). `/cm vocal` seul : qui est dans quel groupe. La régie IA connaît les
spectateurs et les modes (« ouvre le commentaire », « parle à Ensuri », « interdis les spectateurs »).

## Candidats qui arrivent en retard
Un candidat qui se connecte pendant la manche reçoit **son enveloppe** tout seul (pas de doublon : ni s'il en a une, ni
s'il l'a déjà ouverte). Pour le mettre dans sa copie : `/cm arene placer <arène> <joueur>`.

## Avant le direct
- La saison active a les bons candidats (`/cm joueur …`) : les copies suivront au lancement (ou tout de suite avec
  `/cm arene synchroniser`, bouton « Synchro » du menu).
- Chaque arène est liée à sa manche, son mode de jeu et ses étapes sont réglés (menu N, onglet Épreuves).
- Les manches ont leur tâche écrite (sinon pas d'enveloppe) ; `/cm manche enveloppes <manche> manuel` pour les donner
  à la main (`/cm tache enveloppe`).
