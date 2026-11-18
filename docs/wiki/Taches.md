# Tâches, enveloppes et notes cachées

Chaque manche peut avoir une **tâche** : le texte que les candidats lisent sur leur **lettre scellée** (l'objet
« enveloppe ») : une feuille crème pliée en trois, fermée par un gros sceau de cire « CM » posé sur le bord du
rabat. Dans la salle, des **notes cachées** attendent les plus curieux.

## La règle du secret
Une tâche reste **secrète pour tous les non-admins** (candidats, spectateurs, client de diffusion) tant qu'elle
n'est pas révélée :
- un candidat **ouvre une enveloppe** de la manche (révélée quand il arrache le sceau ; s'il laisse la lettre
  fermée, au bout de 45 s) ;
- la **manche est lancée** (`/cm manche lancer`), sauf si la tâche est réglée sur « Enveloppe seulement » ;
- l'organisateur la **révèle** (bouton « Révéler à tous », `/cm tache reveler`, voix « révèle la tâche »).

Le texte d'une tâche secrète ne quitte jamais le serveur pour un non-admin. Une révélation n'est **pas annulée**
par `/cm annuler` (c'est un événement du direct, comme un TP) : pour la remettre au secret,
`/cm tache cacher <manche>` ; avant une nouvelle répétition, `/cm tache cachertout`.

## Ouvrir la lettre
1. La lettre pliée arrive en gros plan, le sceau à la couleur de la cire de la tâche.
2. **Arracher le sceau** : clic sur le sceau puis **tirer** (glisser la souris) : il suit la main en résistant, tremble,
   puis saute avec un craquement et des éclats de cire. Sans souris (manette, clavier) : **maintenir Espace** (ou
   Entrée) ; un simple **clic** sur le sceau l'arrache aussi tout seul. Lâché trop tôt, il se recolle.
3. La lettre se déplie : le rabat du haut se lève, celui du bas descend (puis les volets en plus d'une longue tâche,
   un par un), et la vue recule pour montrer toute la largeur. Clic, Espace ou Entrée passe l'animation.
4. Lecture : la feuille est en pixel art net (comme un livre du jeu), le texte à la machine à écrire. Une longue
   lettre défile : molette, glisser la feuille, flèches, Page préc./suiv., Début/Fin (une petite flèche ▼ le
   signale ; le jeu de diffusion la fait défiler tout seul, lentement). Échap ou J ferme.

Relire (touche J, `/cm tache`, lettre déjà ouverte) : elle se déplie toute seule. Tâche encore secrète : la lettre reste
scellée, le sceau résiste (« Tâche encore secrète »).

**Aperçu organisateur** : `/cm tache apercu <manche>` joue la lettre sur ton écran seulement (sceau, ouverture, mise en
page) : rien n'est révélé, rien n'est annoncé. Le bouton « Aperçu » de l'éditeur fait pareil avec le texte tapé.

## Mise en forme du texte
Quelques signes simples dans le texte de la tâche :

| Écrire | Donne |
|---|---|
| `# Tâche 4 : physique` | un titre en capitales rouges |
| une ligne vide | un nouveau paragraphe |
| `---` | un petit séparateur centré |
| `**gras**`, `__souligné__`, `~~barré~~` | gras, souligné, barré (pour les « corrections ») |
| `{rouge}…{/}` | encre de couleur : rouge, bleu, vert, gris, noir |
| `> Psst…` | une note manuscrite à l'encre bleue (pour un rebondissement) |
| `!règle Le plus rapide gagne.` | la règle finale, centrée, en gras |
| `[tampon: URGENT]` | un tampon rouge |
| `[signature: La Régie]` | la signature en bas à droite (sinon « LE CRAFTMASTER ») |
| `{joueur}`, `{equipe}`, `{temps}` | le nom du lecteur (celui de l'enveloppe), son équipe (pour l'instant son nom), la durée |
| `[secret]…[/secret]` | une **partie cachée** : une barre noire tant que l'organisateur ne l'a pas révélée |

Exemple :
```
# Tâche 4 : le pont
Construisez le pont le plus long.
Vous avez {temps}.

~~Interdit~~ {rouge}Autorisé{/} : la laine.
> Regarde sous la table…
[secret]Mais il doit porter un cochon.[/secret]
!règle Le plus long gagne.
```
**Parties cachées** : le texte d'une partie cachée ne quitte jamais le serveur avant d'être révélé (les joueurs
reçoivent seulement sa longueur). « Révèle la suite » (voix), `/cm tache suite [manche]` ou l'action
`task.reveal_more` révèle la partie suivante : les lettres ouvertes se mettent à jour sous les yeux des
candidats. L'organisateur voit les parties encore cachées en violet souligné. `/cm tache cacher` et
`/cm tache cachertout` les recachent. La carte à l'écran (TV, `!tache`) montre la même mise en forme, simplifiée,
et toujours les barres noires.

## Écrire une tâche
- **Écran** : `/cm tache editer [manche]` (ou le bouton « Modifier » sur la carte). À gauche les manches de la partie
  active (○ sans tâche, ● écrite et secrète, ✔ révélée, ▶ en cours) ; à droite : **titre**, **texte** (une ligne vide
  = nouveau paragraphe), **règles** (une par ligne, affichées en puces), **durée** affichée (secondes ou `m:ss`, vide
  = le chrono de la manche), **cire** (12 couleurs), révélée **au lancement** ou **par enveloppe seulement**.
  « Enregistrer » (changer de manche enregistre aussi), « Aperçu » joue l'ouverture avec le texte tapé (sur ton
  écran seulement), « Révéler », « À l'écran » (la carte 12 s sur tous les écrans), « Enveloppes » (une enveloppe à
  chaque candidat connecté, à son nom).
