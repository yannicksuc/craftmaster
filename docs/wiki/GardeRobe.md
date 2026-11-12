# Garde-robe : changer de tenue en un clic

Pour les épreuves « portez une tenue », « déguisez-vous », un défilé… La **garde-robe** est une armoire en noyer à
filets dorés, deux blocs de haut, avec un miroir sur la porte de droite (onglet créatif CraftMaster, ou planches +
lingot d'or + coffre + vitre). Ses portes s'ouvrent pendant qu'on choisit (on voit les habits pendus).

Une tenue = un nom et quatre pièces (tête, corps, jambes, pieds), portées dans les emplacements d'armure. Elle marche
avec n'importe quelle armure ou tête, et surtout avec les vêtements de **Selfexpression** (mod facultatif du modpack
de l'event, 322 tenues).

![Onze tenues enfilées à la garde-robe, Minecraft 26.2](img/garde-robe-tenues.png)

## Utiliser

- **Clic droit sur l'armoire** : l'écran des tenues. Il s'adapte à la fenêtre (plus de cartes sur un grand écran).
  À gauche, sur toute la hauteur, toi habillé de ce qui est sous la souris (la tête suit la souris, **glisser** pour
  le faire tourner) et le nom de la tenue ; à droite les tenues en cartes, molette pour défiler.
- **Clic sur une tenue** : tu l'enfiles en entier. **Clic sur une seule pièce** (l'icône du chapeau, de la
  veste…) : tu n'enfiles qu'elle et tu gardes le reste. On mélange ainsi les tenues (« Tenue mélangée »).
