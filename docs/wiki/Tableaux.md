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
| Skin (adresse) | Adresse http(s) d'une image de skin 64×64 (ou ancien format 64×32), ou **HD** : 128×128, 256×256, 512×512, 1024×1024 (tout multiple de 64 jusqu'à 1024, et les anciens formats HD 128×64…), 6 Mo au plus. Bras fins détectés à toutes les tailles. Vide = skin du joueur, **avec sa cape** s'il en a une. Une image invalide ou injoignable donne le skin par défaut |
| Pose | Une pose de la bibliothèque ; « Éditer… » ouvre l'éditeur de poses |
| Ficelle (blocs) | Longueur de la ficelle, de 0 à 16 blocs : on peut pendre aussi bas qu'on veut. `auto` = longueur légèrement différente pour chaque tableau (0,55 à 1,05 bloc) |
| Antichamber px % | Effet fenêtre (Oui / Non, désactivé par défaut), profondeur de la pièce en pixels (4 à 96, défaut 16) et sa taille en % de l'ouverture (100 à 200, défaut 140) — voir plus bas |

Mêmes réglages en commande, sur le tableau regardé ou sur des entités visées :
`/cfpt set [cibles] season|rank|player|name|skin|pose|window|windowDepth|windowSize|height <valeur>` (`name -` et
`skin -` remettent le pseudo / le skin du joueur, `height auto`, `window true|false`, `windowDepth 32`,
`windowSize 180`).
Exemple : `/cfpt set @e[type=craftmaster:portrait,sort=nearest,limit=1] height 3.5`.

## Accrochage et balancement
- La ficelle est **une seule ligne droite verticale** du chariot jusqu'au cadre, quelle que soit sa longueur,
  pendant le balancement comme pendant un réarrangement.
- Elle est nouée sur le haut du cadre, **juste au-dessus du centre de gravité** (cadre + personnage dans sa pose,
  chaque pièce pesant son volume) : le tableau pend droit, même quand le personnage le porte au-dessus de sa tête.
- Chaque tableau tourne doucement autour de sa ficelle (± 2,5°, 8 à 12 s par oscillation, chacun à son rythme).
  Calculé par le jeu de chaque joueur, sans trafic réseau, image par image sur une horloge lissée (pas d'à-coups
  à 20 Hz ni de saut quand le serveur envoie l'heure) ; les mouvements suivent des courbes adoucies.

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
derrière** (le haut du corps sort du dos plat du tableau, vu de derrière). Les poses jouent avec le cadre : mains
sur les montants, jambe passée par-dessus la barre du bas, assis sur la barre, penché hors de la peinture… `/cfpt poses` les liste.

Chaque champ de l'écran du tableau a une infobulle ; Entrée enregistre.

**Éditeur de poses** (« Éditer… » dans l'écran du tableau, ou `/cfpt poseEditor`). À droite, un **aperçu 3D**
du tableau (même modèle, skin, cape et cadre que dans le monde, rien de coupé), et le tableau dans le monde montre
aussi la pose en direct.

| Geste / touche | Effet |
|---|---|
| Clic sur une partie (ou le cadre) dans l'aperçu | la choisir (elle passe en jaune ; celle sous la souris en bleu, avec une infobulle) |
| Tirer une partie | la tourner : tête, corps, ensemble = tourner (gauche/droite) et hocher (haut/bas) ; bras, jambes = lever sur le côté et vers l'avant / l'arrière. Ce qui est sous la souris la suit |
| Maj + tirer | le troisième axe (pencher la tête / le corps, vriller un membre) |
| Ctrl + tirer | déplacer tout le personnage |
| Tirer le cadre | le basculer (haut/bas) et l'incliner (gauche/droite) |
| Clic droit tiré, ou tirer le fond | tourner la vue ; Ctrl + molette : zoom ; Origine (ou bouton ⌂) : vue de face |
| Molette, flèches | 1° sur l'axe affiché en haut de l'aperçu (Maj : 5°) ; X, Y, Z choisissent cet axe (un glisser le règle sur l'axe le plus bougé) |
| 1 à 8 | choisir Ensemble, Tête, Corps, Bras D, Bras G, Jambe D, Jambe G, Cadre |
| Ctrl+Z / Ctrl+Y (ou Ctrl+Maj+Z) | annuler / refaire (boutons ← →) ; un glisser entier, un curseur, une rafale de molette = une seule étape |
| M (bouton ↔) | miroir de toute la pose, gauche ↔ droite |
| C (bouton ») | copier le bras / la jambe choisi(e) de l'autre côté, en miroir (pose symétrique) |
| R (bouton 0) | remettre la partie droite (pour l'Ensemble : place et taille aussi ; pour le Cadre : à plat) |
| H (bouton •/○) | cacher / montrer la partie |

Le `[?]` en haut à droite de l'aperçu rappelle tout cela. À gauche, les curseurs donnent les valeurs exactes
(ils suivent les gestes) ; puis :
- « Nouvelle pose » / « Écraser » enregistre sous le nom tapé ;
- « Supprimer » retire la pose du nom tapé ;
- « Partir de » charge une pose existante (annulable) ;
- « Utiliser la pose » l'enregistre et la donne au tableau.

**La toile est une vraie coupe** : ce qui est derrière la surface peinte est « dans le tableau » et ne se voit
pas. Un personnage qui se penche dehors garde les jambes dans la peinture ; un pied passé par-dessus la barre du
bas sort du cadre. Un personnage tourné vers l'arrière (Ensemble, rotation Y à 180°) sort au contraire par le dos
plat du tableau, et ce qui reste devant la toile est caché. La coupe ne vaut que dans le contour du cadre (un
personnage qui porte le cadre au-dessus de sa tête n'est pas coupé).

Supprimer une pose utilisée : les tableaux concernés prennent « classique ». Un fichier illisible est mis de côté
(`poses.json.broken`) et remplacé par les poses fournies.

## Effet Antichamber (option par tableau)
Le cadre devient une **fenêtre magique** : à travers l'ouverture on voit le personnage dans un espace profond,
dans une petite pièce tendue de velours rouge (rideau au fond, murs plus sombres), fixe par rapport au tableau.
Sa **profondeur** et sa **taille** (100 à 200 % de l'ouverture) se règlent par tableau : plus la pièce est grande,
plus on en découvre en se déplaçant (parallaxe). Aucun membre ne dépasse : le personnage est entièrement « derrière la vitre ». De côté, de
dessus, de dessous, le tableau reste un cadre mince ; de dos, on ne voit qu'une toile blanche : rien de
l'intérieur n'est visible ailleurs que par l'ouverture. Réglage : bouton « Fenêtre » de l'écran,
ou `/cfpt set window true`.

Technique : Minecraft 26.3 ne donne pas accès au stencil. L'intérieur est donc une illusion d'optique : chaque
point du personnage et du rideau est ramené, le long du regard du joueur, sur le plan de la toile (il reste
exactement là où l'œil le voit), puis découpé au bord de l'ouverture. Rien n'existe hors du cadre, donc rien ne
peut dépasser ni masquer le reste du monde. Limite : sous un angle très rasant, les textures des faces du
personnage peuvent se déformer très légèrement.
