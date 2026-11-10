# Plateau : décor du studio

Tout le mobilier du plateau télé, dans l'onglet créatif **CraftMaster** (et en recette). Or, velours rouge et
papier crème comme l'affiche ; rien n'est copié d'une émission existante.

## Sièges
| Objet | Bloc | Utilisation |
|---|---|---|
| Trône du maître du jeu | `craftmaster:throne` | Or et velours capitonné, sceau CM au dossier (2 blocs de haut). Clic droit : on s'assoit, comme sur les [chaises](Chaises.md) ; s'accroupir pour se lever |
| Fauteuil de l'assistant | `craftmaster:assistant_chair` | Velours, pieds dorés ; s'assoit pareil |
| Canapé de velours | `craftmaster:sofa` | Une place par bloc ; posés côte à côte (même sens), ils forment un seul canapé : les accoudoirs ne restent qu'aux bouts |

Les trois sont posés face à soi (le dossier de l'autre côté), comme les chaises.

## Éclairage
| Objet | Bloc | Utilisation |
|---|---|---|
| Lampadaire | `floor_lamp` | Abat-jour crème en haut (2 blocs de haut), lumière 15. Clic droit : allumer / éteindre |
| Lampe de bureau | `desk_lamp` | Lampe de banquier en verre vert, lumière 12, clic droit |
| Suspension | `ceiling_pendant` | Se pose sous un plafond (tombe si le bloc au-dessus part), lumière 15, clic droit |
| Projecteur | `spotlight` | Sur le sol, ou suspendu sous un plafond. Voir ci-dessous |
| Enseigne « EN DIRECT » | `on_air_sign` | Caisson lumineux contre un mur (plus large que le bloc). Clic droit ou courant de redstone (allumée tant qu'elle est alimentée) |

### Projecteurs
- Faisceau visible (cône de lumière qui s'estompe) jusqu'au premier bloc rencontré (48 blocs max), avec une
  tache de lumière où il arrive ; **vraie lumière** : un bloc `minecraft:light` invisible (niveau 15) est posé là
  où le faisceau touche, déplacé quand le projecteur bouge, retiré quand il s'éteint ou est cassé.
- Posé : il éclaire droit devant (dans le sens où l'on regarde), un peu vers le bas s'il est suspendu.
- Clic droit : allumer / éteindre. **Accroupi + clic droit (main vide)** : il pointe là où l'on regarde (se mettre
  à côté et viser la cible). **Teinture** à la main : couleur du faisceau (blanc = blanc chaud).
- Il tourne en douceur (lyre et tête motorisées) ; en mode **suivi**, il suit un joueur en permanence.
- Commandes et voix ci-dessous (« projecteurs sur Ensuri »).

## L'émission
| Objet | Bloc / objet | Utilisation |
|---|---|---|
| Chrono géant | `timer_display` | Panneau doré « 1 · Le Pont » + le temps en grands chiffres à 7 segments. Se fixe à un mur (le panneau monte au-dessus du bloc). Clic droit : plus grand (1 à 6 : 2,5 × 1 à 15 × 6 blocs), accroupi : plus petit |
| Écran du studio | `studio_screen` (objet) | Écran plat posé sur un mur, 3 × 2 blocs, qui montre une image téléchargée ; voir `/image` |
| Pupitre de candidat | `contestant_desk` | Pupitre en noyer et velours, plaque en laiton au nom du candidat, tournée vers le public (celui qui le pose). Nom : une **étiquette renommée** utilisée dessus (elle est gardée), ou `/craftMasterDesk name` |
| Sonnette de comptoir | `desk_bell` | « Ding » quand un candidat a fini : les organisateurs voient « ♪ Ensuri a sonné (1:42 restantes) » ; impulsion de redstone, une fois par demi-seconde |
| Gong | `gong` | Disque doré dans un cadre laqué. Clic droit (ou impulsion de redstone) : un coup de gong entendu dans tout le studio, le disque se balance. **Un organisateur accroupi** termine en plus la manche en cours (comme `/craftMasterManche end`, annulable) |
| Table des prix | `prize_table` | Table drapée de velours ; jusqu'à 4 prix posés à plat (un par quart du plateau) : clic droit avec l'objet pour le poser, main vide pour le reprendre. Casser la table fait tomber les prix |
| Podium 1er / 2e / 3e | `podium_first`, `podium_second`, `podium_third` | Marches de marbre (1, ¾, ½ bloc) à bandeau or / argent / bronze et numéro |
| Trophée CraftMaster | `trophy` | Coupe dorée gravée CM |
| Poteau à cordon | `stanchion` | Poteaux en laiton ; un cordon de velours pend jusqu'au poteau suivant sur la même ligne (est ou sud, jusqu'à 4 blocs, sans mur entre les deux) |

### Ce qu'affiche le chrono géant
- Avant de lancer une manche à chrono : sa durée, en crème (« 3:00 »).
- Pendant : le temps restant en or, en **rouge clignotant** les 10 dernières secondes.
- À la fin du temps : « 0:00 » rouge qui clignote 8 s.
- Sans manche ni chrono : « -:-- » discret. Le bandeau donne « numéro · nom » de la manche en cours (« CraftMaster » sinon).

Il suit le chrono de l'émission (barre de boss, `/craftMasterManche start`, la voix), il n'a rien à régler.

### `/image` : images sur les écrans
Remplace le `/image <URL>` du plugin (qui passait par ImageFrame).

- `/image <URL> [largeur] [hauteur]` en **regardant un écran du studio** : il affiche l'image (et change de taille
  si on la donne). En regardant **un mur** : un nouvel écran y est accroché, centré sur le bloc visé, 5 × 3 par
  défaut comme le plugin (largeur seule : hauteur en 5:3), au plus 16 × 9 blocs.
- `/image clear` (retour à la mire), `/image remove`, `/image size <l> <h>`, `/image fit <true|false>` (image
  entière avec bandes noires, ou étirée), sur l'écran regardé.
- Clic droit sur un écran (avec la permission) : réglages (adresse, taille, image entière / étirée, mire).
- Permission **`craftmaster:image`** (comme `craftmaster.image` du plugin) ou organisateur.
- Le jeu de **chaque joueur** télécharge l'image (le serveur ne transmet que l'adresse), en arrière-plan : PNG,
  JPEG, GIF (première image) ou BMP, 12 Mo au plus, réduite à 2048 px si plus grande. Gardée en mémoire et dans
  `craftmaster/image-cache/` du dossier du jeu (7 jours). Pendant le chargement : la mire CraftMaster avec
  « Chargement… » ; adresse injoignable ou format inconnu : la mire avec « Image indisponible : HTTP 404 »…
