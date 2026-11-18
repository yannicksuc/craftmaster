# Objets à manipuler : canards, balles, ballons, golf, cibles, verre gradué

Les petits objets des tâches façon Taskmaster que Minecraft n'a pas : on les pose n'importe où, on les ramasse,
on les lance, ils flottent, rebondissent, éclatent ou se cassent, et les récipients qui comptent disent qui a mis
quoi dedans et de combien de blocs. Tout est dans l'onglet créatif **CraftMaster** (à la suite des objets d'épreuve)
et se fabrique avec des recettes simples. Ils s'ajoutent aux outils de l'arbitre ([Épreuves](Epreuves.md)) :
chaque mesure arrive chez les organisateurs avec des boutons **« [noter …] »** qui l'écrivent sur la feuille de
résultats de la manche en cours.

« Candidats » = les joueurs de la saison active ; « organisateurs » = permission `craftmaster:admin` ou opérateur.
Les messages pour les organisateurs vont dans leur chat et dans le journal (`/cm epreuve journal`).

## 1. Le geste commun : poser, ramasser, lancer

Canard, balle, sachet, œuf et balle de golf se manient pareil :

| Geste | Effet |
|---|---|
| Clic droit **sur un bloc** | Le pose exactement là : sur une dalle, dans un pot de fleurs, contre le côté ou derrière un bloc, sous un plafond. |
| Clic droit **en l'air**, maintenu puis relâché | Le lance, comme un arc : plus on tient (jusqu'à une seconde), plus il va loin. Trajectoire, rebonds, roulement. |
| Clic droit **sur l'objet posé** | Le ramasse (le canard couine). |
| Frapper l'objet | Petit coup de pied (le canard couine et saute ; une balle part loin : on peut faire un foot) ; avec le club de golf : un coup de golf. |
| Marcher dedans | On le pousse (une balle surtout). |
| Distributeur | Il lance l'objet devant lui (machines qui envoient des balles aux candidats). |

