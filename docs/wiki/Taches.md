# Tâches, enveloppes et notes cachées

Chaque manche peut avoir une **tâche** : le texte que les candidats lisent sur la carte de leur **enveloppe
scellée**. On ouvre l'enveloppe comme à la télé : elle arrive, la cire tremble et craque, le rabat s'ouvre sur sa
doublure de velours, la carte sort et se déplie. Dans la salle, des **notes cachées** attendent les plus curieux.

## La règle du secret
Une tâche reste **secrète pour tous les non-admins** (candidats, spectateurs, client de diffusion) tant qu'elle
n'est pas révélée :
- un candidat **ouvre une enveloppe** de la manche (révélée quand la carte sort, ~2,5 s) ;
- la **manche est lancée** (`/craftMasterManche start`), sauf si la tâche est réglée sur « Enveloppe seulement » ;
- l'organisateur la **révèle** (bouton « Révéler à tous », `/tache reveal`, voix « révèle la tâche »).

Le texte d'une tâche secrète ne quitte jamais le serveur pour un non-admin. Une révélation n'est **pas annulée**
par `/craftMasterUndo` (c'est un événement du direct, comme un TP) : pour la remettre au secret,
`/tache hide <manche>` ; avant une nouvelle répétition, `/tache resetReveals`.

## Écrire une tâche
- **Écran** : `/tache edit [manche]` (ou le bouton « Modifier » sur la carte). À gauche les manches de la partie
  active (○ sans tâche, ● écrite et secrète, ✔ révélée, ▶ en cours) ; à droite : **titre**, **texte** (une ligne vide
  = nouveau paragraphe), **règles** (une par ligne, affichées en puces), **durée** affichée (secondes ou `m:ss`, vide
  = le chrono de la manche), **cire** (12 couleurs), révélée **au lancement** ou **par enveloppe seulement**.
  « Enregistrer » (changer de manche enregistre aussi), « Aperçu » joue l'ouverture avec le texte tapé (sur ton
  écran seulement), « Révéler », « À l'écran » (la carte 12 s sur tous les écrans), « Enveloppes » (une enveloppe à
  chaque candidat connecté, à son nom).
- **Commandes** : `/tache set <manche> title|text|append|rules|seconds|wax|revealOnStart <valeur>` (voir
  [Commandes](Commandes.md)) ; dans un texte de commande, `\n` fait un retour à la ligne.
- **Voix** : « la tâche du pont : construisez le pont le plus long… » (le texte dicté est remis en forme),
  « ajoute à la tâche du pont : … », « mets la cire en or », « révèle la tâche », « montre la tâche »,
  « donne les enveloppes ».

Tout passe par l'exécuteur d'actions : mêmes résultats par l'écran, la commande ou la voix, et les textes
s'annulent avec `/craftMasterUndo`.

## Enveloppes
- **Sceau à cire** (organisateurs, `/tache tools` ou onglet créatif) : clic droit → choisir le contenu (la tâche
  d'une manche, ou un texte libre), la cire, le nom écrit dessus, combien (ou **une par candidat**, chacune à son
  nom), ouverture annoncée ou discrète → « Sceller ». Le sceau garde la dernière couleur.
- `/tache envelope <manche> [joueurs|tous]` : enveloppes données directement (aux candidats connectés par défaut).
- **Ouvrir** : clic droit avec l'enveloppe en main. L'enveloppe devient « ouverte » (rabat levé, cire brisée) et se
  relit d'un clic droit. Une enveloppe liée à une manche lit toujours la dernière version de la tâche.
  Annonce à tous (barre d'action + son) : « ✉ Ensuri ouvre sa tâche ».
- **Sur la table** : accroupi + clic droit sur le dessus d'un bloc pose l'enveloppe (bloc plat, cire de sa couleur).
  Clic droit dessus = la ramasser (elle va dans la main) ; accroupi, main vide = l'ouvrir sur place.
- **Lecture** : molette ou flèches pour faire défiler une longue carte ; clic / Espace passe l'animation ;
  Échap ou J ferme. `/tache close [joueurs]` ferme les cartes ouvertes (tout le monde lève les yeux).

## Consulter la tâche (tout le monde)
Touche **J** (catégorie CraftMaster) ou `/tache` (`/craftMasterTask`) : la tâche du moment — celle de la manche en
cours, sinon la dernière révélée. Secrète : l'enveloppe scellée « Tâche encore secrète ». Pour un organisateur,
même secrète (entre deux manches : la prochaine manche qui a une tâche), avec « Révéler à tous », « À l'écran »,
« Modifier ».

## Notes cachées
Créer : `/craftMasterNote create <titre>` (alias `/cfno`) donne l'**outil de cachette** de la note ;
`/cfno text <note> <texte>` ou l'éditeur (clic droit dans le vide avec l'outil, ou `/cfno edit <note>`).

