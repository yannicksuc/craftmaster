# Déroulé du direct : une épreuve = une touche

En direct, une épreuve se pilote en **trois gestes**, au choix au Stream Deck (dossier CraftMaster ▸ Épreuves), dans le
menu **N** (onglet Épreuves, ligne « Direct ») ou en commande :

| Geste | Commande | Ce qui se passe |
|---|---|---|
| **Lancer** | `/cm epreuve lancer <manche>` | Voir ci-dessous : tout est en place en une fois |
| **Suite** | `/cm epreuve suite` | L'étape suivante de l'épreuve (révélation, vocal commun, chrono…) |
| **Terminer** | `/cm epreuve terminer` | Fin de la manche, chacun revient à sa place, copies remises à neuf |

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

## Terminer
`/cm epreuve terminer` (touche « Terminer » à confirmer : deux appuis) : fin de la manche, chaque candidat **revient
où il était avant le lancement** (dimension, place, mode de jeu ; inventaire vidé si l'arène vide l'inventaire),
vocal commun fermé, copies **remises à neuf** pour la prochaine fois. La feuille de résultats et les scores ne bougent
pas : publier reste la touche « Publier ».

## Candidats qui arrivent en retard
Un candidat qui se connecte pendant la manche reçoit **son enveloppe** tout seul (pas de doublon : ni s'il en a une, ni
s'il l'a déjà ouverte). Pour le mettre dans sa copie : `/cm arene placer <arène> <joueur>`.

## Avant le direct
- La saison active a les bons candidats (`/cm joueur …`) : les copies suivront au lancement (ou tout de suite avec
  `/cm arene synchroniser`, bouton « Synchro » du menu).
- Chaque arène est liée à sa manche, son mode de jeu et ses étapes sont réglés (menu N, onglet Épreuves).
- Les manches ont leur tâche écrite (sinon pas d'enveloppe) ; `/cm manche enveloppes <manche> manuel` pour les donner
  à la main (`/cm tache enveloppe`).