**Apparence** : un seul modèle par objet, le moins de cubes possible. La couleur d'un objet teint (canard, balle,
sachet, ballon) n'est pas une teinte appliquée sur du gris : chaque couleur de teinture a sa texture peinte, avec le
même ombrage et des teintes décalées (plus chaudes ou plus violettes dans l'ombre, plus pâles dans la lumière). Un
objet teint de plusieurs teintures prend la couleur de teinture la plus proche. Les autres reprennent les palettes
du jeu : l'acier du seau, l'œuf, la vitre, le bloc cible, le quartz ; le trou de golf **est** le bloc d'herbe du jeu
(mêmes textures, couleur du biome) avec un trou au milieu.

Ils ne sont pas des mobs : pas d'IA, pas de vie, ils ne disparaissent jamais. Dans l'eau, ceux qui flottent
(canard, balle) restent à la surface et **suivent le courant** ; les autres coulent. La lave les détruit.

**Distance d'un lancer** : quand l'objet lancé s'arrête, le lanceur voit « ↔ 7,4 blocs » (avec « record » s'il a
battu son meilleur lancer) et les organisateurs à moins de 64 blocs ont la ligne avec le bouton pour la noter
(distance à plat entre l'endroit du lancer et celui où l'objet s'arrête, roulement compris).

## 2. Canard en plastique

Petit canard en plastique : un rectangle pour le corps, un pour la tête, un pour le bec. Il couine quand on le
ramasse, le frappe, le lance ou quand il tombe de haut. Seize couleurs : il se teint à l'établi comme le cuir
(canard + teinture), et l'onglet créatif en propose déjà sept (jaune, rouge, bleu, vert, rose, blanc, noir). Un
seul modèle ; chaque couleur a sa texture peinte (même ombrage, teintes décalées : un canard jaune tire sur
l'orange dans l'ombre et sur le jaune pâle dans la lumière). Un canard teint de plusieurs teintures prend la
couleur de teinture la plus proche. Son nom suit sa couleur (« Canard en plastique rouge »), ce qui le distingue
aussi pour le compteur.

**Chasse aux canards** (« trouver les dix canards cachés ») :
1. Un organisateur pose les canards (clic droit sur un bloc) : posés par un organisateur, ils sont **cachés** (il
   voit « ✦ Caché : … (7 en tout) »). En créatif, la pile ne diminue pas.
2. Un candidat qui en ramasse un l'a **trouvé** : il voit « ✔ Trouvé : canard en plastique bleu (3) » et les
   organisateurs « ✔ Ensuri a trouvé un canard en plastique bleu (3 en tout, encore 4 cachés) » avec le bouton
   **[noter 3]**. Le canard part dans son inventaire : le compteur sur le joueur les compte aussi.
3. `/cm epreuve jouets` : qui a trouvé combien (et de quelles couleurs), le nombre encore caché ;
   `/cm epreuve jouets liste` : où sont ceux qui restent (clic = s'y téléporter) ; `glow [secondes]` : les fait
   briller à travers les murs ; `collect` : les ramasse tous (fin de la tâche).
4. Organisateur accroupi + clic droit sur un objet posé : il bascule caché / pas caché (et montre son dernier
   joueur).

Un objet lancé n'est jamais caché ; un organisateur qui ramasse un objet caché ne le « trouve » pas (sauf s'il est
lui-même candidat de la saison). Les compteurs sont en mémoire : `/cm epreuve jouets reset` ou un redémarrage les
remet à zéro (les objets cachés, eux, restent dans le monde avec leur état).

## 3. Lancer et cible qui compte

**Objets lancés** : balle rebondissante (rebondit, roule, flotte ; 16 couleurs), sachet (ne rebondit pas, glisse à
peine : idéal pour viser ; 16 couleurs), œuf fragile (voir §5), et les objets du jeu : boules de neige, œufs,
n'importe quel objet jeté avec **Q**.

**Récipients-cibles** : **seau-cible** (10 × 9 px), **poubelle-cible** (12 × 15 px), **tasse-cible** (6 × 6 px, la plus
dure). Ce qui tombe **dedans** est attrapé et gardé à l'intérieur :
- le lanceur voit « ◎ Dans le seau ! (3,4 blocs · 3) » ;
- les organisateurs : « ◎ Ensuri : sachet dans le seau, lancé de 3,4 blocs (3) » avec **[noter 3,4 blocs]**
  (distance) et **[noter 3]** (nombre dans la cible) ;
- un signal redstone bref (le récipient alimente ce qui le touche) et, au comparateur, le nombre d'objets dedans
  (jusqu'à 15).

La distance est celle entre l'endroit du lancer et le récipient. Pour un objet jeté avec Q, une boule de neige ou un
œuf du jeu, c'est la distance du lanceur au moment où ça tombe dedans. Main vide sur le récipient : ce qu'il y a
dedans et à qui (« Ensuri 3, Cyllu 1 ») ; **accroupi** main vide : le vider (organisateurs, ou en créatif).
Le **compteur** marche dessus (« combien de sachets dans la tasse ? »), une trémie dessous le vide.
`/cm epreuve jouets` garde pour chacun le nombre de prises et la plus lointaine.

## 4. Ballon à gonfler et à crever

- **Gonfler** : maintenir le clic droit. Une bouffée toutes les 0,3 s, le ballon grossit dans la main (trois tailles
  de modèle) et le joueur voit sa taille (« ○ 1,4 bloc »). Chaque bouffée en donne un peu moins : le caoutchouc
  durcit.
- **Il éclate si on insiste** : chaque ballon a sa propre limite, tirée au hasard entre 75 et 100 % de la plus
  grande taille (2,4 blocs), inconnue des joueurs. « ✸ Le ballon a éclaté ! » ; les organisateurs le savent.
- Quand on relâche, les organisateurs ont « ○ Ensuri : ballon de 1,5 bloc » et **[noter 1,5 bloc]** (le plus gros
  ballon gagne, mesure « distance »).
- **Attacher** : gonflé (au moins 0,45 bloc), clic droit sur un bloc : il y reste, en se balançant un peu. Clic
  droit dessus : le reprendre, toujours gonflé.
- **Crever** : un coup, une flèche, une boule de neige, tout ce qui blesse. Bang, confettis de sa couleur, signal
  redstone (un bloc de redstone 10 ticks **deux blocs sous le ballon, seulement s'il y a de l'air**, comme les
  objets d'épreuve : mettre le câblage sous le sol) ; les organisateurs ont « ✸ Ensuri a crevé un ballon bleu ».
  Un ballon renommé (« piège ») est annoncé par son nom : « ✸ Cyllu a crevé « piège » ».
- Seize couleurs (ballon + teinture à l'établi). Une pile de ballons : on n'en gonfle qu'un à la fois.

## 5. Œuf fragile

Un œuf qui se pose, se ramasse et se lance comme les autres, mais qui **casse** : au-delà d'une chute de **2 blocs**
par défaut (le choc compte, qu'il tombe ou qu'il cogne un mur en volant), si on le frappe ou si quelque chose le
blesse. Crac, coquille et jaune ; « ◇ Crac ! L'œuf est cassé » pour son joueur et « ◇ L'œuf fragile de Juste s'est
cassé (choc d'une chute de 4,3 blocs) » pour les organisateurs.
- `/cm epreuve jouets oeuf <blocs>` règle l'œuf tenu en main (0 = casse **au moindre choc** : « Œuf à coquille
  fine », aussi dans l'onglet créatif). Le réglage est dans l'objet : il le garde dans un coffre, posé, lancé.
- Posé délicatement (clic droit sur un bloc), un œuf ne casse pas, même à coquille fine.
- « Monter l'œuf le plus haut sans le casser » : le poser en haut de la construction, le mètre ruban mesure la
  hauteur.

## 6. Minigolf

- **Balle de golf** (3 px, blanche) et **club de golf**. Frapper la balle avec le club : la force est la charge
  d'attaque (la barre sous le viseur : attendre une seconde pour un plein coup, environ vingt-cinq blocs en l'air
  et au sol) ; **accroupi** = un putt, qui reste au sol (une dizaine de blocs au plus fort). « ⛳ Coup 3 (putt) ·
  force 60 % ».
- **Trou de golf** : un bloc d'herbe (celui du jeu, à la couleur du biome) avec un trou au milieu, à poser **à la
  place** d'un bloc du sol. Une
  balle qui passe assez lentement sur le trou y tombe (trop vite, elle passe par-dessus) : « ⛳ Ensuri : dans le
  trou en 3 coups » chez les organisateurs, avec **[noter 3 coups]** et **[noter total …]** (tous ses trous depuis
  la remise à zéro), et « Dans le trou en 3 coups ! » pour le joueur.
- Les coups se comptent **par balle** depuis qu'elle a été posée : ramasser la balle et la reposer recommence à 0.
- Un œuf (« golf avec des œufs ») ou une balle rebondissante tombent aussi dans le trou et se comptent pareil ; un
  œuf frappé trop fort au club casse.
- Un coup de club n'est pas un lancer : il ne compte pas comme « plus long lancer ».

## 7. Récipient gradué

**Verre doseur** (1 L, 6 × 10 px) et **cuve graduée** (8 L, presque un bloc, cadre d'acier). Le niveau d'eau se voit
à travers le verre (huit hauteurs) et les graduations sont dessinées sur la vitre.
- Seau d'eau : + 1 000 mL ; fiole d'eau : + 250 mL ; seau vide et fiole vide reprennent la même quantité ; de
  l'eau qui coule **juste au-dessus** (un courant, une cascade) le remplit de 50 mL toutes les 0,5 s ; la pluie un
  peu. Plein, il déborde (le reste est perdu).
- Main vide : la quantité exacte (« ≈ 650 mL / 1 000 mL ») et, pour les organisateurs, les boutons pour la noter
  (mesure « nombre », en mL).
- **Le trait** : un organisateur accroupi main vide trace le trait au niveau actuel (une seconde fois au même niveau
  l'efface). La lecture dit alors l'écart : « 650 mL / 1 000 mL · trait 500 mL (+150 mL) », « (pile !) ».
- Comparateur : le niveau (0 à 15).

## Recettes

| Objet | Recette |
|---|---|
| Canard en plastique ×2 | teinture jaune au-dessus de deux boules de slime (forme : ` J` / `SS`) |
| Balle rebondissante ×4 | 2 boules de slime + teinture rouge (sans forme) |
| Sachet ×2 | papier + graines de blé + ficelle |
| Œuf fragile | œuf + papier |
| Balle de golf ×4 | poudre d'os + boule de slime |
| Club de golf | trois bâtons en colonne à gauche, un lingot de fer en bas à droite |
| Trou de golf | bloc d'herbe + laine blanche + bâton |
| Ballon ×3 | boule de slime + ficelle + teinture rouge |
| Seau-cible / Poubelle-cible / Tasse-cible | seau / chaudron / pot de fleurs + bloc cible |
| Verre doseur | fiole vide + bâton |
| Cuve graduée | chaudron + verre + bâton |
| Couleurs | canard, balle, sachet ou ballon + une ou plusieurs teintures (comme le cuir) |

## Commandes

`/cm epreuve jouets` (alias `cfty`, organisateurs) : voir [Commandes](Commandes.md#épreuves-admin).

## Exemples de tâches

- **« Trouve les dix canards cachés dans le labo, le plus vite »** : `/cm epreuve zone creer labo`, dix canards
  posés par un organisateur (dans des pots, sous des dalles, derrière des meubles), chrono du juge sur chaque
  candidat ; `/cm epreuve jouets` dit qui en a trouvé combien, **[noter 10]**, et `list` / `glow` retrouvent les
  derniers.
- **« Lance un sachet dans la tasse de la plus grande distance »** : une tasse-cible au fond du jardin ; chaque prise
  propose **[noter 7,4 blocs]** ; mesure « distance », le plus grand gagne.
- **« Gonfle le plus gros ballon les yeux bandés »** (avec `/cm fx` flou) : chacun relâche quand il veut ;
  **[noter 1,9 bloc]** pour ceux dont le ballon n'a pas éclaté, disqualifié pour les autres.
- **« Crève un max de ballons en deux minutes »** : des ballons attachés partout, `/cm epreuve jouets` compte les
  ballons crevés de chacun ; un ballon renommé « piège » relié à un signal redstone sous le sol.
- **« Golf à cinq trous avec des œufs »** : cinq trous de golf, un œuf fragile par candidat et par trou, les coups
  notés à chaque trou et au total.
- **« Remplis le verre jusqu'au trait »** : un organisateur verse 600 mL dans un verre doseur, trace le trait,
  le vide ; les candidats remplissent le leur, la lecture donne l'écart à noter.