Cacher (outil en main, organisateur) : **clic droit sur un bloc** — la cachette dépend du bloc :

| Bloc visé | Cachette | Pour la trouver |
|---|---|---|
| tapis, dalle, plaque de pression, trappe | **sous le tapis** | s'accroupir + clic droit |
| bibliothèque, bibliothèque sculptée, pupitre | **dans un livre** | clic droit |
| coffre, tonneau, tout conteneur | **double fond** (ouvert normalement il paraît vide) | s'accroupir + clic droit |
| autre bloc (pot, vase, cheminée…) | **cachée dedans** | clic droit |
| tableau, cadre (entité) | **derrière le tableau** | clic droit |
| accroupi + clic droit sur une face | **feuille épinglée** sur cette face (dessous de table, dos d'armoire) | clic droit sur la feuille |

Et aussi : **bouteille à la mer** (bouton « En bouteille » / `/cfno bottle <note>`, à cacher dans un coffre, à
jeter dans l'eau, à donner), qu'on débouche d'un clic droit.

Réglages par note (éditeur ou `/cfno set <note> …`) :
- **Aspect** : papier, parchemin (bords brûlés), carnet (lignes, marge rouge), post-it.
- **Lecteurs** : tout le monde, candidats de la saison, ou une liste de joueurs. Pour les autres la cachette reste
  un bloc ordinaire (rien ne se passe).
- **Prise par le 1er** : le premier qui la trouve l'emporte (objet « Note » dans son inventaire, relisible) et la
  cachette est vide pour les suivants ; sinon elle reste et chacun peut la lire sur place.
- **Encre invisible** : la feuille paraît vierge, sauf si on tient la **lampe UV** (dans l'une des mains) en la
  lisant : la lumière violette fait apparaître l'écriture.
- **Morceaux** (1 à 6) : la note est déchirée ; chaque clic droit de l'outil cache le morceau suivant. Le lecteur
  voit les morceaux qu'il a trouvés (ou qu'on lui a donnés) et des trous « morceau 2 manquant » ; complète, la
  note se lit d'un bloc.

L'organisateur voit tout : chaque première trouvaille s'affiche dans son chat (« 📜 Ensuri a trouvé « Indice »
(sous le tapis) »), l'éditeur liste les cachettes et qui a trouvé quoi et à quelle heure (`/cfno info <note>`).
Outil en main, ses cachettes scintillent (orange : cette note, vert : les autres, fumée : prise). Clic gauche avec
l'outil sur une cachette : la retirer. « Réinitialiser » oublie les trouvailles et remet les notes prises en place.

## Twitch : `!tache` (facultatif, client de diffusion)
Le jeu filmé par OBS peut lire le chat de la chaîne **en anonyme** (aucun compte, aucune clé, il n'écrit jamais).
Dans `config/craftmaster-client.json` de ce jeu-là :
```json
{ "twitch": { "enabled": true, "channel": "lordfinn", "commands": ["!tache", "!task"],
              "cooldownSeconds": 30, "showSeconds": 12 } }
```
puis `/craftMasterTwitch reload` (état : `/craftMasterTwitch` ; essai sans Twitch : `/craftMasterTwitch test`).
Un spectateur qui tape `!tache` fait glisser la carte de la tâche à l'écran 12 s, au plus une fois toutes les 30 s.
La carte ne montre jamais une tâche secrète, même sur le jeu d'un organisateur (elle dit « Tâche encore secrète »).

## Fichiers
- Textes des tâches : dans `config/craftmaster/config.yml`, clé `task` de chaque manche ([Données](Donnees.md)).
- Révélations et notes (cachettes, trouvailles) : `config/craftmaster/tasks.json`.
- Twitch : `config/craftmaster-client.json` (côté client).
