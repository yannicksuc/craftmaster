# Tableaux suspendus

Un tableau est un cadre doré au fond rouge (le style de l'affiche CraftMaster) avec un personnage dedans — ou qui
en sort —, suspendu par une ficelle à un rail au plafond. Le personnage porte le skin du joueur classé à un rang
d'une saison et a les proportions d'un **bébé zombie** (grosse tête, petit corps : exactement les proportions des
bébés humanoïdes de Minecraft). Sous le cadre, une étiquette : « #1 LordFinn / 16 pts » (mêmes couleurs que les
busts).

## Poser un tableau
- **Objet « Tableau »** (onglet créatif CraftMaster, ou craft : ficelle en haut, tableau vanilla au centre, lingots
  d'or autour). Clic droit **sous** un bloc du plafond : le tableau y est accroché, l'image tournée vers soi.
  Il rejoint la galerie de la dernière saison, au rang suivant.
- Par commande : `/cfpt place <saison> <rang>` en regardant le bloc du plafond, ou
  `/cfpt place <saison> <rang> <x y z du bloc du plafond> <north|south|east|west>` (console).
- **Retirer** : le frapper (comme une peinture ; rendu en survie), ou `/cfpt remove` en le regardant.
- Les tableaux sont des entités sauvegardées avec le monde : ils restent après un redémarrage.

Le rail lui-même se construit avec des blocs (chaînes, barres de fer, dalles…) : le tableau ajoute le chariot en
laiton et la ficelle. Prévoir de la hauteur : un tableau mesure 1,5 bloc, plus la ficelle.

## Réglages (organisateurs)
Clic droit sur le tableau (ou `/cfpt edit`) : l'écran laisse voir le tableau à droite, les changements de pose
s'y voient en direct.

| Réglage | Effet |
|---|---|
| Saison (galerie) | Les tableaux d'une même saison forment une galerie |
| Rang / Joueur | « Rang N » (défaut) : montre le joueur classé N ; « Joueur » : toujours ce pseudo |
| Nom affiché | Remplace le pseudo sur l'étiquette (vide = pseudo) |
| Skin (adresse) | Adresse http(s) d'une image de skin 64×64 (ou ancien format 64×32). Vide = skin du joueur. Une image invalide ou injoignable donne le skin par défaut |
| Pose | Une pose de la bibliothèque ; « Éditer… » ouvre l'éditeur de poses |
| Ficelle (blocs) | Longueur de la ficelle, de 0 à 16 blocs : on peut pendre aussi bas qu'on veut. `auto` = longueur légèrement différente pour chaque tableau (0,55 à 1,05 bloc) |
| Effet Antichamber | Voir plus bas (désactivé par défaut) |

Mêmes réglages en commande, sur le tableau regardé ou sur des entités visées :
`/cfpt set [cibles] season|rank|player|name|skin|pose|window|height <valeur>` (`name -` et `skin -` remettent le
pseudo / le skin du joueur, `height auto`, `window true|false`).
Exemple : `/cfpt set @e[type=craftmaster:portrait,sort=nearest,limit=1] height 3.5`.

## Accrochage et balancement
- La ficelle est **une seule ligne droite verticale** du chariot jusqu'au cadre, quelle que soit sa longueur,
  pendant le balancement comme pendant un réarrangement.
- Elle est nouée sur le haut du cadre, **juste au-dessus du centre de gravité** (cadre + personnage dans sa pose,
  chaque pièce pesant son volume) : le tableau pend droit, même quand le personnage le porte au-dessus de sa tête.
- Chaque tableau tourne doucement autour de sa ficelle (± 2,5°, 8 à 12 s par oscillation, chacun à son rythme).
  Calculé par le jeu de chaque joueur, sans trafic réseau.

## Réarrangement à la publication des scores
Les tableaux d'une galerie occupent des colonnes : leurs emplacements, de gauche à droite vu du public (le côté
où regardent les images). Le rang 1 est à gauche. Quand les scores sont publiés et que l'ordre change, chaque
tableau emporte son joueur vers sa nouvelle colonne :
1. ceux qui vont vers la gauche s'avancent, ceux qui vont vers la droite reculent (si deux tableaux qui vont du
   même côté se croisent, ils prennent deux plans différents) ;
2. glissement latéral jusqu'à la nouvelle colonne, le chariot suit le rail ;
3. retour dans le rang.

Les ficelles ne se croisent jamais. Un joueur qui sort de la galerie laisse son tableau au nouveau venu.
Une saison cachée ne bouge pas (l'ordre trahirait le classement) : ses tableaux restent anonymes (« #1 ? », skin
par défaut) jusqu'à la révélation. Tant que le déroulé du direct n'a pas sa commande « publier », chaque
changement de score compte comme une publication ; `/cfpt publish <saison>` force un réarrangement.

Pendant la révélation, `/cfpt highlight <saison> <rang>` illumine le tableau de ce rang pendant 5 s (halo doré,
pleine lumière, contour lumineux).

## Poses
Bibliothèque partagée par tous les tableaux : `config/craftmaster/poses.json` sur le serveur (envoyée à chaque
joueur). Poses fournies, inspirées de l'affiche : **classique** (buste), **sort du cadre**, **assis dans le
cadre**, **porte le cadre**, **se penche dehors**, **salut**, **bras croisés**, **trône**, et **sort par
derrière** (le haut du corps sort du dos plat du tableau, vu de derrière). `/cfpt poses` les liste.

**Éditeur de poses** (« Éditer… » dans l'écran du tableau, ou `/cfpt poseEditor`) : choisir une partie
(Ensemble, Tête, Corps, Bras D/G, Jambe D/G, Cadre), régler ses rotations (et pour l'ensemble : taille et
décalage ; pour le cadre : bascule et inclinaison) ; le tableau montre le résultat en direct. Puis :
- « Nouvelle pose » / « Écraser » enregistre sous le nom tapé ;
- « Supprimer » retire la pose du nom tapé ;
- « Partir de » charge une pose existante ;
- « Utiliser la pose » l'enregistre et la donne au tableau.

Un décalage Z négatif place le personnage **derrière** la toile : il sort alors du dos du tableau. Dans tous les
cas le personnage reste de son côté de la toile : une pose de face ne dépasse jamais par derrière, une pose de dos
ne traverse jamais la peinture (il est décalé automatiquement s'il le faudrait).

Supprimer une pose utilisée : les tableaux concernés prennent « classique ». Un fichier illisible est mis de côté
(`poses.json.broken`) et remplacé par les poses fournies.

## Effet Antichamber (option par tableau)
Le cadre devient une **fenêtre** sur une petite pièce derrière lui : le personnage n'est visible qu'à travers
l'ouverture (aucun membre ne dépasse, il est poussé derrière l'ouverture), le fond rouge remplit toute
l'ouverture autour de lui et reste fixe par rapport au tableau quand on se déplace (on voit la profondeur de la
pièce). Vu de dos ou de côté, on ne voit que l'arrière blanc de la pièce.

Limite technique : Minecraft 26.3 ne donne pas accès au stencil. L'effet utilise un « mur » invisible
(profondeur seule) autour du cadre, dessiné après les entités et avant l'intérieur du tableau. Ce mur peut
masquer, dans une zone d'environ un bloc autour du cadre, ce qui est dessiné après les entités et se trouve
derrière lui (eau, verre teinté, particules). Les blocs opaques et les autres entités ne sont pas touchés.