- **Commandes** : `/cm tache regler <manche> title|text|append|rules|seconds|wax|revealOnStart <valeur>` (voir
  [Commandes](Commandes.md)) ; dans un texte de commande, `\n` fait un retour à la ligne.
- **Voix** : « la tâche du pont : construisez le pont le plus long… » (le texte dicté est remis en forme),
  « ajoute à la tâche du pont : … », « mets la cire en or », « révèle la tâche », « montre la tâche »,
  « donne les enveloppes ».

Tout passe par l'exécuteur d'actions : mêmes résultats par l'écran, la commande ou la voix, et les textes
s'annulent avec `/cm annuler`.

## Enveloppes
- **Sceau à cire** (organisateurs, `/cm tache outils` ou onglet créatif) : clic droit → choisir le contenu (la tâche
  d'une manche, ou un texte libre), la cire, le nom écrit dessus, combien (ou **une par candidat**, chacune à son
  nom), ouverture annoncée ou discrète → « Sceller ». Le sceau garde la dernière couleur.
- `/cm tache enveloppe <manche> [joueurs|tous]` : enveloppes données directement (aux candidats connectés par défaut).
- **Ouvrir** : clic droit avec l'enveloppe en main, puis arracher le sceau (voir « Ouvrir la lettre »). L'enveloppe
  devient « ouverte » (lettre dépliée, cire brisée) et se relit d'un clic droit. Une enveloppe liée à une manche lit toujours la dernière version de la tâche.
  Annonce à tous (barre d'action + son) quand le sceau saute : « ✉ Ensuri ouvre sa tâche ».
- **Où la poser** : accroupi + clic droit sur un bloc pose l'enveloppe (cire de sa couleur), selon la face visée :
  - le **dessus** : à plat sur la table ;
  - un **côté** : épinglée au mur, debout ;
  - le **dessous** : collée au plafond, à plat ;
  - le **dessous d'une chaîne** (fer ou cuivre) : **pendue** au bout d'un petit bout de chaîne, de face vers le joueur.
  Une chaîne utilisée (clic droit) sur une enveloppe du plafond la suspend ; une seconde fois, la remet à plat.
  La caméra « lieu de la tâche » de l'intro vise l'enveloppe où qu'elle soit.
  **Clic droit dessus = la lire sur place**, comme en main (arracher le sceau, puis relire) : elle reste posée,
  ouverte. **La casser = la récupérer** (elle tombe avec son contenu ; en créatif elle va dans l'inventaire) ;
  accroupi + clic droit la ramasse aussi (dans la main).
- Avec le lancement enchaîné d'une manche ([Réalisation](Realisation.md)), une tâche révélée au lancement l'est
  quand l'image s'ouvre sur les candidats, pas pendant l'intro.
- `/cm tache fermer [joueurs]` ferme les lettres ouvertes (tout le monde lève les yeux).

## Consulter la tâche (tout le monde)
Touche **J** (catégorie CraftMaster) ou `/cm tache` (`/cm tache`) : la tâche du moment — celle de la manche en
cours, sinon la dernière révélée. Secrète : l'enveloppe scellée « Tâche encore secrète ». Pour un organisateur,
même secrète (entre deux manches : la prochaine manche qui a une tâche), avec « Révéler à tous », « À l'écran »,
« Modifier ».

## Notes cachées
Créer : `/cm note creer <titre>` (alias `/cm note`) donne l'**outil de cachette** de la note ;
`/cm note texte <note> <texte>` ou l'éditeur (clic droit dans le vide avec l'outil, ou `/cm note editer <note>`).

Cacher (outil en main, organisateur) : **clic droit sur un bloc** — la cachette dépend du bloc :

| Bloc visé | Cachette | Pour la trouver |
|---|---|---|
| tapis, dalle, plaque de pression, trappe | **sous le tapis** | s'accroupir + clic droit |
| bibliothèque, bibliothèque sculptée, pupitre | **dans un livre** | clic droit |
| coffre, tonneau, tout conteneur | **double fond** (ouvert normalement il paraît vide) | s'accroupir + clic droit |
| autre bloc (pot, vase, cheminée…) | **cachée dedans** | clic droit |
| tableau, cadre (entité) | **derrière le tableau** | clic droit |
| accroupi + clic droit sur une face | **feuille épinglée** sur cette face (dessous de table, dos d'armoire) | clic droit sur la feuille |

Et aussi : **bouteille à la mer** (bouton « En bouteille » / `/cm note bouteille <note>`, à cacher dans un coffre, à
jeter dans l'eau, à donner), qu'on débouche d'un clic droit.

Réglages par note (éditeur ou `/cm note regler <note> …`) :
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
(sous le tapis) »), l'éditeur liste les cachettes et qui a trouvé quoi et à quelle heure (`/cm note info <note>`).
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

## Police et dessins
- La police machine à écrire `craftmaster:typewriter` (lettres de tâche, titre tapé sur le noir au lancement d'une
  manche) est une conversion pixel à pixel de **Salmon Typewriter 9** de Phildjii (pixel, empattements façon
  Courier, accents français). Sa licence autorise de l'utiliser comme ressource affichée dans un jeu, mais interdit
  de redistribuer la police : seule la conversion en image est dans le mod, jamais le `.ttf`, avec la licence
  d'origine (`assets/craftmaster/font/LICENSE-Salmon-Typewriter.txt`). Les caractères qu'elle n'a pas viennent de
  **Cairopixel** de GGBotNet (licence SIL OFL 1.1, `font/OFL.txt`), puis de la police du jeu. Les deux polices sont
  rangées dans `C:\Users\yanni\Archives\Polices\`. Refaire la conversion (les extraire d'abord) :
  `python scripts/convert_typewriter_font.py "<Salmon Typewriter 9 Regular.ttf>" <Cairopixel.ttf> [<License.txt>]`.
- Papier, sceau et cire arrachée : `python scripts/gen_letter_art.py` (outils de textures de l'atelier SteveParty) ;
  objets et bloc : `scripts/gen_assets.py`.

## Fichiers
- Textes des tâches : dans `config/craftmaster/config.yml`, clé `task` de chaque manche ([Données](Donnees.md)).
- Révélations, parties cachées révélées et notes (cachettes, trouvailles) : `config/craftmaster/tasks.json`.
- Twitch : `config/craftmaster-client.json` (côté client).