- L'écran s'éclaire lui-même (pleine luminosité), visible de loin (160 blocs). Il se casse comme un tableau.
- Les images d'épreuve (un plan, une consigne illustrée) se mettent de la même façon sur un écran.

## Commandes
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterImage <URL> [l] [h]` | `image`, `cfim` | Voir ci-dessus (permission image) |
| `/craftMasterSpotlight follow <joueur>` | `cfsp` | Le projecteur regardé suit le joueur (et s'allume) |
| `/cfsp stop` / `on` / `off` | | Arrête de suivre (garde sa direction) / allume / éteint |
| `/cfsp color <teinture>` | | Couleur du faisceau (`red`, `light_blue`…) |
| `/cfsp here` | | Pointe vers soi (se placer là où la lumière doit tomber) |
| `/cfsp all <follow\|stop\|on\|off\|color\|here> …` | | Tous les projecteurs chargés |
| `/cfsp list` | | Les projecteurs chargés, allumés ou non, suivi |
| `/craftMasterDesk name <nom>` | `cfdk` | Nom du pupitre regardé (`-` : plaque vide) |
| `/craftMasterGong` | `cfgo` | Coup de gong sur le gong le plus proche (32 blocs) |

Toutes admin sauf `/image` (permission image). Messages en français, Tab propose joueurs et couleurs.

## Régie vocale
- « Projecteurs sur Ensuri », « lumière sur Juste » → `spotlight.follow {player}` (pseudo ou surnom)
- « Projecteurs fixes » → `spotlight.stop` ; « éteins / allume les projecteurs » → `spotlight.off` / `spotlight.on`

Ces ordres ne touchent pas aux données (rien à annuler) ; ils marchent aussi en mode hors ligne.

## Recettes
| Objet | Recette (lignes) |
|---|---|
| Trône | `O·O` / `OVO` / `OBO` — O lingot d'or, V laine rouge, B bloc d'or |
| Fauteuil de l'assistant | `V··` / `VVV` / `P·P` — V laine rouge, P pépite d'or |
| Canapé (×3) | `V··` / `VVV` / `PPP` — V laine rouge, P planches |
| Lampadaire | laine blanche / torche / lingot d'or (colonne) |
| Lampe de bureau | verre vert / torche / pépite d'or |
| Suspension | pépite d'or / lingot d'or / torche |
| Projecteur | `·F·` / `FGF` / `FFF` — F lingot de fer, G pierre lumineuse |
| Enseigne « EN DIRECT » | vitre noire, lampe à redstone, vitre noire |
| Chrono géant | `OHO` / `RBR` — O lingot d'or, H horloge, R redstone, B béton noir |
| Écran du studio | `BBB` / `BVB` — B béton noir, V vitre |
| Pupitre | `DDD` / `VOV` / `P·P` — D dalle de chêne noir, V laine rouge, O lingot d'or, P planches de chêne noir |
| Sonnette | pépite d'or / lingot d'or / dalle de bois |
| Gong | `BBB` / `BOB` / `B·B` — B bâton, O bloc d'or |
| Table des prix | `VVV` / `POP` / `P·P` — V laine rouge, P planches, O pépite d'or |
| Podium 1er / 2e / 3e | `OOO` / `QQQ` / `QQQ` (or) ; `FFF` / `QQQ` (fer) ; `CCC` / dalles de quartz (cuivre) — Q bloc de quartz |
| Trophée | `O·O` / `OOO` / `·D·` — O lingot d'or, D dalle de chêne noir |
| Poteau à cordon (×2) | pépite d'or / bâton / lingot d'or |

## Sons
`craftmaster:studio.gong` (cloche grave) et `craftmaster:studio.desk_bell` (note de cloche aiguë) : des sons
vanilla réaccordés, remplaçables par un pack de ressources (`assets/craftmaster/sounds.json`).

## Notes techniques
- Paquet `craftmaster:studio_timer` (serveur → clients) : chrono en cours, secondes restantes, durée de la manche,
  nom et numéro ; envoyé à chaque changement (une fois par seconde pendant le chrono) et à la connexion. Il lit
  `Show.timerRemaining` sans rien modifier.
- Écrans : entité `craftmaster:studio_screen` (adresse, largeur, hauteur, sens, image entière) ; paquets
  `studio_screen_open` / `studio_screen_save` pour l'écran de réglages.
- Projecteurs, chrono, pupitres, table, gong, poteaux : entités de bloc dessinées par le client
  (`client/studio/`). Le projecteur vise côté client à chaque image (suivi fluide), le serveur ne déplace que le
  bloc de lumière (toutes les 4 ticks).