- **Clic sur l'étoile ☆** d'une carte : favori (★ ; encore un clic pour l'enlever). Les favoris passent en tête, le
  bouton **★ Favoris** n'affiche qu'eux. Ils sont à chaque joueur et valent dans toutes les garde-robes.
- **Au pied de l'aperçu, quatre cases** (tête, corps, jambes, pieds) montrent ce que tu portes. **Clic** sur une
  case : la liste défile jusqu'à la tenue d'où vient la pièce, qui clignote (recherche, tags et favoris sont remis à
  zéro s'ils la cachaient). **Clic droit** : tu enlèves la pièce (une pièce de costume rend ce que tu portais là
  avant, une pièce à toi va dans l'inventaire).
- **⇄** (coin de l'aperçu) : échange tes affaires et ta dernière tenue. En costume, il remet tes vêtements ; en
  tes vêtements, il remet la dernière tenue exactement comme elle était. Son infobulle dit ce qui va revenir.
- **Poubelle** (à côté) : le costume part, tes vêtements reviennent (la dernière tenue est oubliée).
  **Maj + clic** : tout ce que tu portes est supprimé, tes propres vêtements aussi, après une confirmation.
- **Accroupi + clic droit** sur l'armoire, sans ouvrir l'écran : tu reprends tes vêtements.
- **Barre de recherche** en français, sans tenir compte des accents ni des majuscules, sur le nom de la tenue et de
  ses pièces. Les mots français cherchent aussi leur traduction anglaise, en mots entiers (les vêtements de
  Selfexpression ont des noms anglais) : « médecin », « chapeau », « veste rouge », « bottes », « lutin », « noël »,
  « bonhomme de neige »… Tous les mots tapés doivent correspondre.

## Tags

Le bouton **# Tags** ouvre, à la place des tenues, les tags par catégorie avec le nombre de tenues de chacun. On en
coche un ou plusieurs (« ✕ Tout décocher » pour repartir) : seules les tenues qui ont tous les tags cochés restent,
et le bas de l'écran les rappelle. Les tags comptent aussi dans la recherche et s'affichent dans l'infobulle d'une
tenue.

Ils sont trouvés tout seuls dans les noms de la tenue et de ses pièces (en anglais ou en français, mots entiers ;
pas dans les identifiants d'objets) :

| Catégorie | Exemples |
|---|---|
| Métier | Médecin, Policier, Pompier, Militaire, Chevalier, Ouvrier, Mineur, Forgeron, Jardinier, Cuisinier, Marin, Pilote, Scientifique, Avocat, Prisonnier, Chasseur, Voleur, Mafieux, Royauté, Bouffon, Cowboy |
| Thème | Médiéval, Futuriste, Espace, Noël, Halloween, Far west, Apocalypse, Fantastique, Science-fiction, Hiver, Automne, Animaux, Empire |
| Contexte | Ville, Campagne, Mer, Montagne, Forêt, Château, Chantier, Hôpital, Prison, Champ de bataille |
| Couleur | Rouge, Bleu, Vert, Jaune, Orange, Violet, Rose, Noir, Blanc, Gris, Marron, Cyan, Doré |
| Type | Veste, Manteau, Sweat, T-shirt, Robe, Uniforme, Combinaison, Armure, Chapeau, Casque, Capuche, Masque, Lunettes, Pantalon, Chaussures, Sac, Tablier |
| Matière | Cuir, Fer, Or, Diamant, Netherite, Mailles, Cuivre, Tissu, Métal |
| Utilité | Protection, Protection chimique, Chaud, Travail, Chic, Déguisement, Discrétion |
| Loisir | Sport, Jeu vidéo, Fête, Musique, Plage, Jardinage, Aventure |
| Partie du corps | Tête, Corps, Jambes, Pieds, Tenue complète (d'après les pièces) |

Un organisateur ajoute ses propres tags à une tenue (« défilé », « manche 3 »…) : Modifier, choisir la tenue, les
taper séparés par des virgules, **Enregistrer les tags** (catégorie « Tags de l'organisateur »). Le dictionnaire des
tags automatiques est dans `OutfitTags` : un mot de plus = une ligne.

## Ce que contient une garde-robe

**Toutes les tenues du jeu**, sauf si un organisateur en décide autrement : d'abord les tenues prêtes (ci-dessous),
puis chaque ensemble des objets du jeu (pièces de même nom : `xxx_helmet`, `xxx_chestplate`, `xxx_leggings`,
`xxx_boots`, deux au moins), les vêtements des mods d'abord, les armures vanilla à la fin (265 en tout avec
Selfexpression). Les ensembles sans nom portent le nom de leur pièce de corps, dans la langue du joueur.

Dès qu'un organisateur modifie une garde-robe (ajout, suppression, renommage, ordre), elle garde **sa propre liste** ;
« Remettre toutes les tenues » la ramène à toutes les tenues.

**Tenues prêtes** (en tête de liste, avec un nom français) : `config/craftmaster/tenues.json` si le fichier existe
(modifiable à la main, relu au démarrage du serveur), sinon 20 tenues de Selfexpression : médecin, policier,
chevalier, roi, bouffon, mafieux, capitaine, mineur, ouvrier du BTP, jardinier, forgeron, ingénieur, avocat, costume
strict, cowboy, soldat, prisonnier, lutin de Noël, pilote spatial, vagabond. Sans Selfexpression : 8 tenues en
armures vanilla et cuir teint (chevalier, roi, chef cuisinier, bleu de travail, plongeur, citrouille, cowboy,
squelette).

## Deux types de garde-robes

| | Type : costumes (émission) | Type : rangement (survie) |
|---|---|---|
| Posée par | un organisateur ou un joueur en créatif | un joueur en survie |
| Contenu | toutes les tenues (ou la liste choisie) | vide au départ : on y range ses vrais habits |
| Enfiler | donne des **copies** marquées « Costume : retourne à la garde-robe » ; elle ne se vide jamais | **échange** la tenue avec ce que tu portes (« Affaires de Pseudo » prend sa place) |
| Tes vêtements | mis de côté sur toi, pièce par pièce (gardés à la mort et à la déconnexion) jusqu'à ⇄ ou la poubelle | rangés dans la garde-robe, un clic dessus pour les remettre |
| ⇄ / poubelle | les copies disparaissent (même rangées dans l'inventaire), tes vêtements reviennent | échange avec « Affaires de Pseudo » |
| Cassée | rien ne tombe (que des copies) | les habits tombent |

Une pièce vide dans une tenue enfilée en entier = rien à cet endroit (la tenue est le look entier). Ce que tu
enfiles toi-même pendant un costume reste à toi (rendu dans l'inventaire si quelque chose est déjà mis de côté à
cet endroit).

## Modifier (organisateurs ; tout le monde dans une garde-robe de rangement)

**✎ Modifier** (en haut à droite, allumé quand il est actif, recliquer pour sortir) : un clic **choisit** une tenue
(cadre doré) au lieu de l'enfiler, et deux rangées de boutons apparaissent :
- un nom · **Ajouter ma tenue** (ce que tu portes devient une nouvelle tenue ; refusé si la même existe déjà) ·
  **Renommer** (la tenue choisie) ;
- tenue choisie : **Remplacer** (par ce que tu portes) · **Supprimer** (rangement : **Sortir**, les habits vont
  dans l'inventaire) · **◀ ▶** (déplacer) ; au-dessus, ses tags et **Enregistrer les tags** ;
- sans tenue choisie : **Type : costumes / rangement** · **Remettre toutes les tenues**.

En mode Modifier, les tenues du jeu qu'une liste choisie a retirées restent affichées **en gris** à la fin : un clic
la remet dans la garde-robe.

Pour préparer une tenue : enfiler les pièces (depuis l'onglet créatif de Selfexpression, par exemple), Modifier,
taper un nom, Ajouter ma tenue. Copier une garde-robe préparée : pioche du bloc avec Ctrl en créatif.

## Rendu

La garde-robe ne dessine rien sur le joueur : elle met des objets dans ses emplacements d'armure, et chaque mod les
dessine comme d'habitude (Selfexpression par son `ArmorRenderer`). Aucune mixin de rendu n'est ajoutée : les
vêtements suivent donc les animations (Emotecraft, Player Animation Library) et les statues qui affichent l'armure
comme n'importe quelle armure. L'aperçu de l'écran est une copie du joueur (même skin, sans étiquette de nom) qui ne
touche jamais au vrai joueur.

## Développement

- Code : `wardrobe/` (commun) — `Dressing` (ce que fait un changement de tenue, entière ou pièce par pièce, sans
  Minecraft, testé par `DressingTest`), `OutfitSearch` (recherche et dictionnaire français → anglais,
  `OutfitSearchTest`), `OutfitTags` (tags par catégorie, `OutfitTagsTest`), `Outfit` (nom, pièces, `key()` = ses pièces), `WardrobeState` (pièce jointe Fabric sur le
  joueur : vêtements mis de côté, tenue portée, favoris), `Catalogue` (tenues prêtes, `tenues.json`, ensembles du
  jeu), `WardrobeBlock` (deux blocs, portes), `WardrobeBlockEntity`, `WardrobeNetwork`, `Wardrobes` (enregistrement,
  actions) ; `client/wardrobe/` — l'écran.
- Ressources : `python -I scripts/gen_wardrobe.py` (indépendant de `gen_assets.py`) écrit l'état de bloc, les quatre
  modèles (bas / haut, fermé / ouvert), le modèle d'objet (l'armoire entière, réduite et recentrée), les textures,
  la recette, la loot table et les textes, dans `assets/craftmaster_wardrobe/lang/` (Minecraft fusionne les fichiers
  de langue de tous les espaces de noms : pas de conflit avec `gen_assets.py`).
- `DevInput` (client de dev) sait taper du texte : `type mot_avec_des_espaces` (les `_` deviennent des espaces).
