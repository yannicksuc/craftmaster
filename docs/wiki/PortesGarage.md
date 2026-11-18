# Portes de garage et réglage WorldEdit

## Porte de garage animée

Une porte construite avec **vos propres blocs** (n'importe lesquels) qui remonte en glissant dans le linteau, puis
redescend. Rien n'est téléporté : pendant le mouvement, chaque jeu dessine les blocs de la porte qui montent en
continu (départ et arrivée adoucis) ; arrivé en haut de l'ouverture, chaque rangée rentre dans le linteau comme une
porte qui s'enroule. Fermée, la porte redevient vos vrais blocs ; ouverte, l'ouverture est vide.

### Faire une porte
1. Construire le panneau dans l'ouverture (ex. 3 × 3 de béton rouge entre deux piliers de quartz).
2. Poser le **moteur de porte de garage** (`craftmaster:garage_door_motor`, onglet créatif CraftMaster ; recette :
   3 fers en haut, fer · piston · fer, fer · redstone · fer) **juste au-dessus du panneau, au milieu du linteau**,
   face à vous. Le moteur annonce ce qu'il a trouvé : « Porte trouvée : 3 × 3 (9 blocs) ».
3. Le moteur trouve tout seul le panneau : les blocs **du même type que celui juste en dessous de lui**, qui se
   touchent dans le plan du mur, sous le moteur (1024 blocs au plus, 32 blocs autour du moteur). Le plan est celui
   où la porte est la plus large.
4. Porte faite de **plusieurs sortes de blocs** (vitres, motifs…) : donner sa zone,
   `/cm plateau garage zone <moteur x y z> <coin x y z> <coin x y z>` (tous les blocs de la boîte font partie de la
   porte). `/cm plateau garage zone <moteur> auto` revient à la détection.
5. **Camoufler le moteur** pour garder le look exact du linteau : clic droit sur le moteur avec un bloc en main
   (organisateurs) — le moteur prend son apparence. Accroupi + clic droit main vide : il reprend la sienne (sans
   camouflage, ce geste redit quelle porte il a trouvée).

Tant que la porte est fermée, le moteur relit le panneau : on peut la modifier, la prochaine ouverture prendra la
nouvelle version. Les blocs qui gardent des données (coffres, panneaux, bannières…) ne font pas partie d'une porte.
Casser le moteur remet toujours la porte fermée : vos blocs ne se perdent jamais.

### L'ouvrir et la fermer
- **Redstone** sur le moteur ou sur un bloc autour de l'ouverture (bouton ou levier posé sur un pilier, fil…) :
  - une **impulsion** (bouton) ouvre une porte fermée, ferme une porte ouverte ;
  - un **signal tenu** (levier) l'ouvre (ou la ferme), et le couper la renvoie dans l'autre sens.
- **Créatif** : clic droit main vide sur le moteur.
- **Commande** : `/cm plateau garage ouvrir|close|toggle <x y z>` (`/cm plateau garage`), ou par son nom
  `/cm plateau garage porte <nom> open|close|toggle`.
- **Régie** : actions `garage.open`, `garage.close`, `garage.toggle` (`{"name": "atelier"}`, sans nom = la seule
  porte nommée). À la voix : « ouvre la porte du garage », « ferme la porte de l'atelier ». Stream Deck :
  `http://127.0.0.1:<port>/garage/toggle?name=atelier` (aussi `/garage/open`, `/garage/close`).
  Une porte se nomme avec `/cm plateau garage nom <x y z> <nom>` (gardé dans `config/craftmaster/garages.json`) ;
  `/cm plateau garage liste`, `/cm plateau garage oublier <nom>`.

Relancer pendant le mouvement repart dans l'autre sens, depuis là où est la porte.

### Personne ne reste coincé
- À l'ouverture, l'ouverture se libère **par le bas**, rangée par rangée, au fur et à mesure que le panneau monte
  (des barrières invisibles tiennent la place pendant le mouvement).
- À la fermeture, quiconque se trouve là où le panneau descend est **poussé hors du plan de la porte**, du côté où il
  se tient (devant le moteur s'il est pile au milieu), juste avant que le panneau ne l'atteigne.
- Serveur arrêté pendant le mouvement : au redémarrage la porte finit son mouvement (rien à faire).

### Réglages
- `/cm plateau garage vitesse <x y z> <secondes par bloc>` : 0,6 s par bloc de hauteur par défaut (une porte de 3 de haut
  s'ouvre en 1,8 s), de 0,2 à 3 s.
- `/cm plateau garage info <x y z>` : taille trouvée, état, nom, vitesse, zone.

## WorldEdit sans mises à jour par défaut

WorldEdit Fabric oublie `//perf` à chaque connexion (le réglage n'est pas sauvegardé, et il n'existe pas d'option de
configuration). CraftMaster règle donc **la session WorldEdit de chaque joueur à sa connexion** comme
`//perf neighbors off` + `//perf update off` : un gros collage laisse le sable, l'eau, la redstone et les plantes
comme ils sont.

- WorldEdit reste **facultatif** : sans lui, rien ne se passe (tout passe par réflexion, aucune dépendance).
- Réglage : `config/craftmaster/worldedit.json`, `"perfOff": true` (défaut). `/cm worldedit perf on`
  (`/cm worldedit`) remet le comportement normal de WorldEdit aux prochaines connexions ; `/cm worldedit perf off` le recoupe (et
  l'applique aux joueurs en ligne). `/cm worldedit` seul montre l'état et votre session.
- Un joueur peut toujours taper `//perf ...` pour le reste de sa session.
